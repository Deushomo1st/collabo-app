package com.collabo.backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class RemovalDtos {
    private RemovalDtos() {}

    public record StartRequest(String username, String reason) {}

    /** The plea currently holding the clock, if any. */
    public record PleaView(PersonDto by, Instant endsAt) {}

    /** state: RUNNING, RESPONDED (the person answered), CANCELLED (the founder ended it) or COMPLETED (time ran out and they were removed). */
    public record RemovalResponse(UUID id, PersonDto target, PersonDto initiator, String reason, String state,
                                  Instant startedAt, Instant deadline, PleaView plea) {}
}
