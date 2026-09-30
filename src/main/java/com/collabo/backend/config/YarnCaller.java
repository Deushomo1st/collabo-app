package com.collabo.backend.config;

import com.collabo.backend.entity.User;
import com.collabo.backend.exception.YarnException;
import com.collabo.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Who is calling the Yarnspaces API? The logged-in user of the current session. */
@Component
public class YarnCaller {

    private final UserRepository users;

    public YarnCaller(UserRepository users) {
        this.users = users;
    }

    public User require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            throw new YarnException(HttpStatus.UNAUTHORIZED, "Sign in to use Yarns.");
        }
        return users.findByUsername(auth.getName())
                .orElseThrow(() -> new YarnException(HttpStatus.UNAUTHORIZED, "Sign in to use Yarns."));
    }
}
