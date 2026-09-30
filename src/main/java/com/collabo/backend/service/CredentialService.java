package com.collabo.backend.service;

import com.collabo.backend.dto.CredentialsResponse;
import com.collabo.backend.dto.ShippedLinkRequest;
import com.collabo.backend.entity.ShippedLink;
import com.collabo.backend.repository.ShippedLinkRepository;
import com.collabo.backend.entity.ApplicationState;
import com.collabo.backend.entity.CredentialEntry;
import com.collabo.backend.entity.CredentialKind;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.ApplicationRepository;
import com.collabo.backend.repository.CredentialEntryRepository;
import com.collabo.backend.repository.FollowRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Credentials: recorded by the system, shown by the owner's privacy setting. */
@Service
@Transactional
public class CredentialService {

    static final int MAX_TITLE = 120;
    static final int MAX_DETAIL = 300;
    static final int MAX_SHIPPED = 5;

    private final CredentialEntryRepository entries;
    private final ShippedLinkRepository shipped;
    private final FollowRepository follows;
    private final UserRepository users;
    private final ApplicationRepository applications;
    private final PostService posts;

    public CredentialService(CredentialEntryRepository entries, ShippedLinkRepository shipped, FollowRepository follows, UserRepository users,
                             ApplicationRepository applications, PostService posts) {
        this.shipped = shipped; this.applications = applications; this.posts = posts;
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
        return open(owner);
    }

    /**
     * "View founder's credentials": the founder of a post, for someone with a live application to it.
     * Tied to the application, so it is not a general unlock of the founder's profile.
     */
    @Transactional(readOnly = true)
    public CredentialsResponse founderView(User viewer, UUID postId) {
        Post post = posts.visible(viewer, postId);
        boolean applied = applications.findByPostIdAndApplicantId(postId, viewer.getId())
                .filter(a -> a.getState() != ApplicationState.WITHDRAWN).isPresent();
        if (!applied) throw new ResourceNotFoundException("That post is gone.");
        return open(users.findById(post.getAuthorId()).orElseThrow(() -> new ResourceNotFoundException("That post is gone.")));
    }

    private CredentialsResponse open(User owner) {
        List<CredentialEntry> rows = entries.findByUserIdOrderByOccurredAtDesc(owner.getId());
        Map<UUID, List<ShippedLink>> shipped = shippedFor(rows);
        return new CredentialsResponse(true, rows.stream().map(e -> entryView(e, shipped)).toList());
    }

    // ---- owner-only changes: each answers 404 for someone else's (or a missing) entry --------------------------

    /** Promote to feats (true) or take it back (false). */
    public CredentialsResponse.Entry setFeatured(User me, UUID entryId, Boolean featured) {
        if (featured == null) throw new InvalidProfileException("Say whether to feature it.");
        CredentialEntry e = mine(me, entryId);
        e.setFeatured(featured);
        return entryView(entries.save(e), shippedFor(List.of(e)));
    }

    public CredentialsResponse.Entry addShipped(User me, UUID entryId, ShippedLinkRequest req) {
        CredentialEntry e = mine(me, entryId);
        String title = ProfileService.cleanLinkTitle(req.title());
        String url = ProfileService.cleanUrl(req.url());
        if (shipped.countByEntryId(entryId) >= MAX_SHIPPED) throw new InvalidProfileException("You can attach at most " + MAX_SHIPPED + " links to one credential.");
        shipped.save(new ShippedLink(entryId, title, url));
        return entryView(e, shippedFor(List.of(e)));
    }

    public CredentialsResponse.Entry removeShipped(User me, UUID entryId, UUID linkId) {
        CredentialEntry e = mine(me, entryId);
        shipped.findById(linkId).filter(l -> l.getEntryId().equals(entryId)).ifPresent(shipped::delete);
        return entryView(e, shippedFor(List.of(e)));
    }

    private CredentialEntry mine(User me, UUID entryId) {
        return entries.findById(entryId).filter(e -> e.getUserId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("No such credential."));
    }

    private Map<UUID, List<ShippedLink>> shippedFor(List<CredentialEntry> rows) {
        if (rows.isEmpty()) return Map.of();
        return shipped.findByEntryIdIn(rows.stream().map(CredentialEntry::getId).toList()).stream()
                .collect(Collectors.groupingBy(ShippedLink::getEntryId));
    }

    private static CredentialsResponse.Entry entryView(CredentialEntry e, Map<UUID, List<ShippedLink>> shipped) {
        return CredentialsResponse.Entry.of(e, shipped.getOrDefault(e.getId(), List.of()));
    }

    /** The one function that decides who may see an owner's credentials. The owner always can. */
    boolean canView(User owner, User viewer) {
        UUID o = owner.getId(), v = viewer.getId();
        if (o.equals(v)) return true;
        // applying overrides the owner's setting: a founder always sees the credentials of someone who applied to them
        if (applications.liveBetween(o, v, ApplicationState.WITHDRAWN)) return true;
        return switch (owner.getCredentialsPrivacy()) {
            case EVERYONE -> true;
            case FOLLOWERS -> follows.existsByFollowerIdAndFollowedId(v, o);
            case FOLLOWING -> follows.existsByFollowerIdAndFollowedId(o, v);
            case MUTUAL -> follows.existsByFollowerIdAndFollowedId(v, o) && follows.existsByFollowerIdAndFollowedId(o, v);
            case APPLICANTS -> applications.liveBetween(v, o, ApplicationState.WITHDRAWN);   // people who applied to my posts
        };
    }

    /** Admin seeding by username. */
    public boolean recordFor(String username, CredentialKind kind, String title, String detail,
                             String sourceType, String sourceId, Instant occurredAt) {
        User user = users.findByUsername(username == null ? "" : username)
                .orElseThrow(() -> new ResourceNotFoundException("No one has that username."));
        return record(user.getId(), kind, title, detail, sourceType, sourceId, occurredAt);
    }

    /** Takes back an entry the system recorded (a member opting out of a milestone). Nothing happens if there is none. */
    public void withdraw(UUID userId, String sourceType, String sourceId) {
        entries.findBySourceTypeAndSourceIdAndUserId(sourceType, sourceId, userId).ifPresent(e -> {
            shipped.deleteByEntryId(e.getId());
            entries.delete(e);
        });
    }
}
