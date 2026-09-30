package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.SpaceDtos.CreateRequest;
import com.collabo.backend.dto.SpaceDtos.SpaceResponse;
import com.collabo.backend.service.SpaceService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Forming and opening spaces. Thin shell over SpaceService. */
@RestController
@RequestMapping("/api")
public class SpaceController {

    private final SpaceService spaces;
    private final CurrentUser current;

    public SpaceController(SpaceService spaces, CurrentUser current) { this.spaces = spaces; this.current = current; }

    @PostMapping("/posts/{postId}/space")
    public SpaceResponse form(@PathVariable UUID postId, @RequestBody(required = false) CreateRequest req) {
        return spaces.form(current.require(), postId, req == null ? null : req.name());
    }

    @GetMapping("/posts/{postId}/space")
    public SpaceResponse forPost(@PathVariable UUID postId) { return spaces.forPost(current.require(), postId); }

    @GetMapping("/spaces/{id}")
    public SpaceResponse get(@PathVariable UUID id) { return spaces.get(current.require(), id); }
}
