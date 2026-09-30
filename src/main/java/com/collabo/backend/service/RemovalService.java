package com.collabo.backend.service;

import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.dto.RemovalDtos.PleaView;
import com.collabo.backend.dto.RemovalDtos.RemovalResponse;
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
 * Removal for inefficiency, the way members govern a Workspace. A member flags another as quiet with a reason; the target is
 * nudged in MySpace and has the space's response clock to answer; pleas can buy time; the founder can cancel; if the time
 * runs out the member is removed and the room says who removed whom and why. Time is checked lazily (before any action on the
 * space) and by a one-minute sweep.
 */
@Service
@Transactional
public class RemovalService {

    static final int MAX_REASON = 300;
    static final long PLEA_HOURS = 12;
    private static final String GONE = "No such space.";

    private final SpaceRepository spaces;
    private final RemovalProcessRepository processes;
    private final PleaRepository pleas;
    private final SpaceMemberRepository members;
    private final CollaboratorRepository collaborators;
    private final UserRepository users;
    private final SpaceService spaceService;
    private final SpaceThreadService spaceThreads;
    private final YarnService yarns;

    public RemovalService(SpaceRepository spaces, RemovalProcessRepository processes, PleaRepository pleas, SpaceMemberRepository members,
                          CollaboratorRepository collaborators, UserRepository users, SpaceService spaceService, SpaceThreadService spaceThreads,
                          YarnService yarns) {
        this.spaces = spaces; this.processes = processes; this.pleas = pleas; this.members = members; this.collaborators = collaborators;
        this.users = users; this.spaceService = spaceService; this.spaceThreads = spaceThreads; this.yarns = yarns;
    }

    public RemovalResponse start(User me, UUID spaceId, String username, String rawReason) {
        Space s = inRoom(me, spaceId);
        User target = users.findByUsername(username == null ? "" : username.trim()).orElseThrow(() -> new InvalidProfileException("Name someone in this room."));
        if (target.getId().equals(me.getId())) throw new InvalidProfileException("You cannot flag yourself.");
        if (target.getId().equals(s.getOwnerId())) throw new InvalidProfileException("The founder cannot be flagged.");
        boolean seated = members.findBySpaceIdAndUserId(s.getId(), target.getId()).filter(m -> m.getState() == SpaceMember.State.ACTIVE).isPresent();
        if (!seated) throw new InvalidProfileException("Name someone who is in this room.");
        if (collaborators.existsByPostIdAndUserIdAndState(s.getPostId(), target.getId(), Collaborator.State.ACTIVE)) {
            throw new InvalidProfileException("Collaborators are not removed this way; their process belongs to the WeSpace.");
        }
        if (processes.existsBySpaceIdAndTargetIdAndState(s.getId(), target.getId(), RemovalProcess.State.RUNNING)) {
            throw new InvalidProfileException("That person is already flagged.");
        }
        String reason = rawReason == null ? "" : rawReason.trim();
        if (reason.isEmpty() || reason.length() > MAX_REASON) throw new InvalidProfileException("Give a reason of up to " + MAX_REASON + " characters.");

        RemovalProcess p = processes.save(new RemovalProcess(s.getId(), target.getId(), me.getId(), reason,
                Instant.now().plusSeconds(3600L * s.getResponseClockHours())));
        spaceThreads.announce(s, me.getUsername() + " flagged " + target.getUsername() + " as quiet: " + reason + ". They have "
                + s.getResponseClockHours() + " hours to respond.");
        yarns.systemNote(me, target, me.getUsername() + " says you have gone quiet in \"" + s.getName() + "\": " + reason
                + ". Respond in the space within " + s.getResponseClockHours() + " hours to stay.");
        return one(p);
    }

