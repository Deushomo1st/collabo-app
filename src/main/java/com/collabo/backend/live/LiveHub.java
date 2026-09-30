package com.collabo.backend.live;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Who is connected right now. Signals are addressed to accounts ("u:<userId>", "m:<moderatorId>"), never to topics the browser picks, so the
 * server alone decides who hears what. One account may hold several sockets (tabs, devices), capped so a script cannot hoard them.
 *
 * ponytail: in-memory, for one server. Put a pub/sub (Redis, or Postgres LISTEN/NOTIFY) between publishers and this if the app ever runs on several.
 */
@Component
public class LiveHub {

    static final int MAX_PER_ACCOUNT = 8;
    static final long IDLE_MILLIS = 75_000;   // clients ping every 25s; three missed pings and the socket is dead

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>> byAccount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastHeard = new ConcurrentHashMap<>();   // session id -> millis

    public static String userKey(UUID id) { return "u:" + id; }
    public static String moderatorKey(UUID id) { return "m:" + id; }

    void add(String account, WebSocketSession session) {
        CopyOnWriteArrayList<WebSocketSession> mine = byAccount.computeIfAbsent(account, k -> new CopyOnWriteArrayList<>());
        mine.add(session);
        heard(session);
        while (mine.size() > MAX_PER_ACCOUNT) close(mine.remove(0), CloseStatus.POLICY_VIOLATION.withReason("Too many connections"));
    }

    void remove(String account, WebSocketSession session) {
        lastHeard.remove(session.getId());
        List<WebSocketSession> mine = byAccount.get(account);
        if (mine != null) mine.removeIf(s -> s.getId().equals(session.getId()));
    }

    void heard(WebSocketSession session) { lastHeard.put(session.getId(), System.currentTimeMillis()); }

    public int connections(String account) { List<WebSocketSession> s = byAccount.get(account); return s == null ? 0 : s.size(); }

    /** Sends one signal to every open socket of each account. A slow or broken socket is dropped, never waited on. */
    public void send(Collection<String> accounts, String json) {
        TextMessage message = new TextMessage(json);
        for (String account : accounts) {
            List<WebSocketSession> mine = byAccount.get(account);
            if (mine == null) continue;
            for (WebSocketSession s : mine) {
                try { if (s.isOpen()) s.sendMessage(message); }
                catch (Exception e) { close(s, CloseStatus.SESSION_NOT_RELIABLE); }
            }
        }
    }

    /** Every signed-in user's sockets (not moderators, not the admin console). For id-only signals that concern everyone. */
    public void broadcastUsers(String json) { send(byAccount.keySet().stream().filter(k -> k.startsWith("u:")).toList(), json); }

    /** A client that stopped pinging is gone; free its slot. */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    void sweepIdle() {
        long cutoff = System.currentTimeMillis() - IDLE_MILLIS;
        byAccount.values().forEach(list -> list.stream().filter(s -> lastHeard.getOrDefault(s.getId(), 0L) < cutoff).forEach(s -> close(s, CloseStatus.GOING_AWAY)));
    }

    private static void close(WebSocketSession s, CloseStatus why) {
        try { s.close(why); } catch (Exception ignored) { /* already gone */ }
    }
}
