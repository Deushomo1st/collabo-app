package com.collabo.backend.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP, honouring the reverse-proxy headers Caddy sets.
 * Without this, every request behind Caddy looks like it comes from localhost
 * and the rate limiter would treat everyone as one IP.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // X-Forwarded-For is a comma-separated chain; the first hop is the client.
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
