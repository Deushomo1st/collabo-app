package com.collabo.backend.dto;

import com.collabo.backend.entity.CredentialsPrivacy;
import com.collabo.backend.entity.User;

import java.time.LocalDateTime;

/**
 * A profile as one viewer sees it. Never the User entity (it carries the password hash and the email).
 * credentialsPrivacy is only filled in when you are looking at your own profile.
 */
public record ProfileResponse(
        String username,
        String preferredTitle,
        String bio,
        boolean self,
        CredentialsPrivacy credentialsPrivacy,
        LocalDateTime joined) {

    public static ProfileResponse of(User user, boolean self) {
        return new ProfileResponse(user.getUsername(), user.getPreferredTitle(), user.getBio(), self,
                self ? user.getCredentialsPrivacy() : null, user.getCreatedAt());
    }
}
