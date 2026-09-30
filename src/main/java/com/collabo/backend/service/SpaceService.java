package com.collabo.backend.service;

import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.dto.SpaceDtos.SpaceResponse;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.ApplicationRepository;
import com.collabo.backend.repository.PostRepository;
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

    public SpaceService(SpaceRepository spaces, PostRepository posts, ApplicationRepository applications, UserRepository users,
                        CredentialService credentials, SpaceMemberRepository members) {
        this.members = members; this.spaces = spaces; this.posts = posts; this.applications = applications; this.users = users; this.credentials = credentials;
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
        post.setFormed(true);
        posts.save(post);
        credentials.record(me.getId(), CredentialKind.SPACE_FORMED, space.getName(), "", "space", space.getId().toString(), null);
        return response(space, post, "OWNER");
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
        return response(s, post, role);
    }

    /** OWNER, MEMBER (joined), APPLICANT (accepted, may read, not joined), or null (no access). */
    String roleOf(User me, Space s) {
        if (s.getOwnerId().equals(me.getId())) return "OWNER";
        boolean accepted = applications.findByPostIdAndApplicantId(s.getPostId(), me.getId())
                .filter(a -> a.getState() == ApplicationState.ACCEPTED).isPresent();
        if (!accepted) return null;
        boolean joined = members.findBySpaceIdAndUserId(s.getId(), me.getId()).filter(m -> m.getState() == SpaceMember.State.ACTIVE).isPresent();
        return joined ? "MEMBER" : "APPLICANT";
    }

    private SpaceResponse response(Space s, Post post, String role) {
        User owner = users.findById(s.getOwnerId()).orElseThrow(() -> new ResourceNotFoundException("No such space."));
        return new SpaceResponse(s.getId(), s.getPostId(), s.getName(), post.getTitle(), post.getBody(), PersonDto.of(owner), role, s.getCreatedAt());
    }
}
