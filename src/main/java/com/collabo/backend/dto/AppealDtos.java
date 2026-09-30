package com.collabo.backend.dto;

import com.collabo.backend.dto.RemovalRecordDtos.RecordView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AppealDtos {
    private AppealDtos() {}

    public record AppealRequest(String note) {}

    public record DecideRequest(String outcome) {}

    /** outcome, decidedBy and decidedAt are null while the appeal is open. */
    public record AppealView(UUID id, String note, String outcome, Instant createdAt, String decidedBy, Instant decidedAt, RecordView record) {}

    /** A yarn from the room; sender is null for system yarns. */
    public record HistoryLine(String sender, boolean system, String body, Instant createdAt) {}

    /** The appeal plus the room's yarns from a day before the removal to an hour after it. Nothing else of the room, and no DMs. */
    public record AppealDetail(UUID id, String note, String outcome, Instant createdAt, String decidedBy, Instant decidedAt, RecordView record, List<HistoryLine> history) {}
}
