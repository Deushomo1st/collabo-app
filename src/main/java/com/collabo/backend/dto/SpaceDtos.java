package com.collabo.backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class SpaceDtos {
    private SpaceDtos() {}

    /** Everything is optional: the post's title names the space, the clock falls back to 72 hours, pleas default to on. */
    public record CreateRequest(String name, Integer responseClockHours, Boolean pleasEnabled) {}

    /** Every field is optional; a missing one stays as it is. */
    public record SettingsRequest(String name, Integer responseClockHours, Boolean pleasEnabled) {}

    /** role is how the viewer relates to it: OWNER, MEMBER (joined), or APPLICANT (accepted, not yet joined); canManage: the owner or a collaborator, who set titles and permissions; threadId: the Workspace thread, only for people already in the room. */
    public record SpaceResponse(UUID id, UUID postId, String name, String postTitle, String postBody, PersonDto owner,
                                String role, boolean canManage, UUID threadId,
                                int responseClockHours, boolean pleasEnabled, Instant createdAt) {}
}
