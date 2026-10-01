package com.collabo.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() {}

    /** mediaIds are the user's own uploads from POST /api/media (at most 3). pageUrl is the page they came from. */
    public record SubmitRequest(String summary, String pageUrl, List<UUID> mediaIds) {}

    public record Submitted(UUID id) {}

    /** What the admin sees. */
    public record AdminView(UUID id, String reporter, String summary, String pageUrl, Instant createdAt, boolean resolved, List<PostDtos.MediaDto> media) {}

    public record ResolveRequest(boolean resolved) {}
}
