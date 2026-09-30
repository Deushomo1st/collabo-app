package com.collabo.backend.config;

import com.collabo.backend.exception.YarnException;

import com.collabo.backend.entity.User;
import com.collabo.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Who is calling the Yarnspaces API?
 *
 * There is no login yet, so for now the caller names themselves with an
 * X-Dev-User: <username> header. That is only honoured when
 * app.dev-identity.enabled=true (env ALLOW_DEV_IDENTITY); otherwise the API
 * fails closed with 503. When real auth lands, replace the body of require()
 * and nothing else has to change.
 */
@Component
public class YarnCaller {

    private final boolean enabled;
    private final UserRepository users;

    public YarnCaller(@Value("${app.dev-identity.enabled:false}") boolean enabled, UserRepository users) {
        this.enabled = enabled;
        this.users = users;
    }

    public User require(String devUser) {
        if (!enabled) {
            throw new YarnException(HttpStatus.SERVICE_UNAVAILABLE, "Yarns are not available yet: sign-in is not built.");
        }
        if (devUser == null || devUser.isBlank()) {
            throw new YarnException(HttpStatus.UNAUTHORIZED, "Say who you are first.");
        }
        return users.findByUsername(devUser.trim())
                .orElseThrow(() -> new YarnException(HttpStatus.UNAUTHORIZED, "No account with that username."));
    }
}
