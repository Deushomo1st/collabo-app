package com.collabo.backend.service;

import com.collabo.backend.dto.CollaboratorDtos.CollaboratorResponse;
import com.collabo.backend.dto.CollaboratorDtos.RequestResponse;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Co-founders. The author asks a mutual follow; once they accept they review applications with the author
 * and, when a space exists, sit in it as a member.
 */
@Service
@Transactional
public class CollaboratorService {

    private static final String GONE = "That post is gone.";

    private final CollaboratorRepository collaborators;
    private final PostRepository posts;
    private final UserRepository users;
    private final FollowRepository follows;
    private final UserBlockRepository blocks;
    private final ApplicationRepository applications;
    private final SpaceRepository spaces;
    private final SpaceService spaceService;

    public CollaboratorService(CollaboratorRepository collaborators, PostRepository posts, UserRepository users, FollowRepository follows,
                               UserBlockRepository blocks, ApplicationRepository applications, SpaceRepository spaces, SpaceService spaceService) {
        this.collaborators = collaborators; this.posts = posts; this.users = users; this.follows = follows;
        this.blocks = blocks; this.applications = applications; this.spaces = spaces; this.spaceService = spaceService;
    }

    public CollaboratorResponse invite(User me, UUID postId, String username) {
        authored(me, postId);
        User target = users.findByUsername(username == null ? "" : username.trim()).orElseThrow(() -> new ResourceNotFoundException("No such person."));
        if (target.getId().equals(me.getId())) throw new InvalidProfileException("You are already the founder.");
        if (blocks.counterpartsOf(me.getId()).contains(target.getId())) throw new InvalidProfileException("You cannot ask that person.");
        if (!follows.existsByFollowerIdAndFollowedId(me.getId(), target.getId()) || !follows.existsByFollowerIdAndFollowedId(target.getId(), me.getId())) {
            throw new InvalidProfileException("Collaborators must be people you follow who follow you back.");
        }
        if (applications.findByPostIdAndApplicantId(postId, target.getId()).filter(a -> a.getState() != ApplicationState.WITHDRAWN).isPresent()) {
            throw new InvalidProfileException("That person applied to this idea.");
        }
        Collaborator c = collaborators.findByPostIdAndUserId(postId, target.getId()).orElse(null);
        if (c == null) c = new Collaborator(postId, target.getId());
        else if (c.getState() == Collaborator.State.DECLINED) c.reinvite();
        else throw new InvalidProfileException(c.getState() == Collaborator.State.ACTIVE ? "That person is already a collaborator." : "You already asked that person.");
        return new CollaboratorResponse(PersonDto.of(target), collaborators.save(c).getState().name(), c.getCreatedAt());
    }

    /** Asked or active collaborators, for the author and for active collaborators. */
    @Transactional(readOnly = true)
    public List<CollaboratorResponse> list(User me, UUID postId) {
        Post post = posts.findById(postId).orElseThrow(() -> new ResourceNotFoundException(GONE));
        if (!post.getAuthorId().equals(me.getId()) && !collaborators.existsByPostIdAndUserIdAndState(postId, me.getId(), Collaborator.State.ACTIVE)) {
            throw new ResourceNotFoundException(GONE);
        }
        List<Collaborator> rows = collaborators.findByPostIdAndStateInOrderByCreatedAtAsc(postId, List.of(Collaborator.State.INVITED, Collaborator.State.ACTIVE));
        Map<UUID, User> people = users.findAllById(rows.stream().map(Collaborator::getUserId).toList()).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().filter(c -> people.containsKey(c.getUserId()))
                .map(c -> new CollaboratorResponse(PersonDto.of(people.get(c.getUserId())), c.getState().name(), c.getCreatedAt())).toList();
    }

    /** Collaborations the viewer was asked into or has accepted. */
    @Transactional(readOnly = true)
    public List<RequestResponse> mine(User me) {
        return collaborators.findByUserIdAndStateInOrderByCreatedAtDesc(me.getId(), List.of(Collaborator.State.INVITED, Collaborator.State.ACTIVE)).stream().map(c -> {
            Post p = posts.findById(c.getPostId()).orElse(null);
            User founder = p == null ? null : users.findById(p.getAuthorId()).orElse(null);
            return founder == null ? null : new RequestResponse(p.getId(), p.getTitle(), p.status(), PersonDto.of(founder), c.getState().name(), c.getCreatedAt());
        }).filter(java.util.Objects::nonNull).toList();
    }

    public void accept(User me, UUID postId) {
        Collaborator c = pending(me, postId);
        c.setState(Collaborator.State.ACTIVE);
        collaborators.save(c);
        spaces.findByPostId(postId).ifPresent(s -> spaceService.seat(s, me.getId(), "Collaborator"));
    }

    public void decline(User me, UUID postId) {
        Collaborator c = pending(me, postId);
        c.setState(Collaborator.State.DECLINED);
        collaborators.save(c);
    }

    /** The author removes someone, or a collaborator steps down (username is their own). */
    public void remove(User me, UUID postId, String username) {
        Post post = posts.findById(postId).orElseThrow(() -> new ResourceNotFoundException(GONE));
        User target = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No such collaborator."));
        boolean allowed = post.getAuthorId().equals(me.getId()) || target.getId().equals(me.getId());
        if (!allowed) throw new ResourceNotFoundException(GONE);
        Collaborator c = collaborators.findByPostIdAndUserId(postId, target.getId())
                .filter(x -> x.getState() != Collaborator.State.DECLINED).orElseThrow(() -> new ResourceNotFoundException("No such collaborator."));
        collaborators.delete(c);
        spaces.findByPostId(postId).ifPresent(s -> spaceService.unseat(s, target.getId()));
    }

    private Post authored(User me, UUID postId) {
        return posts.findById(postId).filter(p -> p.getAuthorId().equals(me.getId())).orElseThrow(() -> new ResourceNotFoundException(GONE));
    }

    private Collaborator pending(User me, UUID postId) {
        return collaborators.findByPostIdAndUserId(postId, me.getId()).filter(c -> c.getState() == Collaborator.State.INVITED)
                .orElseThrow(() -> new ResourceNotFoundException("No such request."));
    }
}
