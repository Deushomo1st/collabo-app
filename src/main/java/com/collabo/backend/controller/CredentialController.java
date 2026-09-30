package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.CredentialsResponse;
import com.collabo.backend.dto.FeatureRequest;
import com.collabo.backend.dto.ShippedLinkRequest;
import com.collabo.backend.service.CredentialService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Owner-only changes to a credential entry: feats and shipped links. Thin shell over CredentialService. */
@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    private final CredentialService credentials;
    private final CurrentUser current;

    public CredentialController(CredentialService credentials, CurrentUser current) {
        this.credentials = credentials;
        this.current = current;
    }

    @PatchMapping("/{id}")
    public CredentialsResponse.Entry feature(@PathVariable UUID id, @RequestBody FeatureRequest req) {
        return credentials.setFeatured(current.require(), id, req.featured());
    }

    @PostMapping("/{id}/shipped")
    public CredentialsResponse.Entry addShipped(@PathVariable UUID id, @RequestBody ShippedLinkRequest req) {
        return credentials.addShipped(current.require(), id, req);
    }

    @DeleteMapping("/{id}/shipped/{linkId}")
    public CredentialsResponse.Entry removeShipped(@PathVariable UUID id, @PathVariable UUID linkId) {
        return credentials.removeShipped(current.require(), id, linkId);
    }
}
