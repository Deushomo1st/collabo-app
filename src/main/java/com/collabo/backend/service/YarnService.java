package com.collabo.backend.service;

import com.collabo.backend.entity.ThreadMember;
import com.collabo.backend.entity.UserBlock;
import com.collabo.backend.entity.Yarn;
import com.collabo.backend.entity.YarnThread;
import com.collabo.backend.exception.YarnException;
import com.collabo.backend.repository.ThreadMemberRepository;
import com.collabo.backend.repository.UserBlockRepository;
import com.collabo.backend.repository.YarnRepository;
import com.collabo.backend.repository.YarnThreadRepository;

import com.collabo.backend.dto.YarnDtos.*;
import com.collabo.backend.entity.User;
import com.collabo.backend.entity.YarnThread.Status;
import com.collabo.backend.entity.YarnThread.Tier;
import com.collabo.backend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Threads, yarns, per-person archive/pin/mute, yarn requests and blocking. Rules are in docs: "Yarnspaces backend plan". */
@Service
@Transactional
public class YarnService {

    private static final String CANT_DELIVER = "This yarn can't be delivered.";

    private final YarnThreadRepository threads;
    private final ThreadMemberRepository members;
    private final YarnRepository yarns;
    private final UserBlockRepository blocks;
    private final UserRepository users;
    private final FollowService follows;

    public YarnService(YarnThreadRepository threads, ThreadMemberRepository members, YarnRepository yarns,
                       UserBlockRepository blocks, UserRepository users, FollowService follows) {
        this.follows = follows;
        this.threads = threads; this.members = members; this.yarns = yarns; this.blocks = blocks; this.users = users;
    }

