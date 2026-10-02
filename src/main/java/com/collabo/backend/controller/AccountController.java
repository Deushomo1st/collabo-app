package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.AccountDtos.*;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Account security. Forgot and reset are public (under /api/auth); everything else needs a session. Thin shell over AccountService. */
@RestController
@RequestMapping("/api")
public class AccountController {

    private static final String CONFIRMED = "passwordCodeOk";   // session attribute: until when (ms) the emailed code counts
    private static final long CONFIRM_MS = 10 * 60 * 1000L;

    private final AccountService account;
    private final CurrentUser current;

    public AccountController(AccountService account, CurrentUser current) { this.account = account; this.current = current; }

    @PostMapping("/auth/forgot")
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest req) {
        account.requestReset(req.email());
        return Map.of("message", "If that email has an account, a code is on its way.");   // the same words either way
    }

    @PostMapping("/auth/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest req) {
        account.reset(req.email(), req.code(), req.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account/password/code")
    public ResponseEntity<Void> sendChangeCode() {
        account.sendChangeCode(current.require());
        return ResponseEntity.noContent().build();
    }

    /** The right code marks this session (this device only) as confirmed for ten minutes. */
    @PostMapping("/account/password/verify")
    public ResponseEntity<Void> verifyChangeCode(@Valid @RequestBody CodeRequest req, HttpServletRequest request) {
        account.verifyChangeCode(current.require(), req.code());
        request.getSession().setAttribute(CONFIRMED, System.currentTimeMillis() + CONFIRM_MS);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpServletRequest request) {
        HttpSession session = request.getSession();
        if (!(session.getAttribute(CONFIRMED) instanceof Long until) || until < System.currentTimeMillis())
            throw new InvalidProfileException("Confirm the code we emailed you first.");
        account.changePassword(current.require(), req.current(), req.password(), session.getId());
        session.removeAttribute(CONFIRMED);   // one change per code
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/account/sessions")
    public List<SessionInfo> sessions(HttpServletRequest request) {
        return account.sessions(current.require(), request.getSession().getId());
    }

    @DeleteMapping("/account/sessions/{id}")
    public ResponseEntity<Void> revoke(@PathVariable String id, HttpServletRequest request) {
        account.revoke(current.require(), id, request.getSession().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account/sessions/sign-out-others")
    public ResponseEntity<Void> signOutOthers(HttpServletRequest request) {
        account.signOutEverywhere(current.require().getUsername(), request.getSession().getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/account/delete")
    public ResponseEntity<Void> delete(@Valid @RequestBody ConfirmRequest req, HttpServletRequest request) {
        account.deleteAccount(current.require(), req.password(), request.getSession().getId());
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
