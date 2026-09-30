package com.collabo.backend.service;

import com.collabo.backend.dto.AppealDtos.AppealDetail;
import com.collabo.backend.dto.AppealDtos.AppealView;
import com.collabo.backend.dto.AppealDtos.HistoryLine;
import com.collabo.backend.dto.RemovalRecordDtos.RecordView;
import com.collabo.backend.entity.*;
import com.collabo.backend.entity.Notification.Bucket;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.live.LiveSignals;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A removed person reports the termination once. That opens an investigation (kind APPEAL) on the room's yarns around the
 * removal, and the admin decides whether the badge sticks. The removal itself always stands. Consent to a moderator reading
 * that window is a T&C clause, not code.
 */
@Service
@Transactional
public class AppealService {

    static final int MAX_NOTE = 1000;
    static final Duration BEFORE = Duration.ofHours(24), AFTER = Duration.ofHours(1);
    private static final String DECIDER = "Admin";   // the admin key carries no identity

    private final AppealRepository appeals;
    private final RemovalRecordRepository records;
    private final RemovalRecordService recordViews;
    private final SpaceRepository spaces;
    private final SpaceThreadService spaceThreads;
    private final YarnRepository yarns;
    private final UserRepository users;
    private final NotificationService notifications;
    private final InvestigationRepository investigations;
    private final LiveSignals signals;

    public AppealService(AppealRepository appeals, RemovalRecordRepository records, RemovalRecordService recordViews, SpaceRepository spaces,
                         SpaceThreadService spaceThreads, YarnRepository yarns, UserRepository users, NotificationService notifications,
                         InvestigationRepository investigations, LiveSignals signals) {
        this.signals = signals;
        this.appeals = appeals; this.records = records; this.recordViews = recordViews; this.spaces = spaces; this.spaceThreads = spaceThreads;
        this.yarns = yarns; this.users = users; this.notifications = notifications; this.investigations = investigations;
    }

    public AppealView appeal(User me, UUID recordId, String raw) {
        RemovalRecord r = records.findById(recordId).orElseThrow(() -> new ResourceNotFoundException("No such record."));
        if (!r.getRemovedId().equals(me.getId())) throw new ForbiddenException("Only the removed person can appeal.");
        String note = raw == null ? "" : raw.trim();
        if (note.isEmpty() || note.length() > MAX_NOTE) throw new InvalidProfileException("Say what went wrong, up to " + MAX_NOTE + " characters.");
        if (!r.isBadge()) throw new InvalidProfileException("There is no badge on this record to appeal.");
        if (appeals.findByRecordId(r.getId()).isPresent()) throw new InvalidProfileException("This removal has already been appealed.");
        Appeal a;
        try { a = appeals.saveAndFlush(new Appeal(r.getId(), me.getId(), note)); }
        catch (org.springframework.dao.DataIntegrityViolationException e) {   // two at once: the unique record_id wins
            throw new InvalidProfileException("This removal has already been appealed.");
        }
        investigations.save(Investigation.forAppeal(roomOf(r), a, r.getCreatedAt().minus(BEFORE), r.getCreatedAt().plus(AFTER)));
        signals.adminQueue();
        return view(a, r);
    }

    @Transactional(readOnly = true)
    public AppealDetail detail(UUID id) {
        Appeal a = appeals.findById(id).orElseThrow(() -> new ResourceNotFoundException("No such appeal."));
        RemovalRecord r = records.findById(a.getRecordId()).orElseThrow(() -> new ResourceNotFoundException("No such appeal."));
        AppealView v = view(a, r);
        return new AppealDetail(v.id(), v.note(), v.outcome(), v.createdAt(), v.decidedBy(), v.decidedAt(), v.record(), history(r));
    }

    /** The admin rules on the badge; the investigation closes with it. */
    public AppealView decide(UUID id, String outcome) {
        Appeal a = appeals.findById(id).orElseThrow(() -> new ResourceNotFoundException("No such appeal."));
        Appeal.Outcome o;
        try { o = Appeal.Outcome.valueOf(outcome == null ? "" : outcome.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new InvalidProfileException("Decide STICKS or DROPS."); }
        if (!a.isOpen()) throw new InvalidProfileException("This appeal has already been decided.");
        RemovalRecord r = records.findById(a.getRecordId()).orElseThrow(() -> new ResourceNotFoundException("No such appeal."));
        a.decide(o, null);
        appeals.save(a);
        if (o == Appeal.Outcome.DROPS) { r.dropBadge(); records.save(r); }
        investigations.findByAppealId(a.getId()).ifPresent(i -> { i.close(); investigations.save(i); });
        notifications.notify(a.getAppellantId(), Bucket.SPACES, "Your appeal was decided",
                o == Appeal.Outcome.DROPS ? "The badge for \"" + r.getSpaceName() + "\" was dropped. The removal itself stands."
                        : "The badge for \"" + r.getSpaceName() + "\" stays.", "/HTML-pages/profile.html?u=" + nameOf(a.getAppellantId()));
        return view(a, r);
    }

    private UUID roomOf(RemovalRecord r) {
        Space s = spaces.findById(r.getSpaceId()).orElse(null);
        return s == null ? null : spaceThreads.workspaceId(s.getPostId());
    }

    /** Only the appeal's own room, only its yarns in the window: a day before the removal to an hour after. */
    private List<HistoryLine> history(RemovalRecord r) {
        UUID thread = roomOf(r);
        if (thread == null) return List.of();
        List<Yarn> lines = yarns.findByThreadIdAndCreatedAtBetweenOrderByCreatedAtAsc(thread, r.getCreatedAt().minus(BEFORE), r.getCreatedAt().plus(AFTER));
        Map<UUID, String> names = users.findAllById(lines.stream().map(Yarn::getSenderId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        return lines.stream().map(y -> new HistoryLine(y.getSenderId() == null ? null : names.get(y.getSenderId()), y.getKind() == Yarn.Kind.SYSTEM, y.getBody(), y.getCreatedAt())).toList();
    }

    private AppealView view(Appeal a, RemovalRecord r) {
        RecordView rv = recordViews.present(List.of(r)).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("No such record."));
        return new AppealView(a.getId(), a.getNote(), a.getOutcome() == null ? null : a.getOutcome().name(), a.getCreatedAt(),
                a.getDecidedAt() == null ? null : DECIDER, a.getDecidedAt(), rv);
    }

    private String nameOf(UUID userId) { return users.findById(userId).map(User::getUsername).orElse(""); }
}
