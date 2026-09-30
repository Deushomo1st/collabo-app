package com.collabo.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RemovalRecordDtos {
    private RemovalRecordDtos() {}

    public record AddressRequest(String body) {}

    public record AddressView(UUID id, PersonDto by, String body, Instant createdAt) {}

    /** badge: the removal stands as a mark only if the space had real work behind it. addresses are oldest first. appeal: null, OPEN, STICKS or DROPS. */
    public record RecordView(UUID id, String spaceName, PersonDto removed, PersonDto removedBy, String reason, boolean badge,
                             Instant createdAt, List<AddressView> addresses, String appeal) {}

    public record PremiumRequest(boolean premium) {}
}
