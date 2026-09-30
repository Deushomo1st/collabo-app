package com.collabo.backend.controller;

import com.collabo.backend.dto.AdminCredentialRequest;
import com.collabo.backend.service.CredentialService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Seeds credential entries until spaces and milestones record their own. Gated by AdminKeyFilter (X-Admin-Key). */
@RestController
@RequestMapping("/api/admin/credentials")
public class AdminCredentialController {

    private final CredentialService credentials;

    public AdminCredentialController(CredentialService credentials) {
        this.credentials = credentials;
    }

    /** 201 when a new entry was made, 200 when the same event was already recorded. */
    @PostMapping
    public ResponseEntity<Map<String, Boolean>> seed(@RequestBody AdminCredentialRequest r) {
        boolean created = credentials.recordFor(r.username(), r.kind(), r.title(), r.detail(), r.sourceType(), r.sourceId(), r.occurredAt());
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(Map.of("created", created));
    }
}
