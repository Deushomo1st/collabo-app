package com.collabo.backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class CollaboratorDtos {
    private CollaboratorDtos() {}

    public record InviteRequest(String username) {}

    /** state: INVITED (asked, not answered) or ACTIVE. */
    public record CollaboratorResponse(PersonDto person, String state, Instant createdAt) {}

    /** A request waiting for the viewer's answer. */
    public record RequestResponse(UUID postId, String postTitle, PersonDto founder, Instant createdAt) {}
}
