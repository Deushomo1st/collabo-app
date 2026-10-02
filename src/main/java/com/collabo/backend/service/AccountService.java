package com.collabo.backend.service;

import com.collabo.backend.dto.AccountDtos.SessionInfo;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.PasswordValidationException;
import com.collabo.backend.exception.RateLimitedException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Account security: password reset and change, the devices you are signed in on, and deleting the account. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final int MIN_PASSWORD = 8, MAX_PASSWORD_BYTES = 72;   // BCrypt only reads the first 72 bytes

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final EmailService email;
    private final LoginRateLimiter limiter;
    private final FindByIndexNameSessionRepository<?> sessions;
    private final PostService postService;
    private final PostRepository posts;
    private final PostDraftRepository drafts;
    private final SpaceRepository spaces;
    private final UserAvatarRepository avatars;
    private final ProfileLinkRepository links;
    private final FollowRepository follows;
    private final NotificationRepository notifications;

    public AccountService(UserRepository users, PasswordEncoder encoder, AuthService auth, EmailService email, LoginRateLimiter limiter,
                          FindByIndexNameSessionRepository<?> sessions, PostService postService, PostRepository posts, PostDraftRepository drafts,
                          SpaceRepository spaces, UserAvatarRepository avatars, ProfileLinkRepository links, FollowRepository follows,
                          NotificationRepository notifications) {
        this.users = users; this.encoder = encoder; this.auth = auth; this.email = email; this.limiter = limiter; this.sessions = sessions;
        this.postService = postService; this.posts = posts; this.drafts = drafts; this.spaces = spaces; this.avatars = avatars;
        this.links = links; this.follows = follows; this.notifications = notifications;
    }

    // ---- forgot / reset (signed out) ---------------------------------------------------------------------------------------

    /** Emails a reset code. Says nothing about whether the address has an account, and sends at most one code a minute. */
    @Transactional
    public void requestReset(String rawEmail) {
        User u = users.findByEmail(rawEmail.trim().toLowerCase()).filter(User::isVerified).orElse(null);
        if (u == null) return;
        if (u.getOtpExpiresAt() != null && u.getOtpExpiresAt().isAfter(LocalDateTime.now().plusMinutes(9))) return;   // one was sent seconds ago
        String code = auth.issueOtp(u);
        users.save(u);
        try { email.sendResetEmail(u.getEmail(), u.getUsername(), code); }
        catch (Exception e) { log.warn("Reset email failed for {}", u.getEmail(), e); }   // the caller must not learn whether this address exists
    }

    @Transactional
    public void reset(String rawEmail, String code, String password) {
        checkNew(password);
        String mail = rawEmail.trim().toLowerCase(), key = "reset|" + mail;
        limiter.check(key);   // five wrong codes lock this address for a while, so a 6-digit code can't be guessed
        User u = users.findByEmail(mail).filter(User::isVerified).orElse(null);
        boolean ok = u != null && u.getOtpHash() != null && u.getOtpExpiresAt() != null
                && u.getOtpExpiresAt().isAfter(LocalDateTime.now()) && encoder.matches(code.trim(), u.getOtpHash());
        if (!ok) {
            limiter.recordFailure(key);
            throw new InvalidProfileException("That code is wrong or has expired. Ask for a new one.");   // the same answer for every reason
        }
        limiter.recordSuccess(key);
        u.setPassword(encoder.encode(password));
        u.setOtpHash(null); u.setOtpExpiresAt(null);
        users.save(u);
        signOutEverywhere(u.getUsername(), null);
        tell(u);
    }

    // ---- change password ----------------------------------------------------------------------------------------------------

    /** Step 1, signed in: emails a code. A password change can't start without it, so a borrowed phone isn't enough. One a minute. */
    @Transactional
    public void sendChangeCode(User me) {
        if (me.getOtpExpiresAt() != null) {
            long wait = Duration.between(LocalDateTime.now(), me.getOtpExpiresAt().minusMinutes(9)).getSeconds();
            if (wait > 0) throw new RateLimitedException(wait);
        }
        String code = auth.issueOtp(me);
        users.save(me);
        try { email.sendChangeCodeEmail(me.getEmail(), me.getUsername(), code); }
        catch (Exception e) { log.warn("Change-password code email failed for {}", me.getEmail(), e); throw new InvalidProfileException("We couldn't send the email. Try again in a moment."); }
    }

    /** Step 2: checks the code (five wrong ones lock it for a while) and uses it up. The caller then marks this session as confirmed. */
    @Transactional
    public void verifyChangeCode(User me, String code) {
        String key = "pwcode|" + me.getUsername();
        limiter.check(key);
        boolean ok = me.getOtpHash() != null && me.getOtpExpiresAt() != null && me.getOtpExpiresAt().isAfter(LocalDateTime.now())
                && encoder.matches(code.trim(), me.getOtpHash());
        if (!ok) { limiter.recordFailure(key); throw new InvalidProfileException("That code is wrong or has expired. Ask for a new one."); }
        limiter.recordSuccess(key);
        me.setOtpHash(null); me.setOtpExpiresAt(null);
        users.save(me);
    }

    /** Step 3, once the code is confirmed: the current password and a new one. */

    @Transactional
    public void changePassword(User me, String current, String password, String keepSessionId) {
        confirm(me, current, "Your current password is wrong.");
        checkNew(password);
        if (encoder.matches(password, me.getPassword())) throw new PasswordValidationException("Choose a password you are not using now.");
        me.setPassword(encoder.encode(password));
        users.save(me);
        signOutEverywhere(me.getUsername(), keepSessionId);
        tell(me);
    }

    // ---- signed-in devices ---------------------------------------------------------------------------------------------------

    public List<SessionInfo> sessions(User me, String currentId) {
        return sessions.findByPrincipalName(me.getUsername()).values().stream().map(s -> info(s, currentId))
                .sorted((a, b) -> b.lastActive().compareTo(a.lastActive())).toList();
    }

    private static SessionInfo info(Session s, String currentId) {
        Object device = s.getAttribute("device");   // sessions from before this feature have none
        return new SessionInfo(handle(s.getId()), s.getId().equals(currentId), device == null ? "Unknown device" : device.toString(),
                s.getCreationTime(), s.getLastAccessedTime());
    }

    public void revoke(User me, String handle, String currentId) {
        String id = sessions.findByPrincipalName(me.getUsername()).keySet().stream().filter(k -> handle(k).equals(handle)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("That device is already signed out."));
        if (id.equals(currentId)) throw new InvalidProfileException("That is this device. Use Sign out instead.");
        sessions.deleteById(id);
    }

    public void signOutEverywhere(String username, String keepSessionId) {
        for (String id : sessions.findByPrincipalName(username).keySet()) if (!id.equals(keepSessionId)) sessions.deleteById(id);
    }

    // ---- delete account ---------------------------------------------------------------------------------------------------------

    /**
     * The row stays, wiped of everything personal, so a space, a comment or a removal record that points at this person never breaks;
     * they show as "deleted-xxxxxx". Their ideas, drafts, photo, links, follows and notifications are removed. Space owners must
     * wait: a team can't be left without an owner. The caller ends the current session.
     */
    @Transactional
    public void deleteAccount(User me, String password, String keepSessionId) {
        confirm(me, password, "That password is wrong.");
        if (spaces.existsByOwnerId(me.getId()))
            throw new InvalidProfileException("You own a space, so the account can't be deleted yet. Its team needs an owner.");
        for (Post p : posts.findByAuthorId(me.getId())) postService.delete(me, p.getId());
        drafts.deleteByAuthorId(me.getId());
        links.deleteByUserId(me.getId());
        avatars.deleteById(me.getId());
        follows.deleteByFollowerIdOrFollowedId(me.getId(), me.getId());
        notifications.deleteByUserId(me.getId());
        signOutEverywhere(me.getUsername(), keepSessionId);   // by the old name, so this runs before the rename
        String tag = UUID.randomUUID().toString().replace("-", "");
        me.setUsername("deleted-" + tag.substring(0, 8));
        me.setEmail("deleted-" + tag + "@deleted.invalid");
        me.setFirstName(null); me.setLastName(null); me.setBio(null); me.setPreferredTitle(null);
        me.setPassword(encoder.encode(tag));   // nobody knows it, so the account can't be signed into
        me.setVerified(false);
        me.setOtpHash(null); me.setOtpExpiresAt(null);
        users.save(me);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------------------------

    /** Wrong password attempts are limited like logins are, so a borrowed session can't be used to guess it. */
    private void confirm(User me, String password, String wrong) {
        String key = "pw|" + me.getUsername();
        limiter.check(key);
        if (!encoder.matches(password, me.getPassword())) { limiter.recordFailure(key); throw new InvalidProfileException(wrong); }
        limiter.recordSuccess(key);
    }

    private static void checkNew(String password) {
        if (password.length() < MIN_PASSWORD) throw new PasswordValidationException("Password must be at least " + MIN_PASSWORD + " characters.");
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) throw new PasswordValidationException("That password is too long. Use 72 characters or fewer.");
    }

    private void tell(User u) {
        try { email.sendPasswordChangedEmail(u.getEmail(), u.getUsername()); }
        catch (Exception e) { log.warn("Password-changed email failed for {}", u.getEmail(), e); }   // the change itself already happened
    }

    private static String handle(String sessionId) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8))).substring(0, 16); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
