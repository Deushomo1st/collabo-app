package com.collabo.backend.service;

import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.dto.RemovalRecordDtos.AddressView;
import com.collabo.backend.dto.RemovalRecordDtos.RecordView;
import com.collabo.backend.entity.*;
import com.collabo.backend.entity.Notification.Bucket;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Removal records and the addresses on them. Your own are always yours to see; other people's need premium. */
@Service
@Transactional
public class RemovalRecordService {

    static final int MAX_ADDRESS = 1000;
    /** More than one fulfilled milestone: a single one is a short engagement, not enough for a badge. */
    static final int BADGE_MILESTONES = 2;

    private final RemovalRecordRepository records;
    private final AddressRepository addresses;
    private final MilestoneRepository milestones;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AppealRepository appeals;

    public RemovalRecordService(RemovalRecordRepository records, AddressRepository addresses, MilestoneRepository milestones,
                                UserRepository users, NotificationService notifications, AppealRepository appeals) {
        this.appeals = appeals; this.records = records; this.addresses = addresses; this.milestones = milestones; this.users = users; this.notifications = notifications;
    }

    /** Called when a removal process completes. */
    void record(RemovalProcess p, Space s) {
        long fulfilled = milestones.findBySpaceIdOrderByCreatedAtDesc(s.getId()).stream().filter(Milestone::isFulfilled).count();
        records.save(new RemovalRecord(p.getId(), s.getId(), s.getName(), p.getTargetId(), p.getInitiatorId(), p.getReason(), fulfilled >= BADGE_MILESTONES));
    }

    @Transactional(readOnly = true)
    public List<RecordView> of(User me, String username) {
        User who = users.findByUsername(username == null ? "" : username.trim()).orElseThrow(() -> new ResourceNotFoundException("No such person."));
        List<RemovalRecord> rows = records.findTop50ByRemovedIdOrderByCreatedAtDesc(who.getId());
        if (who.getId().equals(me.getId()) || me.isPremium()) return present(rows);
        // everyone else sees only the removals they carried out themselves (they are a party and may address them)
        List<RemovalRecord> mine = rows.stream().filter(r -> r.getRemovedById().equals(me.getId())).toList();
        if (mine.isEmpty()) throw new ForbiddenException("Other people's removal records are a premium feature.");
        return present(mine);
    }

    public AddressView address(User me, UUID recordId, String raw) {
        RemovalRecord r = records.findById(recordId).orElseThrow(() -> new ResourceNotFoundException("No such record."));
        boolean removed = r.getRemovedId().equals(me.getId());
        if (!removed && !r.getRemovedById().equals(me.getId())) throw new ForbiddenException("Only the two people involved can address this.");
        String body = raw == null ? "" : raw.trim();
        if (body.isEmpty() || body.length() > MAX_ADDRESS) throw new InvalidProfileException("Write up to " + MAX_ADDRESS + " characters.");
        Address a = addresses.save(new Address(r.getId(), me.getId(), body));
        UUID other = removed ? r.getRemovedById() : r.getRemovedId();
        String removedName = removed ? me.getUsername() : users.findById(r.getRemovedId()).map(User::getUsername).orElse("");
        notifications.notify(other, Bucket.ACTIVITY, me.getUsername() + " addressed the removal from \"" + r.getSpaceName() + "\"",
                body.length() > 200 ? body.substring(0, 200) + "..." : body, "/HTML-pages/profile.html?u=" + removedName);
        return new AddressView(a.getId(), PersonDto.of(me), a.getBody(), a.getCreatedAt());
    }

    List<RecordView> present(List<RemovalRecord> rows) {
        Set<UUID> ids = rows.stream().map(RemovalRecord::getId).collect(Collectors.toSet());
        List<Address> all = ids.isEmpty() ? List.of() : addresses.findByRecordIdInOrderByCreatedAtAsc(ids);
        Set<UUID> people = new HashSet<>();
        rows.forEach(r -> { people.add(r.getRemovedId()); people.add(r.getRemovedById()); });
        all.forEach(a -> people.add(a.getAuthorId()));
        Map<UUID, User> byId = users.findAllById(people).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, List<Address>> perRecord = all.stream().collect(Collectors.groupingBy(Address::getRecordId));
        Map<UUID, Appeal> appealOf = ids.isEmpty() ? Map.of() : appeals.findByRecordIdIn(ids).stream().collect(Collectors.toMap(Appeal::getRecordId, Function.identity()));
        List<RecordView> out = new ArrayList<>();
        for (RemovalRecord r : rows) {
            User removed = byId.get(r.getRemovedId()), by = byId.get(r.getRemovedById());
            if (removed == null || by == null) continue;
            List<AddressView> views = perRecord.getOrDefault(r.getId(), List.of()).stream().filter(a -> byId.containsKey(a.getAuthorId()))
                    .map(a -> new AddressView(a.getId(), PersonDto.of(byId.get(a.getAuthorId())), a.getBody(), a.getCreatedAt())).toList();
            out.add(new RecordView(r.getId(), r.getSpaceName(), PersonDto.of(removed), PersonDto.of(by), r.getReason(), r.isBadge(), r.getCreatedAt(), views,
                    Optional.ofNullable(appealOf.get(r.getId())).map(a -> a.isOpen() ? "OPEN" : a.getOutcome().name()).orElse(null)));
        }
        return out;
    }
}
