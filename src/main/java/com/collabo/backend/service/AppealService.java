package com.collabo.backend.service;

import com.collabo.backend.dto.AppealDtos.AppealDetail;
import com.collabo.backend.dto.AppealDtos.AppealView;
import com.collabo.backend.dto.AppealDtos.HistoryLine;
import com.collabo.backend.dto.RemovalRecordDtos.RecordView;
import com.collabo.backend.entity.*;
import com.collabo.backend.entity.Notification.Bucket;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A removed person reports the termination once; a moderator reads only the room's yarns around it and decides whether the
 * badge sticks. The removal itself always stands. Consent to the moderator reading that window is a T&C clause, not code.
 */
@Service
@Transactional
public class AppealService {

    static final int MAX_NOTE = 1000;
    static final Duration BEFORE = Duration.ofHours(24), AFTER = Duration.ofHours(1);

    private final AppealRepository appeals;
    private final RemovalRecordRepository records;
    private final RemovalRecordService recordViews;
    private final SpaceRepository spaces;
    private final SpaceThreadService spaceThreads;
    private final YarnRepository yarns;
    private final UserRepository users;
    private final NotificationService notifications;

    public AppealService(AppealRepository appeals, RemovalRecordRepository records, RemovalRecordService recordViews, SpaceRepository spaces,
                         SpaceThreadService spaceThreads, YarnRepository yarns, UserRepository users, NotificationService notifications) {
        this.appeals = appeals; this.records = records; this.recordViews = recordViews; this.spaces = spaces; this.spaceThreads = spaceThreads;
        this.yarns = yarns; this.users = users; this.notifications = notifications;
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
        return view(a, r);
    }

    @Transactional(readOnly = true)
    public List<AppealView> queue(User me) {
        requireModerator(me);
        List<Appeal> open = appeals.findTop100ByOutcomeIsNullOrderByCreatedAtAsc();
        Map<UUID, RemovalRecord> rows = records.findAllById(open.stream().map(Appeal::getRecordId).toList()).stream().collect(Collectors.toMap(RemovalRecord::getId, Function.identity()));
        return open.stream().filter(a -> rows.containsKey(a.getRecordId())).map(a -> view(a, rows.get(a.getRecordId()))).toList();
    }

    @Transactional(readOnly = true)
    public AppealDetail detail(User me, UUID id) {
        requireModerator(me);
        Appeal a = appeals.findById(id).orElseThrow(() -> new ResourceNotFoundException("No such appeal."));
        RemovalRecord r = records.findById(a.getRecordId()).orElseThrow(() -> new ResourceNotFoundException("No such appeal."));
        AppealView v = view(a, r);
        return new AppealDetail(v.id(), v.note(), v.outcome(), v.createdAt(), v.record(), history(r));
    }

    public AppealView decide(User me, UUID id, String outcome) {
        requireModerator(me);
        Appeal a = appeals.findById(id).orElseThrow(() -> new ResourceNotFoundException("No such appeal."));
        Appeal.Outcome o;
        try { o = Appeal.Outcome.valueOf(outcome == null ? "" : outcome.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new InvalidProfileException("Decide STICKS or DROPS."); }
        if (!a.isOpen()) throw new InvalidProfileException("This appeal has already been decided.");
        RemovalRecord r = records.findById(a.getRecordId()).orElseThrow(() -> new ResourceNotFoundException("No such appeal."));
        a.decide(o, me.getId());
        appeals.save(a);
        if (o == Appeal.Outcome.DROPS) { r.dropBadge(); records.save(r); }
        notifications.notify(a.getAppellantId(), Bucket.SPACES, "Your appeal was decided",
                o == Appeal.Outcome.DROPS ? "The badge for \"" + r.getSpaceName() + "\" was dropped. The removal itself stands."
                        : "The badge for \"" + r.getSpaceName() + "\" stays.", "/HTML-pages/profile.html?u=" + nameOf(a.getAppellantId()));
        return view(a, r);
    }

    /** Only the appeal's own room, only its yarns in the window: a day before the removal to an hour after. */
    private List<HistoryLine> history(RemovalRecord r) {
        Space s = spaces.findById(r.getSpaceId()).orElse(null);
        UUID thread = s == null ? null : spaceThreads.workspaceId(s.getPostId());
        if (thread == null) return List.of();
        List<Yarn> lines = yarns.findByThreadIdAndCreatedAtBetweenOrderByCreatedAtAsc(thread, r.getCreatedAt().minus(BEFORE), r.getCreatedAt().plus(AFTER));
        Map<UUID, String> names = users.findAllById(lines.stream().map(Yarn::getSenderId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        return lines.stream().map(y -> new HistoryLine(y.getSenderId() == null ? null : names.get(y.getSenderId()), y.getKind() == Yarn.Kind.SYSTEM, y.getBody(), y.getCreatedAt())).toList();
    }

    private AppealView view(Appeal a, RemovalRecord r) {
        RecordView rv = recordViews.present(List.of(r)).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("No such record."));
        return new AppealView(a.getId(), a.getNote(), a.getOutcome() == null ? null : a.getOutcome().name(), a.getCreatedAt(), rv);
    }

    private void requireModerator(User me) {
        if (me.getRole() != Role.MODERATOR && me.getRole() != Role.ADMIN) throw new ForbiddenException("Moderators only.");
    }

    private String nameOf(UUID userId) { return users.findById(userId).map(User::getUsername).orElse(""); }
}
