package com.collabo.backend.service;

import com.collabo.backend.dto.AdminStatus;
import com.collabo.backend.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** What the admin console's status strip shows: is the server healthy, is it configured for production, and how big is it. */
@Service
public class AdminStatusService {

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final ModeratorRepository moderators;
    private final SpaceRepository spaces;
    private final PostRepository posts;
    private final boolean mailConfigured, secureCookies, testAccounts;

    public AdminStatusService(JdbcTemplate jdbc, UserRepository users, ModeratorRepository moderators, SpaceRepository spaces, PostRepository posts,
                              @Value("${RESEND_API_KEY:}") String mailKey,
                              @Value("${app.session.cookie-secure:false}") boolean secureCookies,
                              @Value("${app.test-accounts.enabled:false}") boolean testAccounts) {
        this.jdbc = jdbc; this.users = users; this.moderators = moderators; this.spaces = spaces; this.posts = posts;
        this.mailConfigured = mailKey != null && !mailKey.isBlank();
        this.secureCookies = secureCookies; this.testAccounts = testAccounts;
    }

    public AdminStatus status() {
        String database;
        boolean ok;
        try {
            database = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion());
            ok = true;
        } catch (RuntimeException e) { database = "unreachable"; ok = false; }
        Map<String, Long> counts = new LinkedHashMap<>();
        if (ok) {
            counts.put("users", users.count());
            counts.put("verified", users.countByVerifiedTrue());
            counts.put("premium", users.countByPremiumTrue());
            counts.put("moderators", moderators.count());
            counts.put("spaces", spaces.count());
            counts.put("posts", posts.count());
        }
        return new AdminStatus(Instant.now(), ManagementFactory.getRuntimeMXBean().getUptime() / 1000, database, ok, mailConfigured, secureCookies, testAccounts, counts);
    }
}
