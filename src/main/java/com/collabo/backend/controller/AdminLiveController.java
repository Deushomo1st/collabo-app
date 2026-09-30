package com.collabo.backend.controller;

import com.collabo.backend.live.AdminSocket;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Gated by AdminKeyFilter like the rest of /api/admin. Hands the console a one-time ticket for its live socket. */
@RestController
@RequestMapping("/api/admin/live")
public class AdminLiveController {

    private final AdminSocket socket;

    public AdminLiveController(AdminSocket socket) { this.socket = socket; }

    @PostMapping("/ticket")
    public Map<String, String> ticket() { return Map.of("ticket", socket.issue()); }
}
