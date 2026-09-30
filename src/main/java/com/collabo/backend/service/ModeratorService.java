package com.collabo.backend.service;

import com.collabo.backend.dto.ModeratorDtos.*;
import com.collabo.backend.entity.Moderator;
import com.collabo.backend.exception.EmailAlreadyExistsException;
import com.collabo.backend.exception.InvalidCredentialsException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.ModeratorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** Moderator accounts: the admin creates and switches them; a moderator signs in and changes their own password. */
@Service
@Transactional
public class ModeratorService {

    static final int MIN_PASSWORD = 10, MAX_NAME = 60;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ModeratorRepository moderators;
    private final PasswordEncoder encoder;
    private final LoginRateLimiter limiter;
    private final String dummyHash;

    public ModeratorService(ModeratorRepository moderators, PasswordEncoder encoder, LoginRateLimiter limiter) {
        this.moderators = moderators; this.encoder = encoder; this.limiter = limiter;
        this.dummyHash = encoder.encode("not-a-real-password");   // compared when the email is unknown, so timing doesn't reveal it
    }

    public ModeratorView create(CreateRequest req) {
        String name = req.name() == null ? "" : req.name().trim();
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        if (name.isEmpty() || name.length() > MAX_NAME) throw new InvalidProfileException("Name is required, up to " + MAX_NAME + " characters.");
        if (!EMAIL.matcher(email).matches()) throw new InvalidProfileException("Enter a valid email.");
        checkPassword(req.password());
        if (moderators.existsByEmail(email)) throw new EmailAlreadyExistsException();
        return ModeratorView.of(moderators.save(new Moderator(name, email, encoder.encode(req.password()))));
    }

    @Transactional(readOnly = true)
    public List<ModeratorView> list() { return moderators.findAllByOrderByCreatedAtAsc().stream().map(ModeratorView::of).toList(); }

    public ModeratorView setActive(UUID id, boolean active) {
        Moderator m = find(id);
        m.setActive(active);
        return ModeratorView.of(moderators.save(m));
    }

    public void resetPassword(UUID id, String password) {
        checkPassword(password);
        Moderator m = find(id);
        m.setPasswordHash(encoder.encode(password));
        moderators.save(m);
    }

    public void changePassword(Moderator me, String current, String password) {
        if (current == null || !encoder.matches(current, me.getPasswordHash())) throw new InvalidProfileException("Your current password is wrong.");
        checkPassword(password);
        me.setPasswordHash(encoder.encode(password));
        moderators.save(me);
    }

    /** Wrong details, an unknown email and a deactivated account all answer the same. */
    @Transactional(readOnly = true)
    public Moderator authenticate(String email, String password, String clientIp) {
        String id = email == null ? "" : email.trim().toLowerCase();
        String key = "moderator|" + clientIp + "|" + id;
        limiter.check(key);
        Moderator m = moderators.findByEmail(id).orElse(null);
        boolean ok = encoder.matches(password == null ? "" : password, m != null ? m.getPasswordHash() : dummyHash);
        if (m == null || !ok || !m.isActive()) {
            limiter.recordFailure(key);
            throw new InvalidCredentialsException();
        }
        limiter.recordSuccess(key);
        return m;
    }

    private Moderator find(UUID id) { return moderators.findById(id).orElseThrow(() -> new ResourceNotFoundException("No such moderator.")); }

    private void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) throw new InvalidProfileException("Password must be at least " + MIN_PASSWORD + " characters.");
    }
}
