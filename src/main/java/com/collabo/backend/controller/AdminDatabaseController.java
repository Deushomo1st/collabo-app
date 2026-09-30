package com.collabo.backend.controller;

import com.collabo.backend.dto.TableInfo;
import com.collabo.backend.service.AdminDatabaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin database introspection/maintenance. Access gated by AdminKeyFilter.
 */
@RestController
@RequestMapping("/api/admin/db/tables")
public class AdminDatabaseController {

    private final AdminDatabaseService adminDatabaseService;

    public AdminDatabaseController(AdminDatabaseService adminDatabaseService) {
        this.adminDatabaseService = adminDatabaseService;
    }

    @GetMapping
    public List<TableInfo> listTables() {
        return adminDatabaseService.listTables();
    }

    /** Read-only page of rows; secret columns come back masked. */
    @GetMapping("/{tableKey}/rows")
    public ResponseEntity<?> rows(@PathVariable String tableKey, @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
        if (!adminDatabaseService.isAllowed(tableKey)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "no such table: " + tableKey));
        }
        return ResponseEntity.ok(adminDatabaseService.rows(tableKey, limit, offset));
    }

    @PostMapping("/{tableKey}/truncate")
    public ResponseEntity<?> truncate(@PathVariable String tableKey) {
        if (!adminDatabaseService.isCleanable(tableKey)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "table not allowed: " + tableKey));
        }
        adminDatabaseService.truncate(tableKey);
        return ResponseEntity.noContent().build(); // 204
    }
}
