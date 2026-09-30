package com.collabo.backend.service;

import com.collabo.backend.dto.TableInfo;
import com.collabo.backend.dto.TablePage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The admin panel's view of the database: which tables exist, their rows (read-only, sensitive columns masked) and cleaning.
 *
 * Table names come from the database's own catalogue and only an exact match of one of them ever reaches SQL, so user input
 * cannot inject anything. Session internals are left out. Only {@link #CLEANABLE} tables can be wiped.
 */
@Service
public class AdminDatabaseService {

    /** Tables the panel may wipe. Everything else is browse-only. */
    private static final Set<String> CLEANABLE = Set.of("users");
    private static final Pattern HIDDEN = Pattern.compile("^(spring_session.*|flyway.*)$");
    private static final Pattern SECRET_COLUMN = Pattern.compile("(?i).*(password|hash|otp|token|secret|session|salt).*");
    static final String MASK = "••••";
    static final int MAX_LIMIT = 200, CELL = 160;

    private final JdbcTemplate jdbc;

    public AdminDatabaseService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** lower-case key -> the catalogue's own spelling (upper-case on H2, lower-case on Postgres). */
    private Map<String, String> tables() {
        return jdbc.execute((Connection c) -> {
            Map<String, String> found = new TreeMap<>();
            try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), c.getSchema(), "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (!HIDDEN.matcher(name.toLowerCase()).matches()) found.put(name.toLowerCase(), name);
                }
            } catch (SQLException e) { throw new IllegalStateException(e); }
            return found;
        });
    }

    public boolean isAllowed(String tableKey) { return tables().containsKey(tableKey); }

    public boolean isCleanable(String tableKey) { return CLEANABLE.contains(tableKey) && isAllowed(tableKey); }

    public List<TableInfo> listTables() {
        return tables().entrySet().stream().map(e -> {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + quote(e.getValue()), Long.class);
            return new TableInfo(e.getKey(), count == null ? 0L : count, CLEANABLE.contains(e.getKey()));
        }).toList();
    }

    /** A page of rows, oldest key first. Secret columns are masked and long or binary cells are shortened. Callers check isAllowed first. */
    public TablePage rows(String tableKey, int limit, int offset) {
        String table = require(tableKey);
        int lim = Math.max(1, Math.min(limit, MAX_LIMIT)), off = Math.max(0, offset);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + quote(table), Long.class);
        return jdbc.query("SELECT * FROM " + quote(table) + " ORDER BY 1 LIMIT " + lim + " OFFSET " + off, (ResultSet rs) -> {
            ResultSetMetaData md = rs.getMetaData();
            List<String> cols = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnName(i).toLowerCase());
            List<List<String>> rows = new ArrayList<>();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= cols.size(); i++) row.add(cell(cols.get(i - 1), rs.getObject(i)));
                rows.add(row);
            }
            return new TablePage(cols, rows, total == null ? 0L : total);
        });
    }

    /** Only for cleanable tables; callers check isCleanable first. CASCADE keeps it working as other tables reference these. */
    public void truncate(String tableKey) {
        if (!isCleanable(tableKey)) throw new IllegalArgumentException("table not cleanable: " + tableKey);
        jdbc.execute("TRUNCATE TABLE " + quote(require(tableKey)) + " RESTART IDENTITY CASCADE");
    }

    private String require(String tableKey) {
        String name = tables().get(tableKey);
        if (name == null) throw new IllegalArgumentException("table not allowed: " + tableKey);
        return name;
    }

    private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }

    private static String cell(String column, Object v) {
        if (v == null) return null;
        if (SECRET_COLUMN.matcher(column).matches()) return MASK;
        if (v instanceof byte[] b) return "(" + b.length + " bytes)";
        String s = v.toString();
        return s.length() > CELL ? s.substring(0, CELL) + "…" : s;
    }
}
