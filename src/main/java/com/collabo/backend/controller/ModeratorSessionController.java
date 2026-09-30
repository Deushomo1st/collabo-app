package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentModerator;
import com.collabo.backend.dto.LoginRequest;
import com.collabo.backend.dto.ModeratorDtos.*;
import com.collabo.backend.entity.Moderator;
import com.collabo.backend.service.ModeratorService;
import com.collabo.backend.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** A moderator's own sign-in, on the same session cookie as users but a different kind of principal. LoginRequest.identifier is the email. */
@RestController
@RequestMapping("/api/moderator")
public class ModeratorSessionController {

    private final ModeratorService moderators;
    private final CurrentModerator current;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public ModeratorSessionController(ModeratorService moderators, CurrentModerator current) { this.moderators = moderators; this.current = current; }

    @PostMapping("/login")
    public ModeratorView login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        Moderator m = moderators.authenticate(req.identifier(), req.password(), ClientIpResolver.resolve(request));
        if (request.getSession(false) != null) request.changeSessionId();   // session fixation protection, as for users
        else request.getSession(true);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(CurrentModerator.PREFIX + m.getId(), null,
                List.of(new SimpleGrantedAuthority(CurrentModerator.AUTHORITY)));
        SecurityContext context = new SecurityContextImpl(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ModeratorView.of(m);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /** 200 with the moderator when signed in as one, 401 otherwise. */
    @GetMapping("/me")
    public ModeratorView me() { return ModeratorView.of(current.require()); }

    @PutMapping("/password")
    public ResponseEntity<Void> password(@RequestBody ChangePasswordRequest req) {
        moderators.changePassword(current.require(), req.current(), req.password());
        return ResponseEntity.noContent().build();
    }
}
