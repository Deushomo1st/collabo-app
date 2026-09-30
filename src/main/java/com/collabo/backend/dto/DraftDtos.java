package com.collabo.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DraftDtos {
    private DraftDtos() {}

    /** id null = a new draft. Everything else is optional. */
    public record DraftRequest(UUID id, String title, String body, Instant applyBy, List<String> hashtags, List<UUID> mediaIds,
                               Boolean commentsOn, Boolean shoutsOn, List<String> shareWith) {}

    public record DraftResponse(UUID id, String title, String body, Instant applyBy, List<String> hashtags, List<PostDtos.MediaDto> media,
                                boolean commentsOn, boolean shoutsOn, List<String> shareWith, Instant updatedAt) {}
}
