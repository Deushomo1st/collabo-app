package com.collabo.backend.service;

import com.collabo.backend.dto.ApplicationDtos.ApplicationResponse;
import com.collabo.backend.dto.ApplicationDtos.ReviewResponse;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.entity.Application;
import com.collabo.backend.entity.ApplicationReaction;
import com.collabo.backend.entity.Collaborator;
import com.collabo.backend.entity.ApplicationState;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.ApplicationReactionRepository;
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
    private final NotificationService notifications;
    private final ApplicationReactionRepository reactions;
    private final SpaceThreadService threads;

    public ApplicationService(ApplicationRepository applications, PostService postService, PostRepository posts,
                              UserRepository users, UserBlockRepository blocks, SpaceRepository spaces,
                              CollaboratorRepository collaborators,
                              YarnService yarnService, NotificationService notifications,
                              ApplicationReactionRepository reactions, SpaceThreadService threads) {
        this.reactions = reactions; this.threads = threads; this.notifications = notifications; this.yarnService = yarnService; this.collaborators = collaborators;
        this.applications = applications; this.postService = postService; this.posts = posts;
        this.users = users; this.blocks = blocks; this.spaces = spaces;
    }

    public ApplicationResponse apply(User me, UUID postId, String text) {
        Post post = postService.visible(me, postId);
        if (post.getAuthorId().equals(me.getId())) throw new InvalidProfileException("That idea is yours.");
        if (collaborators.existsByPostIdAndUserIdAndState(postId, me.getId(), Collaborator.State.ACTIVE)) {
            throw new InvalidProfileException("You are a collaborator on this idea.");
        }
        if (!post.isApplicationsOn()) throw new InvalidProfileException("This post is not taking applications.");
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
        Map<UUID, List<ApplicationReaction>> reacted = reactions.findByApplicationIdIn(shown.stream().map(Application::getId).toList())
                .stream().collect(Collectors.groupingBy(ApplicationReaction::getApplicationId));
        Map<UUID, User> reviewers = users.findAllById(reacted.values().stream().flatMap(List::stream).map(ApplicationReaction::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Set<UUID> eligible = reviewerIds(post);
        return shown.stream().map(a -> review(a, people.get(a.getApplicantId()), reacted.getOrDefault(a.getId(), List.of()), reviewers, eligible, me)).toList();
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
        if (next == ApplicationState.ACCEPTED) {
            record(a, me, ApplicationReaction.Kind.AGREE);   // accepting is agreeing
            if (!consensus(post, a)) {
                throw new InvalidProfileException("Not everyone agrees. Talk it through in the WeSpace, or win a majority of the collaborators first.");
            }
        }
        a.setState(next);   // the applicant hears of an acceptance when the space forms, not before
        return review(applications.save(a), me, post);
    }

    /** AGREE, DISAGREE, or NONE to take your reaction back. A disagreement is announced in the WeSpace; it is not a veto, a majority still carries. */
    public ReviewResponse react(User me, UUID applicationId, String reaction) {
        Application a = applications.findById(applicationId).orElseThrow(() -> new ResourceNotFoundException("No such application."));
        Post post = ownPost(me, a.getPostId());
        if (a.getState() == ApplicationState.WITHDRAWN || a.getState() == ApplicationState.DECLINED) throw new InvalidProfileException("This application is closed.");
        if (a.getState() == ApplicationState.ACCEPTED && spaces.existsByPostId(a.getPostId())) throw new InvalidProfileException("This applicant is already part of a space.");
        String r = reaction == null ? "" : reaction;
        if (r.equals("NONE")) reactions.findByApplicationIdAndUserId(a.getId(), me.getId()).ifPresent(reactions::delete);
        else if (r.equals("AGREE") || r.equals("DISAGREE")) {
            boolean fresh = record(a, me, ApplicationReaction.Kind.valueOf(r));
            if (r.equals("DISAGREE") && fresh) {
                threads.announceWeSpace(post.getId(), me.getUsername() + " disagrees with accepting " + applicantName(a) + ". Talk it through here.");
            }
        } else throw new InvalidProfileException("Choose agree, disagree or none.");
        // an acceptance that lost its consensus goes back to the shortlist while there is still no space
        if (a.getState() == ApplicationState.ACCEPTED && !consensus(post, a)) {
            a.setState(ApplicationState.SHORTLISTED);
            applications.save(a);
            threads.announceWeSpace(post.getId(), applicantName(a) + " is back on the shortlist: the acceptance no longer has agreement.");
        }
        return review(a, me, post);
    }

    /** Sets the reviewer's reaction; true when it is new or changed. */
    private boolean record(Application a, User me, ApplicationReaction.Kind kind) {
        Optional<ApplicationReaction> had = reactions.findByApplicationIdAndUserId(a.getId(), me.getId());
        if (had.isPresent() && had.get().getKind() == kind) return false;
        ApplicationReaction row = had.orElseGet(() -> new ApplicationReaction(a.getId(), me.getId(), kind));
        row.setKind(kind);
        reactions.save(row);
        return true;
    }

    /** Nobody disagrees, or more than half of the reviewers agree. Only people who still review count. */
    private boolean consensus(Post post, Application a) {
        Set<UUID> eligible = reviewerIds(post);
        List<ApplicationReaction> mine = reactions.findByApplicationId(a.getId()).stream().filter(x -> eligible.contains(x.getUserId())).toList();
        long agree = mine.stream().filter(x -> x.getKind() == ApplicationReaction.Kind.AGREE).count();
        return agree == mine.size() || agree * 2 > eligible.size();
    }

    /** The founder and the active collaborators: the people whose reactions count. Frozen and disbanded ones do not. */
    private Set<UUID> reviewerIds(Post post) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(post.getAuthorId());
        collaborators.findByPostIdAndStateInOrderByCreatedAtAsc(post.getId(), List.of(Collaborator.State.ACTIVE)).forEach(c -> ids.add(c.getUserId()));
        return ids;
    }

    private String applicantName(Application a) { return users.findById(a.getApplicantId()).map(User::getUsername).orElse("The applicant"); }

    private ReviewResponse review(Application a, User me, Post post) {
        User applicant = users.findById(a.getApplicantId()).orElseThrow(() -> new ResourceNotFoundException("No such application."));
        List<ApplicationReaction> rs = reactions.findByApplicationId(a.getId());
        Map<UUID, User> who = users.findAllById(rs.stream().map(ApplicationReaction::getUserId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return review(a, applicant, rs, who, reviewerIds(post), me);
    }

    private static ReviewResponse review(Application a, User applicant, List<ApplicationReaction> rs, Map<UUID, User> who, Set<UUID> eligible, User me) {
        Function<ApplicationReaction.Kind, List<PersonDto>> of = k -> rs.stream().filter(x -> x.getKind() == k && eligible.contains(x.getUserId()))
                .map(x -> PersonDto.of(who.get(x.getUserId()))).toList();
        String mine = rs.stream().filter(x -> x.getUserId().equals(me.getId())).map(x -> x.getKind().name()).findFirst().orElse(null);
        return new ReviewResponse(a.getId(), PersonDto.of(applicant), a.getStatement(), a.getState().name(), a.getCreatedAt(),
                of.apply(ApplicationReaction.Kind.AGREE), of.apply(ApplicationReaction.Kind.DISAGREE), mine);
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
