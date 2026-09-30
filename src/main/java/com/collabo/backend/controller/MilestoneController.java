package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.MilestoneDtos.CreateRequest;
import com.collabo.backend.dto.MilestoneDtos.FulfilRequest;
import com.collabo.backend.dto.MilestoneDtos.MilestoneResponse;
import com.collabo.backend.service.MilestoneService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** A space's milestones. Thin shell over MilestoneService. */
@RestController
@RequestMapping("/api/spaces/{spaceId}/milestones")
public class MilestoneController {

    private final MilestoneService milestones;
    private final CurrentUser current;

    public MilestoneController(MilestoneService milestones, CurrentUser current) { this.milestones = milestones; this.current = current; }

    @PostMapping
    public MilestoneResponse create(@PathVariable UUID spaceId, @RequestBody CreateRequest req) {
        return milestones.create(current.require(), spaceId, req.title());
    }

    @GetMapping
    public List<MilestoneResponse> list(@PathVariable UUID spaceId) { return milestones.list(current.require(), spaceId); }

    @PostMapping("/{id}/fulfil")
    public MilestoneResponse fulfil(@PathVariable UUID spaceId, @PathVariable UUID id, @RequestBody(required = false) FulfilRequest req) {
        return milestones.fulfil(current.require(), spaceId, id, req == null ? null : req.note());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID spaceId, @PathVariable UUID id) { milestones.delete(current.require(), spaceId, id); }

    @PostMapping("/{id}/opt-out")
    public void optOut(@PathVariable UUID spaceId, @PathVariable UUID id) { milestones.optOut(current.require(), spaceId, id); }
}
