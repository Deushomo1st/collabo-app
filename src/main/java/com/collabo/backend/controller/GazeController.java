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

    /** "N new": how many entries sit above the newest ones the browser is showing (top = comma-separated ids). */
    @GetMapping("/newer")
    public java.util.Map<String, Integer> newer(@RequestParam(required = false) String feed, @RequestParam(defaultValue = "false") boolean pending,
                                                @RequestParam(defaultValue = "") String top) {
        var tops = java.util.Arrays.stream(top.split(",")).limit(5).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> { try { return java.util.UUID.fromString(s); } catch (IllegalArgumentException e) { return null; } }).filter(java.util.Objects::nonNull).toList();
        return java.util.Map.of("count", gaze.newer(current.require(), feed, pending, tops));
    }

    @GetMapping("/search")
    public FeedPage search(@RequestParam String q, @RequestParam(required = false) Instant before, @RequestParam(required = false) Integer limit,
                           @RequestParam(defaultValue = "new") String sort) {
        return gaze.search(current.require(), q, before, limit, "top".equals(sort));
    }

    @GetMapping("/search/people")
    public java.util.List<com.collabo.backend.dto.PersonDto> people(@RequestParam String q) { return gaze.people(current.require(), q); }

    @GetMapping
    public FeedPage feed(@RequestParam(required = false) String feed,
                         @RequestParam(defaultValue = "false") boolean pending,
                         @RequestParam(required = false) Instant before,
                         @RequestParam(required = false) Integer limit) {
        return gaze.feed(current.require(), feed, pending, before, limit);
    }
}
