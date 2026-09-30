package com.collabo.backend.config;

import com.collabo.backend.util.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Token-bucket throttling for the whole /api surface, one bucket per caller per tier. A tier's number is its burst and, spread over
 * a minute (an hour for reports), its sustained rate. Signed-in callers are counted by account, everyone else by client IP.
 *
 * AUTH   sign-in, registration and code endpoints: by IP (credential stuffing, account spam)
 * ADMIN  /api/admin/**: by IP, which also caps guesses at the admin key
 * REPORT reporting a Yarnspace: a few per hour, so reports cannot be used as a weapon
 * WRITE  any other change
 * READ   everything else
 * The failed-login lockout in LoginRateLimiter is separate and stays: it is per account, this is per caller.
 * Registered only inside the Spring Security chain (see SecurityConfig), after the session is loaded, so the account is known.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern REPORT = Pattern.compile("^/api/yarns/threads/[^/]+/report$");

    private final boolean enabled;
    private final TokenBucketLimiter auth, admin, report, write, read;

    public RateLimitFilter(@Value("${app.throttle.enabled:true}") boolean enabled,
                           @Value("${app.throttle.auth-per-minute:10}") int authPerMinute,
                           @Value("${app.throttle.admin-per-minute:60}") int adminPerMinute,
                           @Value("${app.throttle.report-per-hour:5}") int reportPerHour,
                           @Value("${app.throttle.write-per-minute:60}") int writePerMinute,
                           @Value("${app.throttle.read-per-minute:240}") int readPerMinute) {
        this.enabled = enabled;
        this.auth = perMinute(authPerMinute);
        this.admin = perMinute(adminPerMinute);
        this.report = new TokenBucketLimiter(reportPerHour, reportPerHour / 3600d);
        this.write = perMinute(writePerMinute);
        this.read = perMinute(readPerMinute);
    }

    private static TokenBucketLimiter perMinute(int n) { return new TokenBucketLimiter(n, n / 60d); }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !(request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/ws/")) || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String ip = "ip:" + ClientIpResolver.resolve(request);
        String path = request.getRequestURI(), method = request.getMethod();
        boolean change = !HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method);

        long wait;
        if (path.startsWith("/api/admin/")) wait = admin.tryAcquire(ip);
        else if (change && isAuthEndpoint(path)) wait = auth.tryAcquire(ip);
        else if (change && REPORT.matcher(path).matches()) wait = report.tryAcquire(caller(ip));
        else wait = (change ? write : read).tryAcquire(caller(ip));

        if (wait > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(wait));
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"You are going too fast. Try again in " + wait + (wait == 1 ? " second" : " seconds") + ".\",\"retryAfterSeconds\":" + wait + "}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAuthEndpoint(String p) {
        return p.equals("/api/auth/login") || p.equals("/api/moderator/login") || p.equals("/api/users") || p.equals("/api/users/verify") || p.equals("/api/users/resend-otp");
    }

    /** The signed-in account (user or moderator), else the client IP. */
    private static String caller(String ip) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken) ? "u:" + a.getName() : ip;
    }
}
