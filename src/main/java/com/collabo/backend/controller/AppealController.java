package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.AppealDtos.*;
import com.collabo.backend.service.AppealService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Appeals by the removed, and the moderators' queue. Thin shell over AppealService. */
@RestController
@RequestMapping("/api")
public class AppealController {

    private final AppealService appeals;
    private final CurrentUser current;

    public AppealController(AppealService appeals, CurrentUser current) { this.appeals = appeals; this.current = current; }

    @PostMapping("/removals/{recordId}/appeal")
    public AppealView appeal(@PathVariable UUID recordId, @RequestBody AppealRequest req) { return appeals.appeal(current.require(), recordId, req.note()); }

    @GetMapping("/moderation/appeals")
    public List<AppealView> queue(@RequestParam(defaultValue = "open") String status) { return appeals.queue(current.require(), "decided".equalsIgnoreCase(status)); }

    @GetMapping("/moderation/appeals/{id}")
    public AppealDetail detail(@PathVariable UUID id) { return appeals.detail(current.require(), id); }

    @PostMapping("/moderation/appeals/{id}/decide")
    public AppealView decide(@PathVariable UUID id, @RequestBody DecideRequest req) { return appeals.decide(current.require(), id, req.outcome()); }
}
