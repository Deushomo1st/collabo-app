package com.collabo.backend.service;

import com.collabo.backend.dto.ApplicationDtos.ApplicationResponse;
import com.collabo.backend.dto.ApplicationDtos.ReviewResponse;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.entity.Application;
import com.collabo.backend.entity.Collaborator;
import com.collabo.backend.entity.ApplicationState;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.ApplicationRepository;
import com.collabo.backend.repository.CollaboratorRepository;
import com.collabo.backend.repository.PostRepository;
import com.collabo.backend.repository.SpaceRepository;
import com.collabo.backend.repository.UserBlockRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applying to a post. The applicant writes a short "why you"; nothing about applications is public,
 * so an idea nobody applied to just reads as closed.
 */
@Service
@Transactional
public class ApplicationService {

    static final int MAX_WORDS = 150;

    private final ApplicationRepository applications;
    private final PostService postService;
    private final PostRepository posts;
    private final UserRepository users;
    private final UserBlockRepository blocks;
    private final SpaceRepository spaces;
    private final CollaboratorRepository collaborators;
    private final YarnService yarnService;

    public ApplicationService(ApplicationRepository applications, PostService postService, PostRepository posts,
                              UserRepository users, UserBlockRepository blocks, SpaceRepository spaces,
                              CollaboratorRepository collaborators,
                              YarnService yarnService) {
        this.yarnService = yarnService; this.collaborators = collaborators;
        this.applications = applications; this.postService = postService; this.posts = posts;
        this.users = users; this.blocks = blocks; this.spaces = spaces;
    }

    public ApplicationResponse apply(User me, UUID postId, String text) {
        Post post = postService.visible(me, postId);
        if (post.getAuthorId().equals(me.getId())) throw new InvalidProfileException("That idea is yours.");
        if (collaborators.existsByPostIdAndUserIdAndState(postId, me.getId(), Collaborator.State.ACTIVE)) {
            throw new InvalidProfileException("You are a collaborator on this idea.");
        }
        if (!"pending".equals(post.status())) throw new InvalidProfileException("Applications for this post are closed.");
        String statement = text == null ? "" : text.trim();
        if (statement.isEmpty()) throw new InvalidProfileException("Tell them why you.");
        if (statement.split("\\s+").length > MAX_WORDS) throw new InvalidProfileException("Keep it to " + MAX_WORDS + " words.");

        Optional<Application> existing = applications.findByPostIdAndApplicantId(postId, me.getId());
        if (existing.isPresent()) {
            if (existing.get().getState() != ApplicationState.WITHDRAWN) throw new InvalidProfileException("You already applied to this one.");
            applications.delete(existing.get());   // a withdrawn application makes way for a fresh one
            applications.flush();
        }
        return response(applications.save(new Application(postId, me.getId(), statement)), post);
    }

    public ApplicationResponse withdraw(User me, UUID applicationId) {
        Application a = applications.findById(applicationId).filter(x -> x.getApplicantId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("No such application."));
        if (a.getState() != ApplicationState.SUBMITTED && a.getState() != ApplicationState.SHORTLISTED) {
            throw new InvalidProfileException("This application can no longer be withdrawn.");
        }
        a.setState(ApplicationState.WITHDRAWN);
        return response(applications.save(a), posts.findById(a.getPostId()).orElseThrow(() -> new ResourceNotFoundException("That post is gone.")));
    }

