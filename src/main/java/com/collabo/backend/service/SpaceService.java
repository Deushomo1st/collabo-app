package com.collabo.backend.service;

import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.dto.SpaceDtos.SpaceResponse;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.ApplicationRepository;
import com.collabo.backend.repository.PostRepository;
import com.collabo.backend.repository.CollaboratorRepository;
import com.collabo.backend.repository.SpaceMemberRepository;
import com.collabo.backend.repository.SpaceRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Spaces. A space is not public: it opens only to its owner and to applicants who were accepted, and
 * everyone else is told it does not exist.
 */
@Service
@Transactional
public class SpaceService {

    static final int MAX_NAME = 80;

    private final SpaceRepository spaces;
    private final PostRepository posts;
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final CredentialService credentials;
    private final SpaceMemberRepository members;
    private final CollaboratorRepository collaborators;
    private final SpaceThreadService spaceThreads;

    public SpaceService(SpaceRepository spaces, PostRepository posts, ApplicationRepository applications, UserRepository users,
                        CredentialService credentials, SpaceMemberRepository members,
                        CollaboratorRepository collaborators, SpaceThreadService spaceThreads) {
        this.spaceThreads = spaceThreads; this.collaborators = collaborators; this.members = members; this.spaces = spaces; this.posts = posts; this.applications = applications; this.users = users; this.credentials = credentials;
    }

    /** The post's author forms the space, once, when at least one applicant has been accepted. */
    public SpaceResponse form(User me, UUID postId, String requestedName) {
        Post post = posts.findById(postId).filter(p -> p.getAuthorId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("That post is gone."));
        if (spaces.existsByPostId(postId)) throw new InvalidProfileException("This idea already has a space.");
        if (!applications.existsByPostIdAndState(postId, ApplicationState.ACCEPTED)) {
            throw new InvalidProfileException("Accept at least one applicant before forming a space.");
        }
        String name = requestedName == null || requestedName.isBlank() ? post.getTitle() : requestedName.trim();
        if (name.length() > MAX_NAME) {
            if (requestedName == null || requestedName.isBlank()) name = name.substring(0, MAX_NAME);   // a long post title is cut, not refused
            else throw new InvalidProfileException("Keep the space name under " + MAX_NAME + " characters.");
        }
        Space space = spaces.save(new Space(postId, me.getId(), name));
        SpaceMember founder = new SpaceMember(space.getId(), me.getId());
        founder.setTitle("Owner");
        founder.setPermissions(java.util.EnumSet.allOf(SpacePermission.class));
        members.save(founder);   // the owner is a member like everyone else
        spaceThreads.openWorkspace(space);
        collaborators.findByPostIdAndStateInOrderByCreatedAtAsc(postId, java.util.List.of(Collaborator.State.ACTIVE))
                .forEach(c -> seat(space, c.getUserId(), "Collaborator"));
        post.setFormed(true);
        posts.save(post);
        credentials.record(me.getId(), CredentialKind.SPACE_FORMED, space.getName(), "", "space", space.getId().toString(), null);
        return response(space, post, "OWNER", true, spaceThreads.workspaceId(postId));
    }

    @Transactional(readOnly = true)
    public SpaceResponse forPost(User me, UUID postId) {
        return open(me, spaces.findByPostId(postId));
    }

    @Transactional(readOnly = true)
    public SpaceResponse get(User me, UUID id) {
        return open(me, spaces.findById(id));
    }

    private SpaceResponse open(User me, java.util.Optional<Space> found) {
        Space s = found.orElseThrow(() -> new ResourceNotFoundException("No such space."));
        String role = roleOf(me, s);
        if (role == null) throw new ResourceNotFoundException("No such space.");
        Post post = posts.findById(s.getPostId()).orElseThrow(() -> new ResourceNotFoundException("No such space."));
        return response(s, post, role, "OWNER".equals(role) || collaborators.existsByPostIdAndUserIdAndState(s.getPostId(), me.getId(), Collaborator.State.ACTIVE),
                "APPLICANT".equals(role) ? null : spaceThreads.workspaceId(s.getPostId()));
    }

    /** The owner can do anything; a member can do what they were given. */
    boolean can(User me, Space s, SpacePermission p) {
        if (s.getOwnerId().equals(me.getId())) return true;
        return members.findBySpaceIdAndUserId(s.getId(), me.getId())
                .filter(m -> m.getState() == SpaceMember.State.ACTIVE && m.getPermissions().contains(p)).isPresent();
    }

    /** Puts someone in the room with every permission (co-founders), or back in if they were out. */
    void seat(Space s, UUID userId, String title) {
        SpaceMember m = members.findBySpaceIdAndUserId(s.getId(), userId).orElseGet(() -> new SpaceMember(s.getId(), userId));
        if (m.getId() != null) m.rejoin();
        m.setTitle(title);
        m.setPermissions(java.util.EnumSet.allOf(SpacePermission.class));
        members.save(m);
        spaceThreads.joinWorkspace(s, userId);
    }

    /** Takes someone out of the room and closes it to them. */
    void unseat(Space s, UUID userId) {
        members.findBySpaceIdAndUserId(s.getId(), userId).ifPresent(m -> {
            m.setState(SpaceMember.State.REMOVED);
            m.setPermissions(java.util.EnumSet.noneOf(SpacePermission.class));
            members.save(m);
        });
        spaceThreads.leaveWorkspace(s, userId);
    }

    /** OWNER, MEMBER (joined), APPLICANT (accepted, may read, not joined), or null (no access, including the removed). */
    String roleOf(User me, Space s) {
        if (s.getOwnerId().equals(me.getId())) return "OWNER";
        var member = members.findBySpaceIdAndUserId(s.getId(), me.getId());
        if (member.filter(m -> m.getState() == SpaceMember.State.REMOVED).isPresent()) return null;   // removal ends reading too
        if (member.filter(m -> m.getState() == SpaceMember.State.ACTIVE).isPresent()) return "MEMBER";   // includes seated collaborators
        boolean accepted = applications.findByPostIdAndApplicantId(s.getPostId(), me.getId())
                .filter(a -> a.getState() == ApplicationState.ACCEPTED).isPresent();
        return accepted ? "APPLICANT" : null;
    }

    private SpaceResponse response(Space s, Post post, String role, boolean canManage, UUID threadId) {
        User owner = users.findById(s.getOwnerId()).orElseThrow(() -> new ResourceNotFoundException("No such space."));
        return new SpaceResponse(s.getId(), s.getPostId(), s.getName(), post.getTitle(), post.getBody(), PersonDto.of(owner), role, canManage, threadId, s.getCreatedAt());
    }
}
