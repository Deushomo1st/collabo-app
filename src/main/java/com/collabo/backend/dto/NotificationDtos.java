package com.collabo.backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationResponse(UUID id, String bucket, boolean actionRequired, String title, String body, String link, boolean read, Instant createdAt) {}
    public record CountResponse(long count) {}
}