    /** Your own applications, newest first. Posts behind a block are left out, without saying why. */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> mine(User me) {
        List<Application> rows = applications.findByApplicantIdOrderByCreatedAtDesc(me.getId());
        Map<UUID, Post> byPost = posts.findAllById(rows.stream().map(Application::getPostId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Post::getId, Function.identity()));
        Set<UUID> hidden = new HashSet<>(blocks.counterpartsOf(me.getId()));
        Map<UUID, User> authors = users.findAllById(byPost.values().stream().map(Post::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().filter(a -> byPost.containsKey(a.getPostId()) && !hidden.contains(byPost.get(a.getPostId()).getAuthorId()))
                .map(a -> {
                    Post p = byPost.get(a.getPostId());
                    return ApplicationResponse.of(a, p, PersonDto.of(authors.get(p.getAuthorId())));
                }).toList();
    }

    /** The founder's review stack: sort "recent" (default) or "oldest"; filter "unreviewed" or "shortlisted" (default all). Withdrawn are never shown. */
    @Transactional(readOnly = true)
    public List<ReviewResponse> stack(User me, UUID postId, String sort, String filter) {
        Post post = ownPost(me, postId);
        boolean oldest = switch (sort == null || sort.isBlank() ? "recent" : sort) {
            case "recent" -> false;
            case "oldest" -> true;
            default -> throw new InvalidProfileException("Unknown sort.");
        };
        ApplicationState only = switch (filter == null || filter.isBlank() ? "all" : filter) {
            case "all" -> null;
            case "unreviewed" -> ApplicationState.SUBMITTED;
            case "shortlisted" -> ApplicationState.SHORTLISTED;
            default -> throw new InvalidProfileException("Unknown filter.");
        };
        List<Application> rows = oldest
                ? applications.findByPostIdAndStateNotOrderByCreatedAtAsc(post.getId(), ApplicationState.WITHDRAWN)
                : applications.findByPostIdAndStateNotOrderByCreatedAtDesc(post.getId(), ApplicationState.WITHDRAWN);
        Set<UUID> hidden = new HashSet<>(blocks.counterpartsOf(me.getId()));
        List<Application> shown = rows.stream().filter(a -> (only == null || a.getState() == only) && !hidden.contains(a.getApplicantId())).toList();
        Map<UUID, User> people = users.findAllById(shown.stream().map(Application::getApplicantId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return shown.stream().map(a -> review(a, people.get(a.getApplicantId()))).toList();
    }

    /** ACCEPT, DECLINE or SHORTLIST, set explicitly by the post's author. Can be changed until a space exists. */
    public ReviewResponse decide(User me, UUID applicationId, String decision) {
        Application a = applications.findById(applicationId).orElseThrow(() -> new ResourceNotFoundException("No such application."));
        Post post = ownPost(me, a.getPostId());
        if (a.getState() == ApplicationState.WITHDRAWN) throw new InvalidProfileException("This application was withdrawn.");
        if (a.getState() == ApplicationState.ACCEPTED && spaces.existsByPostId(a.getPostId())) {
            throw new InvalidProfileException("This applicant is already part of a space.");
        }
        ApplicationState next = switch (decision == null ? "" : decision) {
            case "ACCEPT" -> ApplicationState.ACCEPTED;
            case "DECLINE" -> ApplicationState.DECLINED;
            case "SHORTLIST" -> ApplicationState.SHORTLISTED;
            default -> throw new InvalidProfileException("Choose accept, decline or shortlist.");
        };
        boolean newlyAccepted = next == ApplicationState.ACCEPTED && a.getState() != ApplicationState.ACCEPTED;
        a.setState(next);
        User applicant = users.findById(a.getApplicantId()).orElseThrow(() -> new ResourceNotFoundException("No such application."));
        if (newlyAccepted) yarnService.notifyAccepted(me, applicant, post.getTitle());
        return review(applications.save(a), applicant);
    }

    private static ReviewResponse review(Application a, User applicant) {
        return new ReviewResponse(a.getId(), PersonDto.of(applicant), a.getStatement(), a.getState().name(), a.getCreatedAt());
    }

    /** The post, if the caller is its author or an active collaborator; anyone else (or a missing post) gets not-found. */
    private Post ownPost(User me, UUID postId) {
        Post p = posts.findById(postId).filter(x -> x.getAuthorId().equals(me.getId())
                        || collaborators.existsByPostIdAndUserIdAndState(postId, me.getId(), Collaborator.State.ACTIVE))
                .orElseThrow(() -> new ResourceNotFoundException("That post is gone."));
        return p;
    }

    private ApplicationResponse response(Application a, Post p) {
        User author = users.findById(p.getAuthorId()).orElseThrow(() -> new ResourceNotFoundException("That post is gone."));
        return ApplicationResponse.of(a, p, PersonDto.of(author));
    }
}
