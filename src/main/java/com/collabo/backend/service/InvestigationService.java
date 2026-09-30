package com.collabo.backend.service;

import com.collabo.backend.dto.InvestigationDtos.InvestigationDetail;
import com.collabo.backend.dto.InvestigationDtos.InvestigationView;
import com.collabo.backend.entity.*;
import com.collabo.backend.entity.Notification.Bucket;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.live.LiveSignals;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Investigations into a Yarnspace of any tier. A member reports a thread they sit in; the admin assigns a moderator, and the
 * thread's members are told one was introduced. Appeals arrive here too (AppealService opens them).
 */
@Service
@Transactional
public class InvestigationService {

    static final int MAX_REASON = 1000;

    private final InvestigationRepository investigations;
    private final YarnThreadRepository threads;
    private final ThreadMemberRepository seats;
    private final UserRepository users;
    private final ModeratorRepository moderators;
    private final AppealService appeals;
    private final NotificationService notifications;
    private final FindingService findings;
    private final LiveSignals signals;

    public InvestigationService(InvestigationRepository investigations, YarnThreadRepository threads, ThreadMemberRepository seats,
                                UserRepository users, ModeratorRepository moderators, AppealService appeals, NotificationService notifications,
                                FindingService findings, LiveSignals signals) {
        this.signals = signals;
        this.investigations = investigations; this.threads = threads; this.seats = seats; this.users = users;
        this.moderators = moderators; this.appeals = appeals; this.notifications = notifications; this.findings = findings;
    }

