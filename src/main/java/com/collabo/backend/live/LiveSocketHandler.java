package com.collabo.backend.live;

import com.collabo.backend.config.CurrentModerator;
import com.collabo.backend.config.TokenBucketLimiter;
import com.collabo.backend.entity.Moderator;
import com.collabo.backend.repository.ModeratorRepository;
import com.collabo.backend.repository.UserRepository;
import com.collabo.backend.service.YarnService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single socket each open page keeps to the server. It is signed in by the same session cookie as the rest of the site
 * (the handshake refuses anyone else, and the framework refuses other origins). Server to browser it carries change signals; browser to
 * server it carries only tiny frames: ping, and "delivered" acknowledgements. Everything else goes through the ordinary API.
 *
 * Frames are {"t":"ping"} and {"t":"delivered","thread":"<id>","yarn":"<id>"}; they are read with a pattern, not a JSON tree,
 * because they are this small and fixed. Anything longer than MAX_FRAME or over the per-socket rate is dropped.
 */
@Component
public class LiveSocketHandler extends TextWebSocketHandler {

    static final int MAX_FRAME = 512;
    private static final Pattern TYPE = Pattern.compile("\"t\"\\s*:\\s*\"(\\w+)\"");
    private static final Pattern THREAD = Pattern.compile("\"thread\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");
    private static final Pattern YARN = Pattern.compile("\"yarn\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");
    private static final String ACCOUNT = "account";

    private final LiveHub hub;
    private final UserRepository users;
    private final ModeratorRepository moderators;
    private final YarnService yarns;
    // a burst of 20 frames, then 5 a second, per socket
    private final TokenBucketLimiter frames = new TokenBucketLimiter(20, 5);

    public LiveSocketHandler(LiveHub hub, UserRepository users, ModeratorRepository moderators, YarnService yarns) {
        this.hub = hub; this.users = users; this.moderators = moderators; this.yarns = yarns;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String account = accountOf(session.getPrincipal());
        if (account == null) { session.close(CloseStatus.POLICY_VIOLATION.withReason("Sign in first")); return; }
        session.getAttributes().put(ACCOUNT, account);
        // the decorator makes concurrent sends from many request threads safe, and drops a client that cannot keep up
        hub.add(account, new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024));
        // A browser that was away missed the signals. Being here now is what delivery means, so catch up the senders' ticks.
        if (account.startsWith("u:")) yarns.deliverPending(UUID.fromString(account.substring(2)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String account = (String) session.getAttributes().get(ACCOUNT);
        String text = message.getPayload();
        if (account == null || text.length() > MAX_FRAME || frames.tryAcquire(session.getId()) > 0) return;
        hub.heard(session);
        Matcher type = TYPE.matcher(text);
        if (!type.find()) return;
        switch (type.group(1)) {
            case "ping" -> session.sendMessage(new TextMessage("{\"t\":\"pong\"}"));
            case "delivered" -> delivered(account, text);
            default -> { /* unknown frames are ignored */ }
        }
    }

    private void delivered(String account, String text) {
        if (!account.startsWith("u:")) return;   // a moderator reading never leaves a receipt
        Matcher thread = THREAD.matcher(text), yarn = YARN.matcher(text);
        if (!thread.find() || !yarn.find()) return;
        try { yarns.markDelivered(UUID.fromString(account.substring(2)), UUID.fromString(thread.group(1)), UUID.fromString(yarn.group(1))); }
        catch (RuntimeException ignored) { /* not your thread, or a bad id: nothing to do and nothing to tell */ }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        frames.forget(session.getId());
        Object account = session.getAttributes().get(ACCOUNT);
        if (account != null) hub.remove((String) account, session);
    }

    /** "u:<id>" for a signed-in user, "m:<id>" for an active moderator, null for anything else. */
    private String accountOf(Principal principal) {
        if (!(principal instanceof Authentication auth) || !auth.isAuthenticated()) return null;
        if (CurrentModerator.isModerator(auth)) {
            if (!auth.getName().startsWith(CurrentModerator.PREFIX)) return null;
            try {
                return moderators.findById(UUID.fromString(auth.getName().substring(CurrentModerator.PREFIX.length()))).filter(Moderator::isActive)
                        .map(m -> LiveHub.moderatorKey(m.getId())).orElse(null);
            } catch (IllegalArgumentException e) { return null; }
        }
        return users.findByUsername(auth.getName()).map(u -> LiveHub.userKey(u.getId())).orElse(null);
    }
}
