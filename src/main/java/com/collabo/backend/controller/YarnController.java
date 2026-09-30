package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.exception.YarnException;
import com.collabo.backend.service.InvestigationService;
import com.collabo.backend.service.YarnService;
import com.collabo.backend.dto.InvestigationDtos.InvestigationView;
import com.collabo.backend.dto.InvestigationDtos.ReportRequest;

import com.collabo.backend.dto.YarnDtos.*;
import com.collabo.backend.entity.YarnThread.Tier;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Yarnspaces: MySpace / WeSpace / Workspace threads, archive, blocking. Thin shell over YarnService. */
@RestController
@RequestMapping("/api/yarns")
public class YarnController {

    private final YarnService service;
    private final InvestigationService investigations;
    private final CurrentUser caller;

    public YarnController(YarnService service, InvestigationService investigations, CurrentUser caller) {
        this.service = service;
        this.investigations = investigations;
        this.caller = caller;
    }

    /** Any member can report the Yarnspace they sit in, whatever its tier. The admin takes it from there. */
    @PostMapping("/threads/{id}/report")
    public InvestigationView report(@PathVariable UUID id, @RequestBody ReportRequest req) {
        return investigations.report(caller.require(), id, req.reason());
    }

    @GetMapping("/me")
    public PersonView me() {
        var me = caller.require();
        return new PersonView(me.getId(), me.getUsername(), null);
    }

    @GetMapping("/directory")
    public List<PersonView> directory(@RequestParam(defaultValue = "") String q) {
        return service.directory(caller.require(), q);
    }

    @GetMapping("/threads")
    public List<ThreadView> threads(@RequestParam(defaultValue = "inbox") String view,
                                    @RequestParam(required = false) String tier,
                                    @RequestParam(required = false) String q) {
        if (!view.equals("inbox") && !view.equals("archived")) throw new YarnException(HttpStatus.BAD_REQUEST, "view must be inbox or archived");
        return service.list(caller.require(), view.equals("archived"), parseTier(tier), q);
    }

    @PostMapping("/threads/myspace")
    public ResponseEntity<ThreadView> startMySpace(@Valid @RequestBody StartMySpace req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.startMySpace(caller.require(), req.username(), req.body()));
    }

    @GetMapping("/threads/{id}/yarns")
    public List<YarnView> history(@PathVariable UUID id,
                                  @RequestParam(required = false) Instant before, @RequestParam(defaultValue = "50") int limit) {
        return service.history(caller.require(), id, before, limit);
    }

    @PostMapping("/threads/{id}/yarns")
    public ResponseEntity<YarnView> send(@PathVariable UUID id, @Valid @RequestBody SendYarn req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(caller.require(), id, req.body()));
    }

    @PostMapping("/threads/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        service.markRead(caller.require(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/threads/{id}/prefs")
    public ThreadView prefs(@PathVariable UUID id, @RequestBody Prefs req) {
        return service.setPrefs(caller.require(), id, req);
    }

    @PostMapping("/threads/{id}/respond")
    public ThreadView respond(@PathVariable UUID id, @RequestBody Respond req) {
        return service.respond(caller.require(), id, req.accept());
    }

    @GetMapping("/blocks")
    public List<BlockView> blocked() {
        return service.blocked(caller.require());
    }

    @PutMapping("/blocks/{userId}")
    public ResponseEntity<Void> block(@PathVariable UUID userId) {
        service.block(caller.require(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/blocks/{userId}")
    public ResponseEntity<Void> unblock(@PathVariable UUID userId) {
        service.unblock(caller.require(), userId);
        return ResponseEntity.noContent().build();
    }

    private static Tier parseTier(String tier) {
        if (tier == null || tier.isBlank() || tier.equalsIgnoreCase("all")) return null;
        try { return Tier.valueOf(tier.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new YarnException(HttpStatus.BAD_REQUEST, "Unknown tier."); }
    }
}