    /** A member reports a Yarnspace they sit in. Anyone else gets the same "no such thread" as for a missing one. */
    public InvestigationView report(User me, UUID threadId, String raw) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.isEmpty() || reason.length() > MAX_REASON) throw new InvalidProfileException("Say what is wrong, up to " + MAX_REASON + " characters.");
        if (!threads.existsById(threadId) || seats.findByThreadIdAndUserId(threadId, me.getId()).isEmpty()) throw new ResourceNotFoundException("No such yarn thread.");
        if (investigations.existsByThreadIdAndReporterIdAndKindAndStatusNot(threadId, me.getId(), Investigation.Kind.REPORT, Investigation.Status.CLOSED))
            throw new InvalidProfileException("You have already reported this, and it is being looked at.");
        Investigation saved = investigations.save(new Investigation(Investigation.Kind.REPORT, threadId, me.getId(), reason));
        signals.adminQueue();
        return view(saved);
    }

    /** status: "active" (open, assigned, reported; oldest first) or "closed" (newest first). */
    @Transactional(readOnly = true)
    public List<InvestigationView> list(String status) {
        List<Investigation> rows = "closed".equalsIgnoreCase(status)
                ? investigations.findTop200ByStatusOrderByCreatedAtDesc(Investigation.Status.CLOSED)
                : investigations.findTop200ByStatusInOrderByCreatedAtAsc(EnumSet.of(Investigation.Status.OPEN, Investigation.Status.ASSIGNED, Investigation.Status.REPORTED));
        return views(rows);
    }

    @Transactional(readOnly = true)
    public InvestigationDetail detail(UUID id) {
        Investigation i = find(id);
        List<String> members = i.getThreadId() == null ? List.of() : users.findAllById(involvedSeats(i.getThreadId())).stream().map(User::getUsername).sorted().toList();
        return new InvestigationDetail(view(i), members, i.getAppealId() == null ? null : appeals.detail(i.getAppealId()), findings.list(id));
    }

    /** Closes a report once the admin has read the findings. An appeal closes through decide. */
    public InvestigationView close(UUID id) {
        Investigation i = find(id);
        if (i.getAppealId() != null) throw new InvalidProfileException("An appeal closes when you decide its badge.");
        if (i.isClosed()) throw new InvalidProfileException("This investigation is already closed.");
        i.close();
        investigations.save(i);
        signals.adminQueue();
        if (i.getModeratorId() != null) signals.moderatorCases(i.getModeratorId());
        notifications.notify(i.getReporterId(), Bucket.SPACES, "Your report was reviewed", "A moderator looked into your report and the admin has closed it.",
                i.getThreadId() == null ? null : "/HTML-pages/yarnspaces.html#t/" + i.getThreadId());
        return view(i);
    }

    /** Puts an active moderator on it (or swaps one) and tells the people involved. */
    public InvestigationView assign(UUID id, UUID moderatorId) {
        Investigation i = find(id);
        if (i.isClosed()) throw new InvalidProfileException("This investigation is closed.");
        Moderator m = moderators.findById(moderatorId == null ? new UUID(0, 0) : moderatorId).orElseThrow(() -> new ResourceNotFoundException("No such moderator."));
        if (!m.isActive()) throw new InvalidProfileException("That moderator is deactivated.");
        UUID before = i.getModeratorId();
        i.assign(m.getId());
        investigations.save(i);
        if (before != null && !before.equals(m.getId())) signals.moderatorCases(before);   // the case leaves their desk
        signals.moderatorCases(m.getId());
        signals.adminQueue();
        InvestigationView v = view(i);
        String where = v.title().isBlank() ? "a conversation" : "\"" + v.title() + "\"";
        for (UUID userId : i.getKind() == Investigation.Kind.APPEAL || i.getThreadId() == null ? List.of(i.getReporterId()) : involvedSeats(i.getThreadId()))
            notifications.notify(userId, Bucket.SPACES, "A moderator was introduced",
                    m.getName() + " is reviewing " + where + (i.getKind() == Investigation.Kind.APPEAL ? " for your appeal." : " after a report."),
                    i.getThreadId() == null ? null : "/HTML-pages/yarnspaces.html#t/" + i.getThreadId());
        return v;
    }

    /** The admin's ruling on an appeal's badge. Reports have no ruling; they close with findings (next step). */
    public InvestigationView decide(UUID id, String outcome) {
        Investigation i = find(id);
        if (i.getAppealId() == null) throw new InvalidProfileException("Only an appeal has a badge to decide.");
        appeals.decide(i.getAppealId(), outcome);
        signals.adminQueue();
        if (i.getModeratorId() != null) signals.moderatorCases(i.getModeratorId());
        return view(find(id));
    }

    private List<UUID> involvedSeats(UUID threadId) { return seats.findByThreadId(threadId).stream().map(ThreadMember::getUserId).toList(); }

    private Investigation find(UUID id) { return investigations.findById(id).orElseThrow(() -> new ResourceNotFoundException("No such investigation.")); }

    private InvestigationView view(Investigation i) { return views(List.of(i)).get(0); }

    /** Batched, so a queue of 200 costs a handful of queries, not hundreds. */
    public List<InvestigationView> views(List<Investigation> rows) {
        Set<UUID> threadIds = rows.stream().map(Investigation::getThreadId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, YarnThread> byThread = threads.findAllById(threadIds).stream().collect(Collectors.toMap(YarnThread::getId, Function.identity()));
        Map<UUID, List<UUID>> seated = seats.findByThreadIdIn(threadIds).stream()
                .collect(Collectors.groupingBy(ThreadMember::getThreadId, Collectors.mapping(ThreadMember::getUserId, Collectors.toList())));
        Set<UUID> userIds = new HashSet<>();
        rows.forEach(r -> userIds.add(r.getReporterId()));
        byThread.values().stream().filter(t -> t.getName() == null).forEach(t -> userIds.addAll(seated.getOrDefault(t.getId(), List.of())));
        Map<UUID, String> who = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getUsername));
        Map<UUID, String> mods = moderators.findAllById(rows.stream().map(Investigation::getModeratorId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Moderator::getId, Moderator::getName));
        return rows.stream().map(r -> {
            YarnThread t = r.getThreadId() == null ? null : byThread.get(r.getThreadId());
            String title = t == null ? "" : t.getName() != null ? t.getName()   // a MySpace has no name: it is the two people in it
                    : seated.getOrDefault(t.getId(), List.of()).stream().map(u -> who.getOrDefault(u, "?")).sorted().collect(Collectors.joining(" & "));
            return new InvestigationView(r.getId(), r.getKind().name(), r.getStatus().name(), t == null ? null : t.getTier().name(), title, r.getReason(),
                    who.getOrDefault(r.getReporterId(), "(deleted)"), r.getModeratorId(), r.getModeratorId() == null ? null : mods.get(r.getModeratorId()),
                    r.getCreatedAt(), r.getAssignedAt());
        }).toList();
    }
}
