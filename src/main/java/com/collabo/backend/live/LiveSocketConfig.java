package com.collabo.backend.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Mounts the socket at /ws/live. SecurityConfig already makes every non-public path need a session, so the handshake is refused
 * without one. Browsers send cookies on socket handshakes from any site, so the Origin header is what stops another site from using a
 * visitor's login: by default only this site's own origin is accepted. Extra origins can be listed in app.ws.allowed-origins.
 */
@Configuration
@EnableWebSocket
public class LiveSocketConfig implements WebSocketConfigurer {

    private final LiveSocketHandler handler;
    private final String[] extraOrigins;

    private final AdminSocket admin;

    public LiveSocketConfig(LiveSocketHandler handler, AdminSocket admin, @Value("${app.ws.allowed-origins:}") String[] extraOrigins) {
        this.handler = handler; this.admin = admin; this.extraOrigins = extraOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var reg = registry.addHandler(handler, "/ws/live");
        // the admin console: no session, so a one-time ticket (see AdminSocket) is its credential; same-origin rule applies too
        var adminReg = registry.addHandler(admin, "/ws/admin").addInterceptors(admin);
        if (extraOrigins.length > 0 && !extraOrigins[0].isBlank()) { reg.setAllowedOrigins(extraOrigins); adminReg.setAllowedOrigins(extraOrigins); }   // unset means same-origin only
    }
}
