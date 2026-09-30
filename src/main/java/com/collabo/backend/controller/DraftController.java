package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.DraftDtos.DraftRequest;
import com.collabo.backend.dto.DraftDtos.DraftResponse;
import com.collabo.backend.service.DraftService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Your saved drafts. Thin shell over DraftService. */
@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final DraftService drafts;
    private final CurrentUser current;

    public DraftController(DraftService drafts, CurrentUser current) { this.drafts = drafts; this.current = current; }

    @GetMapping
    public List<DraftResponse> list() { return drafts.list(current.require()); }

    @GetMapping("/{id}")
    public DraftResponse get(@PathVariable UUID id) { return drafts.get(current.require(), id); }

    /** Creates a draft, or replaces the one named by "id" in the body. */
    @PutMapping
    public DraftResponse save(@RequestBody DraftRequest req) { return drafts.save(current.require(), req); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        drafts.delete(current.require(), id);
        return ResponseEntity.noContent().build();
    }
}
