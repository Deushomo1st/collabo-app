package com.collabo.backend.controller;

import com.collabo.backend.dto.InvestigationDtos.*;
import com.collabo.backend.entity.FindingImage;
import com.collabo.backend.service.FindingService;
import com.collabo.backend.service.InvestigationService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The admin's investigation queue. Gated by AdminKeyFilter (X-Admin-Key), like the rest of /api/admin. */
@RestController
@RequestMapping("/api/admin/investigations")
public class AdminInvestigationController {

    private final InvestigationService investigations;

    private final FindingService findings;

    public AdminInvestigationController(InvestigationService investigations, FindingService findings) { this.investigations = investigations; this.findings = findings; }

    @GetMapping
    public List<InvestigationView> list(@RequestParam(defaultValue = "active") String status) { return investigations.list(status); }

    @GetMapping("/{id}")
    public InvestigationDetail detail(@PathVariable UUID id) { return investigations.detail(id); }

    @PostMapping("/{id}/assign")
    public InvestigationView assign(@PathVariable UUID id, @RequestBody AssignRequest req) { return investigations.assign(id, req.moderatorId()); }

    @PostMapping("/{id}/close")
    public InvestigationView close(@PathVariable UUID id) { return investigations.close(id); }

    /** A moderator's screenshot. The admin console fetches it with its key and shows it as a blob. */
    @GetMapping("/screenshots/{imageId}")
    public ResponseEntity<byte[]> screenshot(@PathVariable UUID imageId) {
        FindingImage img = findings.screenshot(imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.getContentType())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(img.getImage());
    }

    @PostMapping("/{id}/decide")
    public InvestigationView decide(@PathVariable UUID id, @RequestBody DecideRequest req) { return investigations.decide(id, req.outcome()); }
}
