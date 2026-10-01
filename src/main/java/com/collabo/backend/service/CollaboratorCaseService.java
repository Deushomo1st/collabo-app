package com.collabo.backend.service;

import com.collabo.backend.dto.CollaboratorDtos.CaseView;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.dto.RemovalDtos.PleaView;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Governance among collaborators, from Governance and moderation. Someone flags a quiet collaborator with a reason; the target is
 * nudged and has the idea's response clock (48 hours or more) to answer; a plea buys twelve hours; the founder can cancel. If the
 * clock runs out the founder and the other collaborators vote to freeze or disband them, and the first choice with a majority wins.
 * A tied vote is the founder's to break by freezing or disbanding directly. Time is settled lazily before any action and by a sweep.
 * Pleas are reused from the Workspace process, with the post id in the space slot.
 * ponytail: pleas are always on here and nobody can apply to be disbanded; "Step down" already covers leaving.
 */
@Service
@Transactional
public class CollaboratorCaseService {

    static final long PLEA_HOURS = 12;
    private static final String GONE = "That post is gone.";
    private static final List<CollaboratorCase.State> OPEN = List.of(CollaboratorCase.State.RUNNING, CollaboratorCase.State.VOTING);

    private final CollaboratorCaseRepository cases;
    private final CaseVoteRepository votes;
    private final PleaRepository pleas;
    private final CollaboratorRepository collaborators;
    private final PostRepository posts;
    private final UserRepository users;
    private final WeSpaceService weSpace;
    private final SpaceThreadService threads;
    private final YarnService yarns;
    private final NotificationService notifications;

    public CollaboratorCaseService(CollaboratorCaseRepository cases, CaseVoteRepository votes, PleaRepository pleas, CollaboratorRepository collaborators,
                                   PostRepository posts, UserRepository users, WeSpaceService weSpace, SpaceThreadService threads, YarnService yarns,
                                   NotificationService notifications) {
        this.cases = cases; this.votes = votes; this.pleas = pleas; this.collaborators = collaborators; this.posts = posts; this.users = users;
        this.weSpace = weSpace; this.threads = threads; this.yarns = yarns; this.notifications = notifications;
    }

    public CaseView start(User me, UUID postId, String username, String rawReason) {
        Post post = inRoom(me, postId);
        User target = users.findByUsername(username == null ? "" : username.trim()).orElseThrow(() -> new InvalidProfileException("Name a collaborator."));
        if (target.getId().equals(me.getId())) throw new InvalidProfileException("You cannot flag yourself.");
        if (!active(postId, target.getId())) throw new InvalidProfileException("Name an active collaborator.");
        if (!cases.findByPostIdAndTargetIdAndStateIn(postId, target.getId(), OPEN).isEmpty()) throw new InvalidProfileException("That person is already flagged.");
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.isEmpty() || reason.length() > WeSpaceService.MAX_REASON) throw new InvalidProfileException("Give a reason of up to " + WeSpaceService.MAX_REASON + " characters.");

