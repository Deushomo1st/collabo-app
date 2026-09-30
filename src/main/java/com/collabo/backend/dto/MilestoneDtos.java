package com.collabo.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MilestoneDtos {
    private MilestoneDtos() {}

    public record CreateRequest(String title) {}

    public record FulfilRequest(String note) {}

    /** credited: the members stamped when it was fulfilled (empty until then). */
    public record MilestoneResponse(UUID id, String title, String note, boolean fulfilled, Instant fulfilledAt,
                                    PersonDto createdBy, List<PersonDto> credited, Instant createdAt) {}
}
