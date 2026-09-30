package com.collabo.backend.dto;

import com.collabo.backend.dto.AppealDtos.AppealDetail;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvestigationDtos {
    private InvestigationDtos() {}

    public record ReportRequest(String reason) {}

    public record AssignRequest(UUID moderatorId) {}

    public record DecideRequest(String outcome) {}

    /** tier is MYSPACE, WESPACE or WORKSPACE (null if the thread is gone); title is the room's name, or who is in a MySpace. moderator is null until assigned. */
    public record InvestigationView(UUID id, String kind, String status, String tier, String title, String reason, String reporter,
                                    UUID moderatorId, String moderator, Instant createdAt, Instant assignedAt) {}

    /** members are the usernames seated in the Yarnspace; appeal is set only for an APPEAL and carries the record and the room's window. */
    public record InvestigationDetail(InvestigationView investigation, List<String> members, AppealDetail appeal) {}
}
