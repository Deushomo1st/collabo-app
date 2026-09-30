package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.SpaceMemberDtos.MemberResponse;
import com.collabo.backend.dto.SpaceMemberDtos.UpdateRequest;
import com.collabo.backend.service.SpaceMemberService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Joining, leaving and managing the people in a space. Thin shell over SpaceMemberService. */
@RestController
@RequestMapping("/api/spaces/{id}")
public class SpaceMemberController {

    private final SpaceMemberService members;
    private final CurrentUser current;

    public SpaceMemberController(SpaceMemberService members, CurrentUser current) { this.members = members; this.current = current; }

    @PostMapping("/join")
    public void join(@PathVariable UUID id) { members.join(current.require(), id); }

    @PostMapping("/leave")
    public void leave(@PathVariable UUID id) { members.leave(current.require(), id); }

    @GetMapping("/members")
    public List<MemberResponse> list(@PathVariable UUID id) { return members.list(current.require(), id); }

    @PatchMapping("/members/{username}")
    public MemberResponse update(@PathVariable UUID id, @PathVariable String username, @RequestBody UpdateRequest req) {
        return members.update(current.require(), id, username, req.title(), req.permissions(), req.confirm());
    }

    @DeleteMapping("/members/{username}")
    public void remove(@PathVariable UUID id, @PathVariable String username) { members.remove(current.require(), id, username); }
}
