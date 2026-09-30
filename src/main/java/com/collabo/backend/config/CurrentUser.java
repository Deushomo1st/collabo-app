package com.collabo.backend.config;

import com.collabo.backend.entity.User;
import com.collabo.backend.exception.UnauthorizedException;
import com.collabo.backend.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The logged-in user of the current session. Every signed-in-only endpoint asks here who is calling. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            throw new UnauthorizedException();
        }
        return users.findByUsername(auth.getName())
                .orElseThrow(() -> new UnauthorizedException());
    }
}
