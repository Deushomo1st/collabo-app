package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.SpaceDtos.CreateRequest;
import com.collabo.backend.dto.SpaceDtos.SettingsRequest;
import com.collabo.backend.dto.SpaceDtos.SpaceResponse;
import com.collabo.backend.service.SpaceService;
import com.collabo.backend.service.SpaceSettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Forming, opening and configuring spaces. Thin shell over SpaceService and SpaceSettingsService. */
@RestController
@RequestMapping("/api")
public class SpaceController {

    private final SpaceService spaces;
    private final SpaceSettingsService settings;
    private final CurrentUser current;

    public SpaceController(SpaceService spaces, SpaceSettingsService settings, CurrentUser current) {
        this.spaces = spaces; this.settings = settings; this.current = current;
    }

    @PostMapping("/posts/{postId}/space")
    public SpaceResponse form(@PathVariable UUID postId, @RequestBody(required = false) CreateRequest req) {
        return req == null ? spaces.form(current.require(), postId, null, null, null)
                : spaces.form(current.require(), postId, req.name(), req.responseClockHours(), req.pleasEnabled());
    }

    @GetMapping("/posts/{postId}/space")
    public SpaceResponse forPost(@PathVariable UUID postId) { return spaces.forPost(current.require(), postId); }

    @GetMapping("/spaces/{id}")
    public SpaceResponse get(@PathVariable UUID id) { return spaces.get(current.require(), id); }

    @PatchMapping("/spaces/{id}/settings")
    public SpaceResponse settings(@PathVariable UUID id, @RequestBody SettingsRequest req) {
        return settings.update(current.require(), id, req.name(), req.responseClockHours(), req.pleasEnabled());
    }
}
