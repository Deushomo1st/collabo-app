package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.PostDtos.FeedPage;
import com.collabo.backend.service.GazeService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** A profile's Posts and Reposts tabs. Thin shell over GazeService. */
@RestController
@RequestMapping("/api/users/{username}/posts")
public class UserPostsController {

    private final GazeService gaze;
    private final CurrentUser current;

    public UserPostsController(GazeService gaze, CurrentUser current) {
        this.gaze = gaze; this.current = current;
    }

    @GetMapping
    public FeedPage posts(@PathVariable String username,
                          @RequestParam(required = false) String tab,
                          @RequestParam(required = false) Instant before,
                          @RequestParam(required = false) Integer limit) {
        return gaze.profile(current.require(), username, tab, before, limit);
    }
}
