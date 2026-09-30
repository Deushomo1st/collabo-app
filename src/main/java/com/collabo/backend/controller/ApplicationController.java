package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.ApplicationDtos.ApplicationResponse;
import com.collabo.backend.dto.ApplicationDtos.ApplyRequest;
import com.collabo.backend.dto.ApplicationDtos.DecisionRequest;
import com.collabo.backend.dto.ApplicationDtos.ReviewResponse;
import com.collabo.backend.dto.CredentialsResponse;
import com.collabo.backend.service.ApplicationService;
import com.collabo.backend.service.CredentialService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Applying to posts and tracking your own applications. Thin shell over ApplicationService. */
@RestController
@RequestMapping("/api")
public class ApplicationController {

    private final ApplicationService applications;
    private final CredentialService credentials;
    private final CurrentUser current;

    public ApplicationController(ApplicationService applications, CredentialService credentials, CurrentUser current) {
        this.applications = applications; this.credentials = credentials; this.current = current;
    }

    @PostMapping("/posts/{postId}/applications")
    public ApplicationResponse apply(@PathVariable UUID postId, @RequestBody ApplyRequest req) {
        return applications.apply(current.require(), postId, req.statement());
    }

    @PostMapping("/applications/{id}/withdraw")
    public ApplicationResponse withdraw(@PathVariable UUID id) { return applications.withdraw(current.require(), id); }

    @GetMapping("/posts/{postId}/applications")
    public List<ReviewResponse> stack(@PathVariable UUID postId, @RequestParam(required = false) String sort,
                                      @RequestParam(required = false) String filter) {
        return applications.stack(current.require(), postId, sort, filter);
    }

    @PatchMapping("/applications/{id}")
    public ReviewResponse decide(@PathVariable UUID id, @RequestBody DecisionRequest req) {
        return applications.decide(current.require(), id, req.decision());
    }

    /** The founder's credentials, for someone who applied to this post. */
    @GetMapping("/posts/{postId}/founder-credentials")
    public CredentialsResponse founderCredentials(@PathVariable UUID postId) { return credentials.founderView(current.require(), postId); }

    @GetMapping("/applications/mine")
    public List<ApplicationResponse> mine() { return applications.mine(current.require()); }
}
