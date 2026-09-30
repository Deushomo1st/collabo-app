package com.collabo.backend.dto;

import com.collabo.backend.dto.AppealDtos.AppealDetail;
import com.collabo.backend.dto.AppealDtos.HistoryLine;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvestigationDtos {
    private InvestigationDtos() {}

    public record ReportRequest(String reason) {}

    public record AssignRequest(UUID moderatorId) {}

    public record DecideRequest(String outcome) {}

    /** recommendation is STICKS or DROPS and only for an appeal; the admin still decides. */
    public record FindingRequest(String text, String recommendation) {}

    /** screenshots are image ids, fetched one by one. */
    public record FindingView(UUID id, String moderator, String text, String recommendation, Instant createdAt, List<UUID> screenshots) {}

    /** tier is MYSPACE, WESPACE or WORKSPACE (null if the thread is gone); title is the room's name, or who is in a MySpace. moderator is null until assigned. */
    public record InvestigationView(UUID id, String kind, String status, String tier, String title, String reason, String reporter,
                                    UUID moderatorId, String moderator, Instant createdAt, Instant assignedAt) {}

    /** members are the usernames seated in the Yarnspace; appeal is set only for an APPEAL and carries the record and the room's window. */
    public record InvestigationDetail(InvestigationView investigation, List<String> members, AppealDetail appeal, List<FindingView> findings) {}

    /** What an assigned moderator sees: the Yarnspace's yarns (an appeal's window only), read-only, and their findings so far. */
    public record ModeratorCase(InvestigationView investigation, List<String> members, List<HistoryLine> yarns, List<FindingView> findings) {}
}
