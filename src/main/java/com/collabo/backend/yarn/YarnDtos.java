package com.collabo.backend.yarn;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response shapes for the Yarnspaces API, kept together because each is tiny. */
public final class YarnDtos {
    private YarnDtos() {}

    public record PersonView(UUID id, String username) {}

    public record ThreadView(UUID id, String tier, String name, String status,
                             boolean iAmRequester, boolean incomingRequest,
                             String lastBody, String lastSender, Instant lastAt, long unread,
                             boolean pinned, boolean muted, boolean archived,
                             UUID otherUserId, List<PersonView> members) {}

    public record YarnView(UUID id, UUID senderId, String sender, String kind, String body, Instant at) {}

    public record BlockView(UUID userId, String username, Instant since) {}

    public record StartMySpace(
            @NotBlank(message = "Who do you want to yarn?") String username,
            @NotBlank(message = "Write a yarn first.") @Size(max = 2000, message = "A yarn can be at most 2000 characters.") String body) {}

    public record NewGroup(
            @NotNull(message = "Pick WeSpace or Workspace.") YarnThread.Tier tier,
            @NotBlank(message = "Give it a name.") @Size(max = 80, message = "Names can be at most 80 characters.") String name,
            @NotNull(message = "Add at least one person.") @Size(min = 1, max = 30, message = "Add between 1 and 30 people.") List<@NotBlank String> usernames) {}

    public record SendYarn(
            @NotBlank(message = "Write a yarn first.") @Size(max = 2000, message = "A yarn can be at most 2000 characters.") String body) {}

    /** Any field left null is left unchanged. */
    public record Prefs(Boolean archived, Boolean pinned, Boolean muted) {}

    public record Respond(boolean accept) {}
}
