package com.collabo.backend.service;

import com.collabo.backend.dto.AppealDtos.HistoryLine;
import com.collabo.backend.dto.InvestigationDtos.InvestigationView;
import com.collabo.backend.dto.InvestigationDtos.ModeratorCase;
import com.collabo.backend.entity.*;
import com.collabo.backend.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** The moderator's side: their open cases, and the Yarnspace of each one, read-only. Nothing else of the platform. */
@Service
@Transactional(readOnly = true)
public class ModeratorWorkService {

    static final int MAX_YARNS = 500;

    private final InvestigationRepository investigations;
    private final InvestigationService views;
    private final FindingService findings;
    private final YarnRepository yarns;
    private final ThreadMemberRepository seats;
    private final UserRepository users;

    public ModeratorWorkService(InvestigationRepository investigations, InvestigationService views, FindingService findings,
                                YarnRepository yarns, ThreadMemberRepository seats, UserRepository users) {
        this.investigations = investigations; this.views = views; this.findings = findings; this.yarns = yarns; this.seats = seats; this.users = users;
    }

    public List<InvestigationView> mine(Moderator m) {
        return views.views(investigations.findByModeratorIdAndStatusInOrderByAssignedAtAsc(m.getId(), EnumSet.of(Investigation.Status.ASSIGNED, Investigation.Status.REPORTED)));
    }

    public ModeratorCase open(Moderator m, UUID id) {
        Investigation i = findings.owned(m, id);
        return new ModeratorCase(views.views(List.of(i)).get(0), memberNames(i), lines(i), findings.list(i.getId()));
    }

    private List<String> memberNames(Investigation i) {
        if (i.getThreadId() == null) return List.of();
        return users.findAllById(seats.findByThreadId(i.getThreadId()).stream().map(ThreadMember::getUserId).toList()).stream().map(User::getUsername).sorted().toList();
    }

    /** An appeal reads only its window; a report reads the latest 500 yarns of the thread. */
    private List<HistoryLine> lines(Investigation i) {
        if (i.getThreadId() == null) return List.of();
        List<Yarn> rows;
        if (i.getWindowFrom() != null && i.getWindowTo() != null) rows = yarns.findByThreadIdAndCreatedAtBetweenOrderByCreatedAtAsc(i.getThreadId(), i.getWindowFrom(), i.getWindowTo());
        else {
            rows = new ArrayList<>(yarns.findByThreadIdAndCreatedAtBeforeOrderByCreatedAtDesc(i.getThreadId(), Instant.now().plus(1, ChronoUnit.DAYS), PageRequest.of(0, MAX_YARNS)));
            Collections.reverse(rows);
        }
        Map<UUID, String> names = users.findAllById(rows.stream().map(Yarn::getSenderId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        return rows.stream().map(y -> new HistoryLine(y.getSenderId() == null ? null : names.get(y.getSenderId()), y.getKind() == Yarn.Kind.SYSTEM, y.getBody(), y.getCreatedAt())).toList();
    }
}
