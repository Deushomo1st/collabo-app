package com.collabo.backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class CollaboratorDtos {
    private CollaboratorDtos() {}

    public record InviteRequest(String username) {}

    /** state: INVITED (asked, not answered) or ACTIVE. */
    public record CollaboratorResponse(PersonDto person, String state, Instant createdAt) {}

    /** A collaboration the viewer was asked into (INVITED) or has accepted (ACTIVE). postStatus is the post's pending/closed/formed. */
    public record RequestResponse(UUID postId, String postTitle, String postStatus, PersonDto founder, String state, Instant createdAt) {}
}
