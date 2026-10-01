package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.YarnDtos.YarnView;
import com.collabo.backend.service.PinService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** A Workspace room's pinned yarns. Thin shell over PinService. */
@RestController
@RequestMapping("/api/spaces/{spaceId}/pins")
public class PinController {

    private final PinService pins;
    private final CurrentUser current;

    public PinController(PinService pins, CurrentUser current) { this.pins = pins; this.current = current; }

    @GetMapping
    public List<YarnView> list(@PathVariable UUID spaceId) { return pins.list(current.require(), spaceId); }

    @PutMapping("/{yarnId}")
    public void pin(@PathVariable UUID spaceId, @PathVariable UUID yarnId) { pins.set(current.require(), spaceId, yarnId, true); }

    @DeleteMapping("/{yarnId}")
    public void unpin(@PathVariable UUID spaceId, @PathVariable UUID yarnId) { pins.set(current.require(), spaceId, yarnId, false); }
}
