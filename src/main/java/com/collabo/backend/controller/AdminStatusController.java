package com.collabo.backend.controller;

import com.collabo.backend.dto.AdminStatus;
import com.collabo.backend.service.AdminStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gated by AdminKeyFilter (X-Admin-Key). Also answers "is my key right?" for the console's gate. */
@RestController
@RequestMapping("/api/admin/status")
public class AdminStatusController {

    private final AdminStatusService status;

    public AdminStatusController(AdminStatusService status) { this.status = status; }

    @GetMapping
    public AdminStatus get() { return status.status(); }
}
