package com.collabo.backend.service;

import com.collabo.backend.dto.UserDto;
import com.collabo.backend.dto.UserResponse;
import com.collabo.backend.entity.Role;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.AccountUnverifiedException;
import com.collabo.backend.exception.EmailAlreadyExistsException;
import com.collabo.backend.exception.InvalidCredentialsException;
import com.collabo.backend.exception.InvalidEmailException;
import com.collabo.backend.exception.InvalidOtpException;
import com.collabo.backend.exception.OtpExpiredException;
import com.collabo.backend.exception.PasswordValidationException;
import com.collabo.backend.exception.RateLimitChallengeException;
import com.collabo.backend.exception.RateLimitedException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.exception.UsernameAlreadyExistsException;
import com.collabo.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Auth domain logic: registration (email-verified via OTP), and later login.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RegistrationRateLimiter rateLimiter;
    private final LoginRateLimiter loginLimiter;
    private final String dummyHash;   // compared against when the account doesn't exist, so timing doesn't reveal it
    private final boolean testAccountsEnabled;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       RegistrationRateLimiter rateLimiter,
                       LoginRateLimiter loginLimiter,
                       @Value("${app.test-accounts.enabled:false}") boolean testAccountsEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.loginLimiter = loginLimiter;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
        this.testAccountsEnabled = testAccountsEnabled;
    }

    /**
     * Register a new account. The account is created UNVERIFIED and an OTP is
     * emailed; the user confirms via {@link #verifyOtp}. Transactional so that
     * a failed email send rolls the user back (registration now REQUIRES a
     * working mail sender).
     *
     * <p>When test accounts are enabled ({@code app.test-accounts.enabled=true},
     * off by default), the account is created verified immediately with
     * test=true: email-format validation, OTP, and all outbound email are
     * skipped. This is a global switch meant to be driven by a UI toggle later.
     */
    @Transactional
    public UserResponse register(UserDto dto, String clientIp) {
        RegistrationRateLimiter.RateLimitResult rl =
                rateLimiter.check(clientIp, dto.getChallengeToken(), dto.getChallengeAnswer());

        if (rl.challenge() != null) {
            throw new RateLimitChallengeException(rl.challenge().question(), rl.challenge().token());
        }
        if (rl.retryAfterSeconds() > 0) {
            throw new RateLimitedException(rl.retryAfterSeconds());
        }

        // Normalize identity fields once, at the boundary: email is case-insensitive
        // (lowercased + trimmed) so Foo@x.com == foo@x.com; username is trimmed but
        // case-preserving. This must happen BEFORE the uniqueness checks so the
        // normalized value is what gets both checked and persisted.
        String email = dto.getEmail().trim().toLowerCase();
        String username = dto.getUsername().trim();

        // Test mode (global switch, off by default): skip the "is this a real
        // email" check and everything email-related below.
        boolean testAccount = testAccountsEnabled;
        if (!testAccount && !isValidEmail(email)) {
            throw new InvalidEmailException();
        }

        userRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.isVerified()) {
                throw new EmailAlreadyExistsException();
            }
            // Account exists but was never confirmed — point them back to their inbox.
            throw new AccountUnverifiedException();
        });
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException();
        }

        String password = dto.getPassword();
        // Hard floor: even the "proceed anyway" path can't go below 6 chars.
        if (password.length() < 6) {
            throw new PasswordValidationException("Password must be at least 6 characters");
        }
        // Weak passwords are only accepted if the client explicitly acknowledged
        // them via the proceed-anyway flow.
        if (!isSecurePassword(password) && !dto.isWeakPasswordAccepted()) {
            throw new PasswordValidationException("Password not yet secure");
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setFirstName(dto.getFirstName().trim());
        user.setLastName(dto.getLastName().trim());
        user.setPassword(passwordEncoder.encode(password));
        // Registration always creates a USER account — the client cannot pick a
        // role here. ADMIN accounts are created only via the admin panel.
        user.setRole(Role.USER);
        user.setWelcomed(false);   // brand-new account: show the welcome flow once

        if (testAccount) {
            // Test account: verified immediately, no OTP, no email send.
            user.setTest(true);
            user.setVerified(true);
            User saved = userRepository.save(user);
            log.info("Registered TEST account {} (test mode active — skipped email verification)", email);
            return UserResponse.from(saved);
        }

        user.setVerified(false);
        String code = issueOtp(user);

        User savedUser = userRepository.save(user);
        emailService.sendVerificationEmail(email, username, code);
        return UserResponse.from(savedUser);
    }

    /**
     * Check an email-or-username + password. Returns the user; the caller opens the session.
     * Wrong details always give the same error, and unverified accounts are only revealed
     * after the password was right.
     */
    @Transactional(readOnly = true)
    public User authenticate(String identifier, String password, String clientIp) {
        String id = identifier.trim();
        String key = clientIp + "|" + id.toLowerCase();
        loginLimiter.check(key);

        User user = userRepository.findByEmail(id.toLowerCase()).or(() -> userRepository.findByUsername(id)).orElse(null);
        boolean ok = passwordEncoder.matches(password, user != null ? user.getPassword() : dummyHash);
        if (user == null || !ok) {
            loginLimiter.recordFailure(key);
            throw new InvalidCredentialsException();
        }
        if (!user.isVerified()) {
            throw new AccountUnverifiedException();
        }
        loginLimiter.recordSuccess(key);
        return user;
    }

    /**
     * Confirm an account with the OTP that was emailed. Idempotent: verifying an
     * already-verified account just returns its current state. The welcome email
     * is sent here (post-confirmation) and is best-effort.
     */
    @Transactional
    public UserResponse verifyOtp(String email, String code) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("No account found for this email"));

        if (user.isVerified()) {
            return UserResponse.from(user);
        }
        if (user.getOtpHash() == null || user.getOtpExpiresAt() == null) {
            throw new OtpExpiredException();
        }
        if (user.getOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new OtpExpiredException();
        }
        if (!passwordEncoder.matches(code.trim(), user.getOtpHash())) {
            throw new InvalidOtpException();
        }

        user.setVerified(true);
        user.setOtpHash(null);
        user.setOtpExpiresAt(null);
        User saved = userRepository.save(user);

        // Best-effort: a mail failure must never roll back the verification we
        // just committed to.
        try {
            emailService.sendWelcomeEmail(saved.getEmail(), saved.getUsername());
        } catch (Exception e) {
            log.warn("Welcome email failed for {}", saved.getEmail(), e);
        }

        return UserResponse.from(saved);
    }

    /**
     * Issue a fresh OTP to an unverified account. No-op if the account is
     * already verified (there is nothing left to confirm).
     */
    @Transactional
    public void resendOtp(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("No account found for this email"));
        if (user.isVerified()) {
            return;
        }
        String code = issueOtp(user);
        userRepository.save(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), code);
    }

    /** Generate a 6-digit code, store its BCrypt hash + expiry on the user, return the plaintext. */
    String issueOtp(User user) {
        int code = 100000 + SECURE_RANDOM.nextInt(900000);
        String codeStr = String.valueOf(code);
        user.setOtpHash(passwordEncoder.encode(codeStr));
        user.setOtpExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        return codeStr;
    }

    /** "Secure" = 8+ chars with uppercase + lowercase + digit + special. */
    private boolean isSecurePassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean upper = false, lower = false, digit = false, special = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else special = true;
        }
        return upper && lower && digit && special;
    }

    /** Minimal "looks like an email" check — only used when the account is NOT a test account. */
    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