    // ---- people -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PersonView> directory(User me, String q) {
        String s = q == null ? "" : q.trim();
        if (s.length() < 2) return List.of();
        return users.findTop10ByUsernameContainingIgnoreCaseAndIdNot(s, me.getId()).stream()
                .filter(u -> !blocks.existsByBlockerIdAndBlockedId(u.getId(), me.getId()))
                .map(u -> new PersonView(u.getId(), u.getUsername())).toList();
    }

    // ---- listing ----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ThreadView> list(User me, boolean archived, Tier tier, String q) {
        List<ThreadMember> mine = members.findByUserId(me.getId()).stream().filter(m -> m.isArchived() == archived).toList();
        if (mine.isEmpty()) return List.of();
        Set<UUID> ids = mine.stream().map(ThreadMember::getThreadId).collect(Collectors.toSet());
        Map<UUID, YarnThread> byId = threads.findAllById(ids).stream().collect(Collectors.toMap(YarnThread::getId, Function.identity()));
        Map<UUID, List<ThreadMember>> seats = members.findByThreadIdIn(ids).stream().collect(Collectors.groupingBy(ThreadMember::getThreadId));
        Map<UUID, User> people = peopleFor(seats.values().stream().flatMap(List::stream).map(ThreadMember::getUserId).collect(Collectors.toSet()));
        Set<UUID> iBlocked = blocks.findByBlockerIdOrderByCreatedAtDesc(me.getId()).stream().map(UserBlock::getBlockedId).collect(Collectors.toSet());
        String needle = q == null ? "" : q.trim().toLowerCase();

        List<ThreadView> out = new ArrayList<>();
        for (ThreadMember seat : mine) {
            YarnThread t = byId.get(seat.getThreadId());
            if (t == null || (tier != null && t.getTier() != tier)) continue;
            UUID other = t.getTier() == Tier.MYSPACE ? otherOf(seats.get(t.getId()), me.getId()) : null;
            if (other != null && iBlocked.contains(other)) continue;   // lives in the Blocked list instead
            ThreadView v = view(t, seat, me, other, seats.get(t.getId()), people);
            if (!needle.isEmpty() && !matches(v, needle)) continue;
            out.add(v);
        }
        // ponytail: one unread count per thread; batch into a grouped query if inboxes get large
        out.sort(Comparator.comparing(ThreadView::pinned).reversed().thenComparing(ThreadView::lastAt, Comparator.reverseOrder()));
        return out;
    }

    // ---- creating ---------------------------------------------------------

    public ThreadView startMySpace(User me, String username, String body) {
        User target = users.findByUsername(username.trim()).orElseThrow(() -> new YarnException(HttpStatus.NOT_FOUND, "No one with that username."));
        if (target.getId().equals(me.getId())) throw new YarnException(HttpStatus.BAD_REQUEST, "You can't yarn yourself.");
        requireNotBlocked(me.getId(), target.getId());
        String key = dmKey(me.getId(), target.getId());
        YarnThread t = threads.findByDmKey(key).orElseGet(() -> {
            YarnThread fresh = new YarnThread();
            fresh.setTier(Tier.MYSPACE); fresh.setStatus(Status.PENDING);
            fresh.setCreatedBy(me.getId()); fresh.setDmKey(key);
            threads.save(fresh);
            members.save(seat(fresh, me.getId(), ThreadMember.Role.OWNER));
            members.save(seat(fresh, target.getId(), ThreadMember.Role.MEMBER));
            return fresh;
        });
        send(me, t.getId(), body);
        return viewFor(me, t.getId());
    }

    // ---- yarns ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<YarnView> history(User me, UUID threadId, Instant before, int limit) {
        seat(threadId, me.getId());
        Map<UUID, User> people = peopleFor(members.findByThreadId(threadId).stream().map(ThreadMember::getUserId).collect(Collectors.toSet()));
        Instant cursor = before == null ? Instant.now().plusSeconds(86_400) : before;
        return yarns.findByThreadIdAndCreatedAtBeforeOrderByCreatedAtDesc(threadId, cursor, PageRequest.of(0, Math.max(1, Math.min(limit, 100))))
                .stream().map(y -> yarnView(y, people)).toList();
    }

    public YarnView send(User me, UUID threadId, String rawBody) {
        ThreadMember mine = seat(threadId, me.getId());
        YarnThread t = threads.findById(threadId).orElseThrow();
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()) throw new YarnException(HttpStatus.BAD_REQUEST, "Write a yarn first.");
        if (t.getTier() == Tier.MYSPACE) {
            requireNotBlocked(me.getId(), otherOf(members.findByThreadId(threadId), me.getId()));
            if (t.getStatus() == Status.DECLINED) throw new YarnException(HttpStatus.FORBIDDEN, CANT_DELIVER);
            if (t.getStatus() == Status.PENDING) {
                if (!t.getCreatedBy().equals(me.getId())) throw new YarnException(HttpStatus.FORBIDDEN, "Accept the yarn request first.");
                if (yarns.countByThreadId(threadId) >= 1) throw new YarnException(HttpStatus.CONFLICT, "Wait for them to accept your request before sending more.");
            }
        }
        Yarn y = addYarn(t, me.getId(), Yarn.Kind.USER, body);
        mine.setLastReadAt(y.getCreatedAt());
        // a fresh yarn brings an archived thread back for everyone else, unless they muted it
        members.findByThreadId(threadId).stream()
                .filter(m -> !m.getUserId().equals(me.getId()) && m.isArchived() && !m.isMuted() && t.getStatus() != Status.DECLINED)
                .forEach(m -> m.setArchivedAt(null));
        return yarnView(y, peopleFor(Set.of(me.getId())));
    }

    public void markRead(User me, UUID threadId) {
        seat(threadId, me.getId()).setLastReadAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    // ---- per-person state -------------------------------------------------

    public ThreadView setPrefs(User me, UUID threadId, Prefs p) {
        ThreadMember m = seat(threadId, me.getId());
        if (p.archived() != null) m.setArchivedAt(p.archived() ? Instant.now() : null);
        if (p.pinned() != null) m.setPinned(p.pinned());
        if (p.muted() != null) m.setMuted(p.muted());
        return viewFor(me, threadId);
    }

    public ThreadView respond(User me, UUID threadId, boolean accept) {
        ThreadMember m = seat(threadId, me.getId());
        YarnThread t = threads.findById(threadId).orElseThrow();
        if (t.getTier() != Tier.MYSPACE || t.getStatus() != Status.PENDING || t.getCreatedBy().equals(me.getId())) {
            throw new YarnException(HttpStatus.CONFLICT, "There is no request to answer.");
        }
        if (accept) {
            t.setStatus(Status.ACCEPTED);
            addYarn(t, null, Yarn.Kind.SYSTEM, me.getUsername() + " accepted the yarn request.");
        } else {
            t.setStatus(Status.DECLINED);
            m.setArchivedAt(Instant.now());
        }
        return viewFor(me, threadId);
    }

    // ---- blocking ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BlockView> blocked(User me) {
        List<UserBlock> rows = blocks.findByBlockerIdOrderByCreatedAtDesc(me.getId());
        Map<UUID, User> people = peopleFor(rows.stream().map(UserBlock::getBlockedId).collect(Collectors.toSet()));
        return rows.stream().filter(b -> people.containsKey(b.getBlockedId()))
                .map(b -> new BlockView(b.getBlockedId(), people.get(b.getBlockedId()).getUsername(), b.getCreatedAt())).toList();
    }

    public void block(User me, UUID userId) {
        if (userId.equals(me.getId())) throw new YarnException(HttpStatus.BAD_REQUEST, "You can't block yourself.");
        if (!users.existsById(userId)) throw new YarnException(HttpStatus.NOT_FOUND, "No such person.");
        if (!blocks.existsByBlockerIdAndBlockedId(me.getId(), userId)) blocks.save(new UserBlock(me.getId(), userId));
        follows.endBetween(me.getId(), userId);   // a block ends any following, both ways
    }

    public void unblock(User me, UUID userId) {
        blocks.deleteByBlockerIdAndBlockedId(me.getId(), userId);
    }

    // ---- helpers ----------------------------------------------------------

    private void requireNotBlocked(UUID a, UUID b) {
        if (blocks.existsByBlockerIdAndBlockedId(a, b) || blocks.existsByBlockerIdAndBlockedId(b, a)) {
            throw new YarnException(HttpStatus.FORBIDDEN, CANT_DELIVER);   // same words either way, so a block isn't revealed
        }
    }

    private ThreadMember seat(UUID threadId, UUID userId) {
        return members.findByThreadIdAndUserId(threadId, userId)
                .orElseThrow(() -> new YarnException(HttpStatus.NOT_FOUND, "No such yarn thread."));   // not-a-member looks like not-found
    }

    private static ThreadMember seat(YarnThread t, UUID userId, ThreadMember.Role role) {
        ThreadMember m = new ThreadMember(t.getId(), userId, role);
        if (role == ThreadMember.Role.OWNER) m.setLastReadAt(Instant.now());
        return m;
    }

    private Yarn addYarn(YarnThread t, UUID sender, Yarn.Kind kind, String body) {
        Yarn y = yarns.save(new Yarn(t.getId(), sender, kind, body));
        t.recordYarn(sender, body, y.getCreatedAt());
        return y;
    }

    private static String dmKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private static UUID otherOf(List<ThreadMember> seats, UUID me) {
        return seats.stream().map(ThreadMember::getUserId).filter(id -> !id.equals(me)).findFirst().orElse(null);
    }

    private Map<UUID, User> peopleFor(Collection<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private ThreadView viewFor(User me, UUID threadId) {
        YarnThread t = threads.findById(threadId).orElseThrow();
        List<ThreadMember> seats = members.findByThreadId(threadId);
        ThreadMember mine = seats.stream().filter(m -> m.getUserId().equals(me.getId())).findFirst().orElseThrow();
        UUID other = t.getTier() == Tier.MYSPACE ? otherOf(seats, me.getId()) : null;
        return view(t, mine, me, other, seats, peopleFor(seats.stream().map(ThreadMember::getUserId).collect(Collectors.toSet())));
    }

    private ThreadView view(YarnThread t, ThreadMember mine, User me, UUID other, List<ThreadMember> seats, Map<UUID, User> people) {
        List<PersonView> who = seats.stream().map(s -> people.get(s.getUserId())).filter(Objects::nonNull)
                .map(u -> new PersonView(u.getId(), u.getUsername())).toList();
        boolean requester = t.getCreatedBy().equals(me.getId());
        User last = t.getLastSenderId() == null ? null : people.get(t.getLastSenderId());
        String name = other != null && people.containsKey(other) ? people.get(other).getUsername() : t.getName();
        return new ThreadView(t.getId(), t.getTier().name(), name, t.getStatus().name(),
                requester, t.getStatus() == Status.PENDING && !requester && t.getTier() == Tier.MYSPACE,
                t.getLastBody(), last == null ? "System" : last.getUsername(), t.getLastYarnAt(),
                yarns.countUnread(t.getId(), mine.getLastReadAt(), me.getId()),
                mine.isPinned(), mine.isMuted(), mine.isArchived(), other, who);
    }

    private static boolean matches(ThreadView v, String needle) {
        return (v.name() != null && v.name().toLowerCase().contains(needle))
                || (v.lastBody() != null && v.lastBody().toLowerCase().contains(needle))
                || v.members().stream().anyMatch(p -> p.username().toLowerCase().contains(needle));
    }

    private static YarnView yarnView(Yarn y, Map<UUID, User> people) {
        User s = y.getSenderId() == null ? null : people.get(y.getSenderId());
        return new YarnView(y.getId(), y.getSenderId(), s == null ? "System" : s.getUsername(), y.getKind().name(), y.getBody(), y.getCreatedAt());
    }
}
