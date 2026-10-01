package com.collabo.backend.service;

import com.collabo.backend.dto.YarnDtos.YarnView;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.SpaceRepository;
import com.collabo.backend.repository.UserRepository;
import com.collabo.backend.repository.YarnRepository;
import com.collabo.backend.repository.YarnThreadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pinned yarns in a Workspace room. The team reads them; the owner and anyone holding POST_IN_ROOM pin and unpin. */
@Service
@Transactional
public class PinService {

    private static final String GONE = "No such space.";

    private final SpaceRepository spaces;
    private final YarnRepository yarns;
    private final YarnThreadRepository threads;
    private final UserRepository users;
    private final SpaceService spaceAccess;
    private final SpaceThreadService spaceThreads;

    public PinService(SpaceRepository spaces, YarnRepository yarns, YarnThreadRepository threads, UserRepository users,
                      SpaceService spaceAccess, SpaceThreadService spaceThreads) {
        this.spaces = spaces; this.yarns = yarns; this.threads = threads; this.users = users; this.spaceAccess = spaceAccess; this.spaceThreads = spaceThreads;
    }

    @Transactional(readOnly = true)
    public List<YarnView> list(User me, UUID spaceId) {
        Space s = team(me, spaceId);
        UUID thread = spaceThreads.workspaceId(s.getPostId());
        if (thread == null) return List.of();
        List<Yarn> rows = yarns.findByThreadIdAndPinnedTrueOrderByCreatedAtAsc(thread);
        Map<UUID, User> people = users.findAllById(rows.stream().map(Yarn::getSenderId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        return rows.stream().map(y -> view(y, people)).toList();
    }

    public void set(User me, UUID spaceId, UUID yarnId, boolean pinned) {
        Space s = team(me, spaceId);
        if (!spaceAccess.can(me, s, SpacePermission.POST_IN_ROOM)) throw new ForbiddenException("You do not have permission to pin in this room.");
        UUID thread = spaceThreads.workspaceId(s.getPostId());
        Yarn y = yarns.findById(yarnId).filter(x -> x.getThreadId().equals(thread) && x.getKind() == Yarn.Kind.USER)
                .orElseThrow(() -> new ResourceNotFoundException("No such yarn."));
        if (y.isPinned() == pinned) return;
        y.setPinned(pinned);
        spaceThreads.announce(s, me.getUsername() + (pinned ? " pinned a yarn." : " unpinned a yarn."));
    }

    private Space team(User me, UUID spaceId) {
        Space s = spaces.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException(GONE));
        String role = spaceAccess.roleOf(me, s);
        if (!"OWNER".equals(role) && !"MEMBER".equals(role)) throw new ResourceNotFoundException(GONE);
        return s;
    }

    private static YarnView view(Yarn y, Map<UUID, User> people) {
        User u = y.getSenderId() == null ? null : people.get(y.getSenderId());
        return new YarnView(y.getId(), y.getSenderId(), u == null ? "System" : u.getUsername(), y.getKind().name(), y.getBody(), y.getCreatedAt(), null, y.isPinned());
    }
}
