package com.collabo.backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class SpaceDtos {
    private SpaceDtos() {}

    /** name is optional; the post's title is used when it is missing. */
    public record CreateRequest(String name) {}

    /** role is how the viewer relates to it: OWNER, MEMBER (joined), or APPLICANT (accepted, not yet joined); canManage: the owner or a collaborator, who set titles and permissions. */
    public record SpaceResponse(UUID id, UUID postId, String name, String postTitle, String postBody, PersonDto owner,
                                String role, boolean canManage, Instant createdAt) {}
}