        int hours = post.getResponseClockHours();
        CollaboratorCase k = cases.save(new CollaboratorCase(postId, target.getId(), me.getId(), reason, Instant.now().plusSeconds(3600L * hours)));
        threads.announceWeSpace(postId, me.getUsername() + " flagged " + target.getUsername() + " as quiet: " + reason + ". They have " + hours + " hours to respond.");
        String note = me.getUsername() + " says you have gone quiet on \"" + post.getTitle() + "\": " + reason + ". Respond in the collaborators' room within " + hours + " hours to stay.";
        yarns.systemNote(me, target, note);
        notifications.require(target.getId(), "ccase:" + k.getId(), Notification.Bucket.SPACES, "You were flagged as quiet", note, wespaceLink(postId));
        return one(k, me);
    }

    public List<CaseView> list(User me, UUID postId) {
        inRoom(me, postId);
        List<CollaboratorCase> rows = cases.findTop50ByPostIdOrderByStartedAtDesc(postId);
        Map<UUID, User> people = users.findAllById(rows.stream().flatMap(k -> java.util.stream.Stream.of(k.getTargetId(), k.getInitiatorId())).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return present(rows, people, me);
    }

    /** The flagged person answers while the clock is still running, which ends it. */
    public CaseView respond(User me, UUID postId, UUID id) {
        inRoom(me, postId);
        CollaboratorCase k = open(postId, id);
        if (k.getState() != CollaboratorCase.State.RUNNING) throw new InvalidProfileException("The clock has run out; the collaborators are voting.");
        if (!k.getTargetId().equals(me.getId())) throw new ForbiddenException("Only the flagged person can answer.");
        end(k, CollaboratorCase.State.RESPONDED);
        threads.announceWeSpace(postId, me.getUsername() + " responded. The flag ended.");
        return one(k, me);
    }

    /** A plea buys twelve hours: one standing at a time, one per collaborator per week, never by or for the wrong person. */
    public CaseView plea(User me, UUID postId, UUID id) {
        inRoom(me, postId);
        CollaboratorCase k = open(postId, id);
        if (k.getState() != CollaboratorCase.State.RUNNING) throw new InvalidProfileException("The clock has run out; a plea can no longer buy time.");
        if (k.getTargetId().equals(me.getId())) throw new InvalidProfileException("A plea is made on someone else's behalf.");
        if (k.getInitiatorId().equals(me.getId())) throw new InvalidProfileException("You flagged this person, so you cannot plead for them.");
        Instant now = Instant.now();
        boolean standing = pleas.findBySpaceIdAndEndsAtAfter(postId, now).stream()
                .anyMatch(x -> cases.findById(x.getProcessId()).filter(c -> c.getState() == CollaboratorCase.State.RUNNING).isPresent());
        if (standing) throw new InvalidProfileException("A plea is already standing. Wait for it to end.");
        if (pleas.existsBySpaceIdAndPleaderIdAndCreatedAtAfter(postId, me.getId(), now.minusSeconds(7 * 86400L))) throw new InvalidProfileException("You have used your plea for this week.");
        pleas.save(new Plea(k.getId(), postId, me.getId(), now, now.plusSeconds(3600 * PLEA_HOURS)));
        k.extend(PLEA_HOURS);
        cases.save(k);
        notifications.notify(k.getTargetId(), Notification.Bucket.SPACES, "Someone entered a plea for you", me.getUsername() + " bought you " + PLEA_HOURS + " more hours.", wespaceLink(postId));
        threads.announceWeSpace(postId, me.getUsername() + " entered a plea for " + name(k.getTargetId()) + ": " + PLEA_HOURS + " more hours to reach them.");
        return one(k, me);
    }

    /** Only the founder ends a timed process early, whether the clock is still running or the vote is open. */
    public CaseView cancel(User me, UUID postId, UUID id) {
        Post post = inRoom(me, postId);
        CollaboratorCase k = open(postId, id);
        if (!post.getAuthorId().equals(me.getId())) throw new ForbiddenException("Only the founder can cancel this.");
        end(k, CollaboratorCase.State.CANCELLED);
        threads.announceWeSpace(postId, me.getUsername() + " cancelled the flag on " + name(k.getTargetId()) + ".");
        return one(k, me);
    }

    /** The founder and the other collaborators choose; the first choice to reach a majority is carried out. */
    public CaseView vote(User me, UUID postId, UUID id, String rawChoice) {
        Post post = inRoom(me, postId);
        CollaboratorCase k = open(postId, id);
        if (k.getState() != CollaboratorCase.State.VOTING) throw new InvalidProfileException("Voting opens when the clock runs out.");
        Set<UUID> eligible = voters(post, k);
        if (!eligible.contains(me.getId())) throw new ForbiddenException("You cannot vote on this one.");
        CaseVote.Choice choice;
        try { choice = CaseVote.Choice.valueOf(rawChoice == null ? "" : rawChoice.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new InvalidProfileException("Vote to freeze or to disband."); }
        CaseVote v = votes.findByCaseIdAndVoterId(k.getId(), me.getId()).orElse(null);
        if (v == null) votes.save(new CaseVote(k.getId(), me.getId(), choice)); else { v.setChoice(choice); votes.save(v); }

        Map<CaseVote.Choice, Long> tally = votes.findByCaseId(k.getId()).stream().collect(Collectors.groupingBy(CaseVote::getChoice, Collectors.counting()));
        long needed = eligible.size() / 2 + 1;
        for (CaseVote.Choice c : CaseVote.Choice.values()) {
            if (tally.getOrDefault(c, 0L) < needed) continue;
            User target = users.findById(k.getTargetId()).orElseThrow(() -> new ResourceNotFoundException("No such collaborator."));
            Collaborator seat = collaborators.findByPostIdAndUserId(postId, target.getId()).orElseThrow(() -> new ResourceNotFoundException("No such collaborator."));
            if (c == CaseVote.Choice.FREEZE) weSpace.freezeSeat(post, seat, target, "The collaborators' vote");
            else weSpace.disbandSeat(post, seat, target, "The collaborators' vote", k.getReason());
            break;
        }
        return one(k, me);
    }

    /** Time's up for everyone whose clock has run out: voting opens, or the case is dropped if the person is no longer active. */
    public void settleDue(Instant now) {
        for (CollaboratorCase k : cases.findByStateAndDeadlineBefore(CollaboratorCase.State.RUNNING, now)) openVoting(k);
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    void sweep() { settleDue(Instant.now()); }

    private void openVoting(CollaboratorCase k) {
        Post post = posts.findById(k.getPostId()).orElse(null);
        if (post == null || !active(k.getPostId(), k.getTargetId())) { end(k, CollaboratorCase.State.CANCELLED); return; }
        k.setState(CollaboratorCase.State.VOTING);
        cases.save(k);
        notifications.resolve("ccase:" + k.getId());
        threads.announceWeSpace(k.getPostId(), name(k.getTargetId()) + " did not respond in time. The founder and collaborators now vote: freeze or disband.");
        for (UUID voter : voters(post, k)) {
            notifications.notify(voter, Notification.Bucket.SPACES, "A vote is open", name(k.getTargetId()) + " went quiet on \"" + post.getTitle() + "\". Freeze or disband?", wespaceLink(k.getPostId()));
        }
    }

    /** The founder and every active collaborator except the one being judged. */
    private Set<UUID> voters(Post post, CollaboratorCase k) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(post.getAuthorId());
        collaborators.findByPostIdAndStateInOrderByCreatedAtAsc(post.getId(), List.of(Collaborator.State.ACTIVE)).forEach(c -> ids.add(c.getUserId()));
        ids.remove(k.getTargetId());
        return ids;
    }

    private void end(CollaboratorCase k, CollaboratorCase.State next) {
        k.setState(next);
        cases.save(k);
        notifications.resolve("ccase:" + k.getId());
    }

    /** The founder and active collaborators only; before anything else, any clock that has run out is settled. */
    private Post inRoom(User me, UUID postId) {
        Post post = posts.findById(postId).orElseThrow(() -> new ResourceNotFoundException(GONE));
        if (!post.getAuthorId().equals(me.getId()) && !active(postId, me.getId())) throw new ResourceNotFoundException(GONE);
        Instant now = Instant.now();
        cases.findByPostIdAndState(postId, CollaboratorCase.State.RUNNING).stream().filter(k -> k.getDeadline().isBefore(now)).forEach(this::openVoting);
        return post;
    }

    private CollaboratorCase open(UUID postId, UUID id) {
        CollaboratorCase k = cases.findByIdAndPostId(id, postId).orElseThrow(() -> new ResourceNotFoundException("No such flag."));
        if (!k.isOpen()) throw new InvalidProfileException("This flag has already ended.");
        return k;
    }

    private boolean active(UUID postId, UUID userId) { return collaborators.existsByPostIdAndUserIdAndState(postId, userId, Collaborator.State.ACTIVE); }

    private String name(UUID userId) { return users.findById(userId).map(User::getUsername).orElse("someone"); }

    private String wespaceLink(UUID postId) { return "/HTML-pages/wespace.html?post=" + postId; }

    private CaseView one(CollaboratorCase k, User me) {
        Map<UUID, User> people = users.findAllById(List.of(k.getTargetId(), k.getInitiatorId())).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return present(List.of(k), people, me).get(0);
    }

    private List<CaseView> present(List<CollaboratorCase> rows, Map<UUID, User> people, User me) {
        Instant now = Instant.now();
        List<UUID> ids = rows.stream().map(CollaboratorCase::getId).toList();
        Map<UUID, Plea> live = new HashMap<>();
        pleas.findByProcessIdIn(ids).stream().filter(x -> x.getEndsAt().isAfter(now))
                .forEach(x -> live.merge(x.getProcessId(), x, (a, b) -> a.getEndsAt().isAfter(b.getEndsAt()) ? a : b));
        Map<UUID, User> pleaders = users.findAllById(live.values().stream().map(Plea::getPleaderId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, List<CaseVote>> ballots = votes.findByCaseIdIn(ids).stream().collect(Collectors.groupingBy(CaseVote::getCaseId));
        Map<UUID, Post> postById = new HashMap<>();
        List<CaseView> out = new ArrayList<>();
        for (CollaboratorCase k : rows) {
            User target = people.get(k.getTargetId()), by = people.get(k.getInitiatorId());
            if (target == null || by == null) continue;
            Plea plea = k.getState() == CollaboratorCase.State.RUNNING ? live.get(k.getId()) : null;
            User pleader = plea == null ? null : pleaders.get(plea.getPleaderId());
            List<CaseVote> cast = ballots.getOrDefault(k.getId(), List.of());
            Post post = postById.computeIfAbsent(k.getPostId(), id -> posts.findById(id).orElse(null));
            Set<UUID> eligible = k.getState() == CollaboratorCase.State.VOTING && post != null ? voters(post, k) : Set.of();
            String mine = cast.stream().filter(v -> v.getVoterId().equals(me.getId())).map(v -> v.getChoice().name()).findFirst().orElse(null);
            out.add(new CaseView(k.getId(), PersonDto.of(target), PersonDto.of(by), k.getReason(), k.getState().name(), k.getStartedAt(), k.getDeadline(),
                    pleader == null ? null : new PleaView(PersonDto.of(pleader), plea.getEndsAt()),
                    (int) cast.stream().filter(v -> v.getChoice() == CaseVote.Choice.FREEZE).count(),
                    (int) cast.stream().filter(v -> v.getChoice() == CaseVote.Choice.DISBAND).count(),
                    eligible.size(), mine, eligible.contains(me.getId())));
        }
        return out;
    }
}
