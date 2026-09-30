package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.CollaboratorDtos.CollaboratorResponse;
import com.collabo.backend.dto.CollaboratorDtos.InviteRequest;
import com.collabo.backend.dto.CollaboratorDtos.RequestResponse;
import com.collabo.backend.service.CollaboratorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Asking, answering and removing collaborators. Thin shell over CollaboratorService. */
@RestController
@RequestMapping("/api")
public class CollaboratorController {

    private final CollaboratorService collaborators;
    private final CurrentUser current;

    public CollaboratorController(CollaboratorService collaborators, CurrentUser current) { this.collaborators = collaborators; this.current = current; }

    @PostMapping("/posts/{postId}/collaborators")
    public CollaboratorResponse invite(@PathVariable UUID postId, @RequestBody InviteRequest req) {
        return collaborators.invite(current.require(), postId, req.username());
    }

    @GetMapping("/posts/{postId}/collaborators")
    public List<CollaboratorResponse> list(@PathVariable UUID postId) { return collaborators.list(current.require(), postId); }

    @PostMapping("/posts/{postId}/collaborators/accept")
    public void accept(@PathVariable UUID postId) { collaborators.accept(current.require(), postId); }

    @PostMapping("/posts/{postId}/collaborators/decline")
    public void decline(@PathVariable UUID postId) { collaborators.decline(current.require(), postId); }

    @DeleteMapping("/posts/{postId}/collaborators/{username}")
    public void remove(@PathVariable UUID postId, @PathVariable String username) { collaborators.remove(current.require(), postId, username); }

    @GetMapping("/collaborations")
    public List<RequestResponse> mine() { return collaborators.mine(current.require()); }
}
