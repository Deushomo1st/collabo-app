package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentModerator;
import com.collabo.backend.dto.InvestigationDtos.*;
import com.collabo.backend.service.FindingService;
import com.collabo.backend.service.ModeratorWorkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A moderator's cases: read the assigned Yarnspace, report findings with screenshots. Needs a moderator session. */
@RestController
@RequestMapping("/api/moderator/investigations")
public class ModeratorInvestigationController {

    private final ModeratorWorkService work;
    private final FindingService findings;
    private final CurrentModerator current;

    public ModeratorInvestigationController(ModeratorWorkService work, FindingService findings, CurrentModerator current) {
        this.work = work; this.findings = findings; this.current = current;
    }

    @GetMapping
    public List<InvestigationView> mine() { return work.mine(current.require()); }

    @GetMapping("/{id}")
    public ModeratorCase open(@PathVariable UUID id) { return work.open(current.require(), id); }

    @PostMapping("/{id}/findings")
    public ResponseEntity<FindingView> addFinding(@PathVariable UUID id, @RequestBody FindingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(findings.add(current.require(), id, req.text(), req.recommendation()));
    }

    /** Raw PNG or JPEG bytes as the body, read with a cap so an oversized upload is never held whole. */
    @PostMapping(value = "/{id}/findings/{findingId}/screenshots", consumes = {"image/png", "image/jpeg"})
    public ResponseEntity<Map<String, UUID>> addScreenshot(@PathVariable UUID id, @PathVariable UUID findingId, HttpServletRequest request) throws IOException {
        byte[] bytes = request.getInputStream().readNBytes(FindingService.MAX_BYTES + 1);
        UUID shot = findings.addScreenshot(current.require(), id, findingId, request.getContentType(), bytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", shot));
    }
}
