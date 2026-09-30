package com.collabo.backend.controller;

import com.collabo.backend.dto.LoginRequest;
import com.collabo.backend.dto.UserResponse;
import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import com.collabo.backend.service.AuthService;
import com.collabo.backend.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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

/** Login sessions: sign in, sign out, who am I. Thin shell; the checking is in AuthService. */
@RestController
@RequestMapping("/api/auth")
public class SessionController {

    private final AuthService authService;
    private final UserRepository users;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public SessionController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        User user = authService.authenticate(req.identifier(), req.password(), ClientIpResolver.resolve(request));

        if (request.getSession(false) != null) request.changeSessionId();   // new id on login: session fixation protection
        else request.getSession(true);
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
                user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = new SecurityContextImpl(auth);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /** 200 with the account when signed in, 401 when not (the frontend uses this to decide what to show). */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        return users.findByUsername(auth.getName()).map(u -> ResponseEntity.ok(UserResponse.from(u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
