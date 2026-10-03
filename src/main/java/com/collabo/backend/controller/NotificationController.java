package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.NotificationDtos.CountResponse;
import com.collabo.backend.dto.NotificationDtos.NotificationResponse;
import com.collabo.backend.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** The signed-in user's notifications. Thin shell over NotificationService. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final CurrentUser current;

    public NotificationController(NotificationService notifications, CurrentUser current) { this.notifications = notifications; this.current = current; }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(required = false) String filter) { return notifications.list(current.require(), filter); }

    @GetMapping("/unread-count")
    public CountResponse unread() { return new CountResponse(notifications.unread(current.require())); }

    @PostMapping("/{id}/read")
    public void read(@PathVariable UUID id) { notifications.markRead(current.require(), id); }

    @PostMapping("/read-link")
    public void readLink(@RequestParam String link) { notifications.readLink(current.require(), link); }

    @PostMapping("/read-all")
    public void readAll() { notifications.readAll(current.require()); }
}
