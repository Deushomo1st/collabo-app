package com.collabo.backend.service;

import com.collabo.backend.dto.CollaboratorDtos.SeatView;
import com.collabo.backend.dto.CollaboratorDtos.WeSpaceAbout;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.CollaboratorRepository;
import com.collabo.backend.repository.PostRepository;
import com.collabo.backend.repository.SpaceRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The collaborators' side of governance: the WeSpace "about the group" view, and nudging, freezing and disbanding a collaborator.
 * Frozen opts someone out without removing them; disbanded needs a reason and leaves the spot open to return.
 * ponytail: the founder decides alone. The poster-and-collaborators vote, the response clock on a nudge and pleas for collaborators
 * are in Governance and moderation but not built; add them on top of freeze/disband when wanted.
 */
@Service
@Transactional
public class WeSpaceService {

    static final int MAX_REASON = 300;
    private static final String GONE = "That post is gone.";

    private final CollaboratorRepository collaborators;
    private final PostRepository posts;
    private final UserRepository users;
    private final SpaceRepository spaces;
    private final SpaceService spaceService;
    private final SpaceThreadService threads;
    private final YarnService yarns;
    private final NotificationService notifications;

    public WeSpaceService(CollaboratorRepository collaborators, PostRepository posts, UserRepository users, SpaceRepository spaces,
                          SpaceService spaceService, SpaceThreadService threads, YarnService yarns, NotificationService notifications) {
        this.collaborators = collaborators; this.posts = posts; this.users = users; this.spaces = spaces;
        this.spaceService = spaceService; this.threads = threads; this.yarns = yarns; this.notifications = notifications;
    }

    /** For the founder and for active or frozen collaborators; anyone else gets the same 404 as a missing post. */
    @Transactional(readOnly = true)
    public WeSpaceAbout about(User me, UUID postId) {
        Post post = posts.findById(postId).orElseThrow(() -> new ResourceNotFoundException(GONE));
        boolean founder = post.getAuthorId().equals(me.getId());
        Collaborator mine = collaborators.findByPostIdAndUserId(postId, me.getId())
                .filter(c -> c.getState() == Collaborator.State.ACTIVE || c.getState() == Collaborator.State.FROZEN).orElse(null);
        if (!founder && mine == null) throw new ResourceNotFoundException(GONE);

        List<SeatView> seats = new ArrayList<>();
        users.findById(post.getAuthorId()).ifPresent(u -> seats.add(new SeatView(PersonDto.of(u), "ACTIVE", null, true)));
        for (Collaborator c : collaborators.findByPostIdAndStateInOrderByCreatedAtAsc(postId,
                List.of(Collaborator.State.ACTIVE, Collaborator.State.FROZEN, Collaborator.State.DISBANDED, Collaborator.State.INVITED))) {
            users.findById(c.getUserId()).ifPresent(u -> seats.add(new SeatView(PersonDto.of(u), c.getState().name(), c.getReason(), false)));
        }
        String role = founder ? "FOUNDER" : mine.getState() == Collaborator.State.FROZEN ? "FROZEN" : "COLLABORATOR";
        UUID spaceId = spaces.findByPostId(postId).map(Space::getId).orElse(null);
        return new WeSpaceAbout(postId, post.getTitle(), post.getBody(), post.status(), role, threads.weSpaceId(postId), spaceId, seats);
    }

    /** Any active collaborator or the founder can remind someone; it lands in that person's MySpace and notifications. */
    public void nudge(User me, UUID postId, String username) {
        Post post = post(postId);
        User target = target(username);
        Collaborator c = seat(postId, target);
        if (c.getState() != Collaborator.State.ACTIVE) throw new InvalidProfileException("Only an active collaborator can be nudged.");
        boolean allowed = post.getAuthorId().equals(me.getId()) || collaborators.existsByPostIdAndUserIdAndState(postId, me.getId(), Collaborator.State.ACTIVE);
        if (!allowed || target.getId().equals(me.getId())) throw new ResourceNotFoundException(GONE);
        String body = me.getUsername() + " nudged you: a decision in the collaborators' room of \"" + post.getTitle() + "\" is waiting on you.";
        yarns.systemNote(me, target, body);
        notifications.notify(target.getId(), Notification.Bucket.SPACES, "A collaborator nudged you", body, "/HTML-pages/yarnspaces.html");
    }

