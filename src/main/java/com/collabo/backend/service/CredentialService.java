package com.collabo.backend.service;

import com.collabo.backend.dto.CredentialsResponse;
import com.collabo.backend.entity.CredentialEntry;
import com.collabo.backend.entity.CredentialKind;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.CredentialEntryRepository;
import com.collabo.backend.repository.FollowRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Credentials: recorded by the system, shown by the owner's privacy setting. */
@Service
@Transactional
public class CredentialService {

    static final int MAX_TITLE = 120;
    static final int MAX_DETAIL = 300;

    private final CredentialEntryRepository entries;
    private final FollowRepository follows;
    private final UserRepository users;

    public CredentialService(CredentialEntryRepository entries, FollowRepository follows, UserRepository users) {
        this.entries = entries; this.follows = follows; this.users = users;
    }

    /**
     * The one way an entry comes to exist (spaces and milestones call this). Recording the same
     * (sourceType, sourceId) for the same user again does nothing. Returns whether a new entry was made.
     */
    public boolean record(UUID userId, CredentialKind kind, String title, String detail,
                          String sourceType, String sourceId, Instant occurredAt) {
        String t = title == null ? "" : title.trim();
        String d = detail == null ? "" : detail.trim();
        if (kind == null || t.isEmpty() || sourceType == null || sourceType.isBlank() || sourceId == null || sourceId.isBlank()) {
            throw new InvalidProfileException("A credential needs a kind, a title and a source.");
        }
        if (t.length() > MAX_TITLE || d.length() > MAX_DETAIL || sourceType.length() > 40 || sourceId.length() > 64) {
            throw new InvalidProfileException("That credential is too long.");
        }
        // ponytail: check-then-insert; two simultaneous identical events hit the unique constraint and one fails. Fine until events fire concurrently.
        if (entries.existsBySourceTypeAndSourceIdAndUserId(sourceType, sourceId, userId)) return false;
        entries.save(new CredentialEntry(userId, kind, t, d, sourceType, sourceId, occurredAt == null ? Instant.now() : occurredAt));
        return true;
    }

    @Transactional(readOnly = true)
    public CredentialsResponse view(String username, User viewer) {
        User owner = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No one has that username."));
        if (!canView(owner, viewer)) return CredentialsResponse.hidden();
        return CredentialsResponse.of(entries.findByUserIdOrderByOccurredAtDesc(owner.getId()));
    }

    /** The one function that decides who may see an owner's credentials. The owner always can. */
    boolean canView(User owner, User viewer) {
        UUID o = owner.getId(), v = viewer.getId();
        if (o.equals(v)) return true;
        return switch (owner.getCredentialsPrivacy()) {
            case EVERYONE -> true;
            case FOLLOWERS -> follows.existsByFollowerIdAndFollowedId(v, o);
            case FOLLOWING -> follows.existsByFollowerIdAndFollowedId(o, v);
            case MUTUAL -> follows.existsByFollowerIdAndFollowedId(v, o) && follows.existsByFollowerIdAndFollowedId(o, v);
            case APPLICANTS -> false;   // acts as "only me" until applications exist (phase 4)
        };
    }

    /** Admin seeding by username. */
    public boolean recordFor(String username, CredentialKind kind, String title, String detail,
                             String sourceType, String sourceId, Instant occurredAt) {
        User user = users.findByUsername(username == null ? "" : username)
                .orElseThrow(() -> new ResourceNotFoundException("No one has that username."));
        return record(user.getId(), kind, title, detail, sourceType, sourceId, occurredAt);
    }
}
