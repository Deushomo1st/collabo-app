package com.collabo.backend.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response shapes for the Yarnspaces API, kept together because each is tiny. */
public final class YarnDtos {
    private YarnDtos() {}

    /** avatar is the picture's version (add it to the picture URL as ?v=), or null when they have none. */
    public record PersonView(UUID id, String username, Long avatar) {}

    public record ThreadView(UUID id, String tier, String name, String status,
                             boolean iAmRequester, boolean incomingRequest,
                             String lastBody, String lastSender, Instant lastAt, long unread,
                             boolean pinned, boolean muted, boolean archived,
                             UUID otherUserId, List<PersonView> members) {}

    /** receipt is only set on the caller's own yarns: SENT (saved), DELIVERED (every other member's browser has it) or READ (every other member has opened it). */
    public record YarnView(UUID id, UUID senderId, String sender, String kind, String body, Instant at, String receipt) {}

    /** A yarn found by keyword. The page already holds the thread and its members, so only ids come back. */
    public record YarnHit(UUID threadId, UUID senderId, String body, Instant at) {}

    public record BlockView(UUID userId, String username, Instant since) {}

    public record StartMySpace(
            @NotBlank(message = "Who do you want to yarn?") String username,
            @NotBlank(message = "Write a yarn first.") @Size(max = 2000, message = "A yarn can be at most 2000 characters.") String body) {}

    public record SendYarn(
            @NotBlank(message = "Write a yarn first.") @Size(max = 2000, message = "A yarn can be at most 2000 characters.") String body) {}

    /** Any field left null is left unchanged. */
    public record Prefs(Boolean archived, Boolean pinned, Boolean muted) {}

    public record Respond(boolean accept) {}
}