    public List<RemovalResponse> list(User me, UUID spaceId) {
        Space s = inRoom(me, spaceId);
        List<RemovalProcess> rows = processes.findTop50BySpaceIdOrderByStartedAtDesc(s.getId());
        Map<UUID, User> people = users.findAllById(rows.stream().flatMap(p -> java.util.stream.Stream.of(p.getTargetId(), p.getInitiatorId())).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return present(rows, people);
    }

    /** The flagged person answers, which ends the process. */
    public RemovalResponse respond(User me, UUID spaceId, UUID id) {
        Space s = inRoom(me, spaceId);
        RemovalProcess p = running(s, id);
        if (!p.getTargetId().equals(me.getId())) throw new ForbiddenException("Only the flagged person can answer.");
        p.resolve(RemovalProcess.State.RESPONDED);
        processes.save(p);
        spaceThreads.announce(s, me.getUsername() + " responded. The removal process ended.");
        return one(p);
    }

    /** A plea buys twelve hours: one active at a time in a space, one per person per week, and pleas can be switched off. */
    public RemovalResponse plea(User me, UUID spaceId, UUID id) {
        Space s = inRoom(me, spaceId);
        RemovalProcess p = running(s, id);
        if (!s.isPleasEnabled()) throw new InvalidProfileException("Pleas are switched off in this space.");
        if (p.getTargetId().equals(me.getId())) throw new InvalidProfileException("A plea is made on someone else's behalf.");
        if (p.getInitiatorId().equals(me.getId())) throw new InvalidProfileException("You flagged this person, so you cannot plead for them.");
        Instant now = Instant.now();
        boolean standing = pleas.findBySpaceIdAndEndsAtAfter(s.getId(), now).stream()
                .anyMatch(x -> processes.findById(x.getProcessId()).filter(RemovalProcess::isRunning).isPresent());
        if (standing) throw new InvalidProfileException("A plea is already standing. Wait for it to end.");
        if (pleas.existsBySpaceIdAndPleaderIdAndCreatedAtAfter(s.getId(), me.getId(), now.minusSeconds(7 * 86400L))) {
            throw new InvalidProfileException("You have used your plea for this week.");
        }
        pleas.save(new Plea(p.getId(), s.getId(), me.getId(), now, now.plusSeconds(3600 * PLEA_HOURS)));
        p.extend(PLEA_HOURS);
        processes.save(p);
        spaceThreads.announce(s, me.getUsername() + " entered a plea for " + name(p.getTargetId()) + ": " + PLEA_HOURS + " more hours to reach them.");
        return one(p);
    }

    /** Only the founder ends a timed process early. */
    public RemovalResponse cancel(User me, UUID spaceId, UUID id) {
        Space s = inRoom(me, spaceId);
        RemovalProcess p = running(s, id);
        if (!s.getOwnerId().equals(me.getId())) throw new ForbiddenException("Only the founder can cancel this.");
        p.resolve(RemovalProcess.State.CANCELLED);
        processes.save(p);
        spaceThreads.announce(s, me.getUsername() + " cancelled the removal process for " + name(p.getTargetId()) + ".");
        return one(p);
    }

    /** Removes everyone whose time has run out, and tells the room and their DM why. */
    public void settleDue(Instant now) {
        for (RemovalProcess p : processes.findByStateAndDeadlineBefore(RemovalProcess.State.RUNNING, now)) complete(p);
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    void sweep() { settleDue(Instant.now()); }

    private void complete(RemovalProcess p) {
        Space s = spaces.findById(p.getSpaceId()).orElse(null);
        User target = users.findById(p.getTargetId()).orElse(null);
        User by = users.findById(p.getInitiatorId()).orElse(null);
        p.resolve(RemovalProcess.State.COMPLETED);
        processes.save(p);
        if (s == null || target == null || by == null) return;
        spaceService.unseat(s, target.getId());
        spaceThreads.announce(s, by.getUsername() + " removed " + target.getUsername() + " for: " + p.getReason());
        yarns.systemNote(by, target, "You were removed from \"" + s.getName() + "\" for: " + p.getReason());
    }

    /** Owner and members only; before anything else is decided, anyone whose time is up is settled. */
    private Space inRoom(User me, UUID spaceId) {
        Space s = spaces.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException(GONE));
        String role = spaceService.roleOf(me, s);
        if (role == null || role.equals("APPLICANT")) throw new ResourceNotFoundException(GONE);
        Instant now = Instant.now();
        processes.findBySpaceIdAndState(s.getId(), RemovalProcess.State.RUNNING).stream().filter(p -> p.getDeadline().isBefore(now)).forEach(this::complete);
        return s;
    }

    private RemovalProcess running(Space s, UUID id) {
        RemovalProcess p = processes.findByIdAndSpaceId(id, s.getId()).orElseThrow(() -> new ResourceNotFoundException("No such process."));
        if (!p.isRunning()) throw new InvalidProfileException("This process has already ended.");
        return p;
    }

    private String name(UUID userId) { return users.findById(userId).map(User::getUsername).orElse("someone"); }

    private RemovalResponse one(RemovalProcess p) {
        Map<UUID, User> people = users.findAllById(List.of(p.getTargetId(), p.getInitiatorId())).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return present(List.of(p), people).get(0);
    }

    private List<RemovalResponse> present(List<RemovalProcess> rows, Map<UUID, User> people) {
        Map<UUID, Plea> live = new HashMap<>();
        Instant now = Instant.now();
        pleas.findByProcessIdIn(rows.stream().map(RemovalProcess::getId).toList()).stream().filter(x -> x.getEndsAt().isAfter(now))
                .forEach(x -> live.merge(x.getProcessId(), x, (a, b) -> a.getEndsAt().isAfter(b.getEndsAt()) ? a : b));
        Set<UUID> pleaders = live.values().stream().map(Plea::getPleaderId).collect(Collectors.toSet());
        Map<UUID, User> pleaderUsers = users.findAllById(pleaders).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<RemovalResponse> out = new ArrayList<>();
        for (RemovalProcess p : rows) {
            User target = people.get(p.getTargetId()), by = people.get(p.getInitiatorId());
            if (target == null || by == null) continue;
            Plea plea = p.isRunning() ? live.get(p.getId()) : null;
            User pleader = plea == null ? null : pleaderUsers.get(plea.getPleaderId());
            out.add(new RemovalResponse(p.getId(), PersonDto.of(target), PersonDto.of(by), p.getReason(), p.getState().name(),
                    p.getStartedAt(), p.getDeadline(), pleader == null ? null : new PleaView(PersonDto.of(pleader), plea.getEndsAt())));
        }
        return out;
    }
}
