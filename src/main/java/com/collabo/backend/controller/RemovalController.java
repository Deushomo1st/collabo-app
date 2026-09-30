package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.RemovalDtos.RemovalResponse;
import com.collabo.backend.dto.RemovalDtos.StartRequest;
import com.collabo.backend.service.RemovalService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Removal for inefficiency in a space. Thin shell over RemovalService. */
@RestController
@RequestMapping("/api/spaces/{spaceId}/removals")
public class RemovalController {

    private final RemovalService removals;
    private final CurrentUser current;

    public RemovalController(RemovalService removals, CurrentUser current) { this.removals = removals; this.current = current; }

    @PostMapping
    public RemovalResponse start(@PathVariable UUID spaceId, @RequestBody StartRequest req) {
        return removals.start(current.require(), spaceId, req.username(), req.reason());
    }

    @GetMapping
    public List<RemovalResponse> list(@PathVariable UUID spaceId) { return removals.list(current.require(), spaceId); }

    @PostMapping("/{id}/respond")
    public RemovalResponse respond(@PathVariable UUID spaceId, @PathVariable UUID id) { return removals.respond(current.require(), spaceId, id); }

    @PostMapping("/{id}/plea")
    public RemovalResponse plea(@PathVariable UUID spaceId, @PathVariable UUID id) { return removals.plea(current.require(), spaceId, id); }

    @PostMapping("/{id}/cancel")
    public RemovalResponse cancel(@PathVariable UUID spaceId, @PathVariable UUID id) { return removals.cancel(current.require(), spaceId, id); }
}
