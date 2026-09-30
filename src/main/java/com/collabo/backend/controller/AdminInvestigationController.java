package com.collabo.backend.controller;

import com.collabo.backend.dto.InvestigationDtos.*;
import com.collabo.backend.service.InvestigationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The admin's investigation queue. Gated by AdminKeyFilter (X-Admin-Key), like the rest of /api/admin. */
@RestController
@RequestMapping("/api/admin/investigations")
public class AdminInvestigationController {

    private final InvestigationService investigations;

    public AdminInvestigationController(InvestigationService investigations) { this.investigations = investigations; }

    @GetMapping
    public List<InvestigationView> list(@RequestParam(defaultValue = "active") String status) { return investigations.list(status); }

    @GetMapping("/{id}")
    public InvestigationDetail detail(@PathVariable UUID id) { return investigations.detail(id); }

    @PostMapping("/{id}/assign")
    public InvestigationView assign(@PathVariable UUID id, @RequestBody AssignRequest req) { return investigations.assign(id, req.moderatorId()); }

    @PostMapping("/{id}/decide")
    public InvestigationView decide(@PathVariable UUID id, @RequestBody DecideRequest req) { return investigations.decide(id, req.outcome()); }
}
