package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.AppealDtos.AppealRequest;
import com.collabo.backend.dto.AppealDtos.AppealView;
import com.collabo.backend.service.AppealService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The removed person's appeal. The admin rules on it through /api/admin/investigations. Thin shell over AppealService. */
@RestController
@RequestMapping("/api")
public class AppealController {

    private final AppealService appeals;
    private final CurrentUser current;

    public AppealController(AppealService appeals, CurrentUser current) { this.appeals = appeals; this.current = current; }

    @PostMapping("/removals/{recordId}/appeal")
    public AppealView appeal(@PathVariable UUID recordId, @RequestBody AppealRequest req) { return appeals.appeal(current.require(), recordId, req.note()); }
}
