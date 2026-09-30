package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.ApplicationDtos.ApplicationResponse;
import com.collabo.backend.dto.ApplicationDtos.ApplyRequest;
import com.collabo.backend.service.ApplicationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Applying to posts and tracking your own applications. Thin shell over ApplicationService. */
@RestController
@RequestMapping("/api")
public class ApplicationController {

    private final ApplicationService applications;
    private final CurrentUser current;

    public ApplicationController(ApplicationService applications, CurrentUser current) {
        this.applications = applications; this.current = current;
    }

    @PostMapping("/posts/{postId}/applications")
    public ApplicationResponse apply(@PathVariable UUID postId, @RequestBody ApplyRequest req) {
        return applications.apply(current.require(), postId, req.statement());
    }

    @PostMapping("/applications/{id}/withdraw")
    public ApplicationResponse withdraw(@PathVariable UUID id) { return applications.withdraw(current.require(), id); }

    @GetMapping("/applications/mine")
    public List<ApplicationResponse> mine() { return applications.mine(current.require()); }
}
