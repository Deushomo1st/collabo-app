package com.collabo.backend.service;

import com.collabo.backend.dto.TableInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Allowlist-only database table introspection/truncation for the admin panel.
 *
 * The allowlist map is THE extension point: key = public API name (lowercase),
 * value = real Postgres table name. User input never reaches SQL — only values
 * from this map do — which kills SQL injection by construction and keeps
 * dangerous targets (flyway/hibernate metadata) out of reach.
 */
@Service
public class AdminDatabaseService {

    // Register future entities here, e.g. "orders" -> "orders"
    private static final Map<String, String> ALLOWED_TABLES = Map.of(
            "users", "users"
    );

    private final JdbcTemplate jdbcTemplate;

    public AdminDatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isAllowed(String tableKey) {
        return ALLOWED_TABLES.containsKey(tableKey);
    }

    public List<TableInfo> listTables() {
        return ALLOWED_TABLES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Long count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + entry.getValue(), Long.class);
                    return new TableInfo(entry.getKey(), count == null ? 0L : count);
                })
                .toList();
    }

    /**
     * TRUNCATE is transactional in Postgres and safe. RESTART IDENTITY resets
     * sequences (no-op for UUID PKs but the right habit); CASCADE keeps this
     * working once future entities FK-reference the allowlisted tables.
     * Callers MUST check isAllowed(tableKey) first — this throws otherwise.
     */
    public void truncate(String tableKey) {
        String table = ALLOWED_TABLES.get(tableKey);
        if (table == null) {
            throw new IllegalArgumentException("table not allowed: " + tableKey);
        }
        jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE");
    }
}
