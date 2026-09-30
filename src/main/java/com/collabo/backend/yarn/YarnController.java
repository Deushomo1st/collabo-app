package com.collabo.backend.yarn;

import com.collabo.backend.yarn.YarnDtos.*;
import com.collabo.backend.yarn.YarnThread.Tier;
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

    private static final String WHO = "X-Dev-User";

    private final YarnService service;
    private final YarnCaller caller;

    public YarnController(YarnService service, YarnCaller caller) {
        this.service = service;
        this.caller = caller;
    }

    @GetMapping("/me")
    public PersonView me(@RequestHeader(value = WHO, required = false) String who) {
        var me = caller.require(who);
        return new PersonView(me.getId(), me.getUsername());
    }

    @GetMapping("/directory")
    public List<PersonView> directory(@RequestHeader(value = WHO, required = false) String who, @RequestParam(defaultValue = "") String q) {
        return service.directory(caller.require(who), q);
    }

    @GetMapping("/threads")
    public List<ThreadView> threads(@RequestHeader(value = WHO, required = false) String who,
                                    @RequestParam(defaultValue = "inbox") String view,
                                    @RequestParam(required = false) String tier,
                                    @RequestParam(required = false) String q) {
        if (!view.equals("inbox") && !view.equals("archived")) throw new YarnException(HttpStatus.BAD_REQUEST, "view must be inbox or archived");
        return service.list(caller.require(who), view.equals("archived"), parseTier(tier), q);
    }

    @PostMapping("/threads/myspace")
    public ResponseEntity<ThreadView> startMySpace(@RequestHeader(value = WHO, required = false) String who, @Valid @RequestBody StartMySpace req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.startMySpace(caller.require(who), req.username(), req.body()));
    }

    @PostMapping("/threads/group")
    public ResponseEntity<ThreadView> createGroup(@RequestHeader(value = WHO, required = false) String who, @Valid @RequestBody NewGroup req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGroup(caller.require(who), req.tier(), req.name(), req.usernames()));
    }

    @GetMapping("/threads/{id}/yarns")
    public List<YarnView> history(@RequestHeader(value = WHO, required = false) String who, @PathVariable UUID id,
                                  @RequestParam(required = false) Instant before, @RequestParam(defaultValue = "50") int limit) {
        return service.history(caller.require(who), id, before, limit);
    }

    @PostMapping("/threads/{id}/yarns")
    public ResponseEntity<YarnView> send(@RequestHeader(value = WHO, required = false) String who, @PathVariable UUID id, @Valid @RequestBody SendYarn req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(caller.require(who), id, req.body()));
    }

    @PostMapping("/threads/{id}/read")
    public ResponseEntity<Void> read(@RequestHeader(value = WHO, required = false) String who, @PathVariable UUID id) {
        service.markRead(caller.require(who), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/threads/{id}/prefs")
    public ThreadView prefs(@RequestHeader(value = WHO, required = false) String who, @PathVariable UUID id, @RequestBody Prefs req) {
        return service.setPrefs(caller.require(who), id, req);
    }

    @PostMapping("/threads/{id}/respond")
    public ThreadView respond(@RequestHeader(value = WHO, required = false) String who, @PathVariable UUID id, @RequestBody Respond req) {
        return service.respond(caller.require(who), id, req.accept());
    }

    @GetMapping("/blocks")
    public List<BlockView> blocked(@RequestHeader(value = WHO, required = false) String who) {
        return service.blocked(caller.require(who));
    }

    @PutMapping("/blocks/{userId}")
    public ResponseEntity<Void> block(@RequestHeader(value = WHO, required = false) String who, @PathVariable UUID userId) {
        service.block(caller.require(who), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/blocks/{userId}")
    public ResponseEntity<Void> unblock(@RequestHeader(value = WHO, required = false) String who, @PathVariable UUID userId) {
        service.unblock(caller.require(who), userId);
        return ResponseEntity.noContent().build();
    }

    private static Tier parseTier(String tier) {
        if (tier == null || tier.isBlank() || tier.equalsIgnoreCase("all")) return null;
        try { return Tier.valueOf(tier.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new YarnException(HttpStatus.BAD_REQUEST, "Unknown tier."); }
    }
}
