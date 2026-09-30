package com.collabo.backend.config;

import com.collabo.backend.entity.Moderator;
import com.collabo.backend.exception.UnauthorizedException;
import com.collabo.backend.repository.ModeratorRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The signed-in moderator. A user's session never passes here, and a moderator's session never passes CurrentUser. */
@Component
public class CurrentModerator {

    public static final String AUTHORITY = "ROLE_MODERATOR_ACCOUNT";
    public static final String PREFIX = "moderator:";

    private final ModeratorRepository moderators;

    public CurrentModerator(ModeratorRepository moderators) { this.moderators = moderators; }

    /** True for a session that was opened through the moderator login. */
    public static boolean isModerator(Authentication auth) {
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> AUTHORITY.equals(a.getAuthority()));
    }

    /** The active moderator behind this session; a deactivated account is signed out at its next request. */
    public Moderator require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isModerator(auth) || !auth.getName().startsWith(PREFIX)) throw new UnauthorizedException();
        try {
            return moderators.findById(UUID.fromString(auth.getName().substring(PREFIX.length()))).filter(Moderator::isActive).orElseThrow(UnauthorizedException::new);
        } catch (IllegalArgumentException e) { throw new UnauthorizedException(); }
    }
}
