package com.collabo.backend.live;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The admin console signs in with a key, not a session, so it cannot use /ws/live. Instead it trades the key (sent as a header, to
 * POST /api/admin/live/ticket) for a one-time ticket good for 30 seconds, and opens /ws/admin?ticket=... The key itself never goes in a URL,
 * and a ticket that leaks into a log is already spent. The socket only ever carries the "queue" signal and pings.
 */
@Component
public class AdminSocket extends TextWebSocketHandler implements HandshakeInterceptor {

    public static final String ACCOUNT = "a:admin";
    static final Duration TTL = Duration.ofSeconds(30);
    private static final int MAX_TICKETS = 200;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LiveHub hub;
    private final Map<String, Long> tickets = new ConcurrentHashMap<>();   // ticket -> expiry millis

    public AdminSocket(LiveHub hub) { this.hub = hub; }

    /** Called only behind the admin key. */
    public String issue() {
        long now = System.currentTimeMillis();
        tickets.values().removeIf(exp -> exp < now);
        if (tickets.size() >= MAX_TICKETS) tickets.clear();   // a flood is an attack or a bug; nobody legitimate needs 200 unspent tickets
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tickets.put(ticket, now + TTL.toMillis());
        return ticket;
    }

    private boolean redeem(String ticket) {
        Long exp = ticket == null ? null : tickets.remove(ticket);   // remove: a ticket works once
        return exp != null && exp >= System.currentTimeMillis();
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler h, Map<String, Object> attrs) {
        String ticket = UriComponentsBuilder.fromUri(req.getURI()).build().getQueryParams().getFirst("ticket");
        return redeem(ticket);
    }

    @Override public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler h, Exception ex) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        hub.add(ACCOUNT, new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws java.io.IOException {
        if (message.getPayload().length() > 64) return;
        hub.heard(session);
        if (message.getPayload().contains("\"ping\"")) session.sendMessage(new TextMessage("{\"t\":\"pong\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { hub.remove(ACCOUNT, session); }
}
