package com.collabo.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Gates all /api/admin/** endpoints behind a shared-secret header (X-Admin-Key).
 *
 * NOT annotated with @Component — it is declared as a @Bean in SecurityConfig,
 * registered in the Spring Security chain via addFilterBefore(...), and its
 * servlet-container registration is explicitly disabled through a
 * FilterRegistrationBean with setEnabled(false) to avoid double registration.
 *
 * Fails closed: a blank configured key rejects everything with 503.
 */
public class AdminKeyFilter extends OncePerRequestFilter {

    public static final String ADMIN_KEY_HEADER = "X-Admin-Key";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    private final String adminKey;

    public AdminKeyFilter(String adminKey) {
        this.adminKey = adminKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (adminKey == null || adminKey.isBlank()) {
            writeJsonError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "admin key not configured");
            return;
        }
        String candidate = request.getHeader(ADMIN_KEY_HEADER);
        // Constant-time comparison to avoid a timing oracle on the shared secret.
        if (candidate == null || !MessageDigest.isEqual(
                adminKey.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8))) {
            writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "invalid admin key");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