    public void freeze(User me, UUID postId, String username) {
        Post post = founderPost(me, postId);
        User target = target(username);
        Collaborator c = seat(postId, target);
        if (c.getState() != Collaborator.State.ACTIVE) throw new InvalidProfileException("Only an active collaborator can be frozen.");
        c.setState(Collaborator.State.FROZEN, null);
        collaborators.save(c);
        threads.announceWeSpace(postId, me.getUsername() + " froze " + target.getUsername() + ". They can read but not write until unfrozen.");
        notifications.notify(target.getId(), Notification.Bucket.SPACES, "You were frozen", "You are opted out of the collaborators' room of \"" + post.getTitle() + "\" until " + me.getUsername() + " unfreezes you.", "/HTML-pages/yarnspaces.html");
    }

    public void unfreeze(User me, UUID postId, String username) {
        Post post = founderPost(me, postId);
        User target = target(username);
        Collaborator c = seat(postId, target);
        if (c.getState() != Collaborator.State.FROZEN) throw new InvalidProfileException("That person is not frozen.");
        c.setState(Collaborator.State.ACTIVE, null);
        collaborators.save(c);
        threads.announceWeSpace(postId, me.getUsername() + " unfroze " + target.getUsername() + ".");
        notifications.notify(target.getId(), Notification.Bucket.SPACES, "You were unfrozen", "You are back in the collaborators' room of \"" + post.getTitle() + "\".", "/HTML-pages/yarnspaces.html");
    }

    /** The seat stays on the table, marked, with the reason; asking them again (invite) is how they return. */
    public void disband(User me, UUID postId, String username, String rawReason) {
        Post post = founderPost(me, postId);
        User target = target(username);
        Collaborator c = seat(postId, target);
        if (c.getState() != Collaborator.State.ACTIVE && c.getState() != Collaborator.State.FROZEN) throw new InvalidProfileException("That person is not on the team.");
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.isEmpty() || reason.length() > MAX_REASON) throw new InvalidProfileException("Give a reason of up to " + MAX_REASON + " characters.");
        c.setState(Collaborator.State.DISBANDED, reason);
        collaborators.save(c);
        threads.announceWeSpace(postId, me.getUsername() + " disbanded " + target.getUsername() + " for: " + reason);
        threads.leaveWeSpace(postId, target.getId());
        spaces.findByPostId(postId).ifPresent(s -> spaceService.unseat(s, target.getId()));
        notifications.notify(target.getId(), Notification.Bucket.SPACES, "You were disbanded", me.getUsername() + ": " + reason + ". Your spot on \"" + post.getTitle() + "\" stays open if you come back.", "/HTML-pages/yarnspaces.html");
    }

    private Post post(UUID postId) { return posts.findById(postId).orElseThrow(() -> new ResourceNotFoundException(GONE)); }

    private Post founderPost(User me, UUID postId) {
        Post p = post(postId);
        if (!p.getAuthorId().equals(me.getId())) throw new ForbiddenException("Only the founder can do this.");
        return p;
    }

    private User target(String username) {
        return users.findByUsername(username == null ? "" : username.trim()).orElseThrow(() -> new ResourceNotFoundException("No such collaborator."));
    }

    private Collaborator seat(UUID postId, User target) {
        return collaborators.findByPostIdAndUserId(postId, target.getId()).orElseThrow(() -> new ResourceNotFoundException("No such collaborator."));
    }
}
