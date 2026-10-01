package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.CollaboratorDtos.CaseView;
import com.collabo.backend.dto.CollaboratorDtos.ClockRequest;
import com.collabo.backend.dto.CollaboratorDtos.VoteRequest;
import com.collabo.backend.dto.RemovalDtos.StartRequest;
import com.collabo.backend.service.CollaboratorCaseService;
import com.collabo.backend.service.WeSpaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Flagging a quiet collaborator, the clock, pleas and the vote. Thin shell over CollaboratorCaseService. */
@RestController
@RequestMapping("/api/posts/{postId}")
public class CollaboratorCaseController {

    private final CollaboratorCaseService cases;
    private final WeSpaceService weSpace;
    private final CurrentUser current;

    public CollaboratorCaseController(CollaboratorCaseService cases, WeSpaceService weSpace, CurrentUser current) {
        this.cases = cases; this.weSpace = weSpace; this.current = current;
    }

    @PutMapping("/wespace/clock")
    public void clock(@PathVariable UUID postId, @RequestBody ClockRequest req) { weSpace.setClock(current.require(), postId, req.hours()); }

    @PostMapping("/cases")
    public CaseView start(@PathVariable UUID postId, @RequestBody StartRequest req) { return cases.start(current.require(), postId, req.username(), req.reason()); }

    @GetMapping("/cases")
    public List<CaseView> list(@PathVariable UUID postId) { return cases.list(current.require(), postId); }

    @PostMapping("/cases/{id}/respond")
    public CaseView respond(@PathVariable UUID postId, @PathVariable UUID id) { return cases.respond(current.require(), postId, id); }

    @PostMapping("/cases/{id}/plea")
    public CaseView plea(@PathVariable UUID postId, @PathVariable UUID id) { return cases.plea(current.require(), postId, id); }

    @PostMapping("/cases/{id}/cancel")
    public CaseView cancel(@PathVariable UUID postId, @PathVariable UUID id) { return cases.cancel(current.require(), postId, id); }

    @PostMapping("/cases/{id}/vote")
    public CaseView vote(@PathVariable UUID postId, @PathVariable UUID id, @RequestBody VoteRequest req) { return cases.vote(current.require(), postId, id, req.choice()); }
}
