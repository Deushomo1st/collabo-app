package com.collabo.backend.service;

import com.collabo.backend.dto.MilestoneDtos.MilestoneResponse;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A space's milestones. Fulfilling one stamps and credits everyone in the room at that moment. */
@Service
@Transactional
public class MilestoneService {

    static final int MAX_TITLE = 120;
    static final int MAX_NOTE = 500;
    private static final String GONE = "No such space.";

    private final SpaceRepository spaces;
    private final MilestoneRepository milestones;
    private final MilestoneCreditRepository credits;
    private final SpaceMemberRepository members;
    private final UserRepository users;
    private final SpaceService spaceAccess;
    private final SpaceThreadService spaceThreads;
    private final CredentialService credentials;

    public MilestoneService(SpaceRepository spaces, MilestoneRepository milestones, MilestoneCreditRepository credits, SpaceMemberRepository members,
                            UserRepository users, SpaceService spaceAccess, SpaceThreadService spaceThreads, CredentialService credentials) {
        this.spaces = spaces; this.milestones = milestones; this.credits = credits; this.members = members;
        this.users = users; this.spaceAccess = spaceAccess; this.spaceThreads = spaceThreads; this.credentials = credentials;
    }

    public MilestoneResponse create(User me, UUID spaceId, String rawTitle) {
        Space s = writable(me, spaceId);
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty()) throw new InvalidProfileException("Give the milestone a title.");
        if (title.length() > MAX_TITLE) throw new InvalidProfileException("Keep the title under " + MAX_TITLE + " characters.");
        return one(milestones.save(new Milestone(s.getId(), title, me.getId())));
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> list(User me, UUID spaceId) {
        Space s = readable(me, spaceId);
        List<Milestone> rows = milestones.findBySpaceIdOrderByCreatedAtDesc(s.getId());
        Map<UUID, List<MilestoneCredit>> stamps = credits.findByMilestoneIdIn(rows.stream().map(Milestone::getId).toList())
                .stream().collect(Collectors.groupingBy(MilestoneCredit::getMilestoneId));
        Set<UUID> ids = new HashSet<>();
        rows.forEach(m -> ids.add(m.getCreatedBy()));
        stamps.values().forEach(l -> l.forEach(c -> ids.add(c.getUserId())));
        Map<UUID, User> people = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().map(m -> present(m, stamps.getOrDefault(m.getId(), List.of()), people)).toList();
    }

    public MilestoneResponse fulfil(User me, UUID spaceId, UUID milestoneId, String rawNote) {
        Space s = writable(me, spaceId);
        Milestone m = milestones.findByIdAndSpaceId(milestoneId, s.getId()).orElseThrow(() -> new ResourceNotFoundException("No such milestone."));
        if (m.isFulfilled()) throw new InvalidProfileException("This milestone is already fulfilled.");
        String note = rawNote == null ? "" : rawNote.trim();
        if (note.length() > MAX_NOTE) throw new InvalidProfileException("Keep the note under " + MAX_NOTE + " characters.");
        m.fulfil(note);
        milestones.save(m);
        // everyone in the room right now, owner included; nobody is picked, so nobody can be left out by hand
        for (SpaceMember member : members.findBySpaceIdAndStateOrderByJoinedAtAsc(s.getId(), SpaceMember.State.ACTIVE)) {
            credits.save(new MilestoneCredit(m.getId(), member.getUserId()));
            credentials.record(member.getUserId(), CredentialKind.MILESTONE_CREDITED, m.getTitle(), s.getName(), "milestone", m.getId().toString(), null);
        }
        spaceThreads.announce(s, "Milestone fulfilled: " + m.getTitle() + (note.isEmpty() ? "." : ". " + note));
        return one(m);
    }

    /** Only an objective nobody has been credited for can be dropped. */
    public void delete(User me, UUID spaceId, UUID milestoneId) {
        Space s = writable(me, spaceId);
        Milestone m = milestones.findByIdAndSpaceId(milestoneId, s.getId()).orElseThrow(() -> new ResourceNotFoundException("No such milestone."));
        if (m.isFulfilled()) throw new InvalidProfileException("A fulfilled milestone stays on the record.");
        milestones.delete(m);
    }

    /** A credited member takes themselves off a milestone they had no hand in; their credential for it goes too. */
    public void optOut(User me, UUID spaceId, UUID milestoneId) {
        Space s = readable(me, spaceId);
        Milestone m = milestones.findByIdAndSpaceId(milestoneId, s.getId()).orElseThrow(() -> new ResourceNotFoundException("No such milestone."));
        MilestoneCredit stamp = credits.findByMilestoneIdAndUserId(m.getId(), me.getId())
                .orElseThrow(() -> new InvalidProfileException("You are not credited on this milestone."));
        credits.delete(stamp);
        credentials.withdraw(me.getId(), "milestone", m.getId().toString());
    }

    private Space readable(User me, UUID spaceId) {
        Space s = spaces.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException(GONE));
        if (spaceAccess.roleOf(me, s) == null) throw new ResourceNotFoundException(GONE);
        return s;
    }

    private Space writable(User me, UUID spaceId) {
        Space s = readable(me, spaceId);
        if (!spaceAccess.can(me, s, SpacePermission.LOG_MILESTONES)) throw new ForbiddenException("You do not have permission to log milestones here.");
        return s;
    }

    private MilestoneResponse one(Milestone m) {
        List<MilestoneCredit> stamps = credits.findByMilestoneIdIn(List.of(m.getId()));
        Set<UUID> ids = new HashSet<>(List.of(m.getCreatedBy()));
        stamps.forEach(c -> ids.add(c.getUserId()));
        return present(m, stamps, users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity())));
    }

    private static MilestoneResponse present(Milestone m, List<MilestoneCredit> stamps, Map<UUID, User> people) {
        List<PersonDto> credited = stamps.stream().map(c -> people.get(c.getUserId())).filter(Objects::nonNull).map(PersonDto::of).toList();
        User by = people.get(m.getCreatedBy());
        return new MilestoneResponse(m.getId(), m.getTitle(), m.getNote(), m.isFulfilled(), m.getFulfilledAt(),
                by == null ? null : PersonDto.of(by), credited, m.getCreatedAt());
    }
}
