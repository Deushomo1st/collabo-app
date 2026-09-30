package com.collabo.backend.service;

import com.collabo.backend.entity.*;
import com.collabo.backend.repository.ThreadMemberRepository;
import com.collabo.backend.repository.UserRepository;
import com.collabo.backend.repository.YarnRepository;
import com.collabo.backend.repository.YarnThreadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The Yarns a space owns. A space's Workspace thread opens when it forms and seats people as they join;
 * the collaborators' WeSpace opens when the first collaborator accepts. Threads are found by the post they came from.
 */
@Service
@Transactional
public class SpaceThreadService {

    private final YarnThreadRepository threads;
    private final ThreadMemberRepository seats;
    private final YarnRepository yarns;
    private final UserRepository users;

    public SpaceThreadService(YarnThreadRepository threads, ThreadMemberRepository seats, YarnRepository yarns, UserRepository users) {
        this.threads = threads; this.seats = seats; this.yarns = yarns; this.users = users;
    }

    /** The space's Workspace thread, or null before it exists. */
    @Transactional(readOnly = true)
    public UUID workspaceId(UUID postId) {
        return threads.findByPostIdAndTier(postId, YarnThread.Tier.WORKSPACE).map(YarnThread::getId).orElse(null);
    }

    public void openWorkspace(Space space) {
        open(YarnThread.Tier.WORKSPACE, space.getPostId(), space.getOwnerId(), space.getName());
    }

    public void joinWorkspace(Space space, UUID userId) {
        threads.findByPostIdAndTier(space.getPostId(), YarnThread.Tier.WORKSPACE).ifPresent(t -> enter(t, userId));
    }

    public void leaveWorkspace(Space space, UUID userId) {
        threads.findByPostIdAndTier(space.getPostId(), YarnThread.Tier.WORKSPACE).ifPresent(t -> exit(t, userId));
    }

    /** A new collaborator sits in the post's WeSpace, which is opened (with the author in it) if this is the first. */
    public void joinWeSpace(Post post, UUID userId) {
        YarnThread t = threads.findByPostIdAndTier(post.getId(), YarnThread.Tier.WESPACE)
                .orElseGet(() -> open(YarnThread.Tier.WESPACE, post.getId(), post.getAuthorId(), post.getTitle()));
        enter(t, userId);
    }

    public void leaveWeSpace(UUID postId, UUID userId) {
        threads.findByPostIdAndTier(postId, YarnThread.Tier.WESPACE).ifPresent(t -> exit(t, userId));
    }

    private YarnThread open(YarnThread.Tier tier, UUID postId, UUID ownerId, String name) {
        YarnThread t = new YarnThread();
        t.setTier(tier); t.setName(name); t.setCreatedBy(ownerId); t.setPostId(postId);
        threads.save(t);
        ThreadMember owner = new ThreadMember(t.getId(), ownerId, ThreadMember.Role.OWNER);
        owner.setLastReadAt(Instant.now());
        seats.save(owner);
        say(t, nameOf(ownerId) + " opened " + name + ".");
        return t;
    }

    private void enter(YarnThread t, UUID userId) {
        if (seats.findByThreadIdAndUserId(t.getId(), userId).isPresent()) return;
        seats.save(new ThreadMember(t.getId(), userId, ThreadMember.Role.MEMBER));
        say(t, nameOf(userId) + " joined.");
    }

    private void exit(YarnThread t, UUID userId) {
        seats.findByThreadIdAndUserId(t.getId(), userId).ifPresent(m -> {
            seats.delete(m);
            say(t, nameOf(userId) + " left.");
        });
    }

    private void say(YarnThread t, String body) {
        Yarn y = yarns.save(new Yarn(t.getId(), null, Yarn.Kind.SYSTEM, body));
        t.recordYarn(null, body, y.getCreatedAt());
        threads.save(t);
    }

    private String nameOf(UUID userId) { return users.findById(userId).map(User::getUsername).orElse("Someone"); }
}
