package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.PostDtos.FeedPage;
import com.collabo.backend.service.GazeService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** The Gaze and Shared Gaze. Thin shell over GazeService. */
@RestController
@RequestMapping("/api/gaze")
public class GazeController {

    private final GazeService gaze;
    private final CurrentUser current;

    public GazeController(GazeService gaze, CurrentUser current) {
        this.gaze = gaze; this.current = current;
    }

    @GetMapping
    public FeedPage feed(@RequestParam(required = false) String feed,
                         @RequestParam(defaultValue = "false") boolean pending,
                         @RequestParam(required = false) Instant before,
                         @RequestParam(required = false) Integer limit) {
        return gaze.feed(current.require(), feed, pending, before, limit);
    }
}
