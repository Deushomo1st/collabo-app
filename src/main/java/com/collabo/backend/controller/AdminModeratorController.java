package com.collabo.backend.controller;

import com.collabo.backend.dto.ModeratorDtos.*;
import com.collabo.backend.service.ModeratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The admin's moderator accounts. Gated by AdminKeyFilter (X-Admin-Key), like the rest of /api/admin. */
@RestController
@RequestMapping("/api/admin/moderators")
public class AdminModeratorController {

    private final ModeratorService moderators;

    public AdminModeratorController(ModeratorService moderators) { this.moderators = moderators; }

    @GetMapping
    public List<ModeratorView> list() { return moderators.list(); }

    @PostMapping
    public ResponseEntity<ModeratorView> create(@RequestBody CreateRequest req) { return ResponseEntity.status(HttpStatus.CREATED).body(moderators.create(req)); }

    @PatchMapping("/{id}/active")
    public ModeratorView setActive(@PathVariable UUID id, @RequestBody ActiveRequest req) { return moderators.setActive(id, req.active()); }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id, @RequestBody PasswordRequest req) {
        moderators.resetPassword(id, req.password());
        return ResponseEntity.noContent().build();
    }
}
