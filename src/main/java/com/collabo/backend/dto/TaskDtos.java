package com.collabo.backend.dto;

import java.time.Instant;
import java.util.UUID;

public final class TaskDtos {
    private TaskDtos() {}

    public record CreateRequest(String title, String assignee) {}

    /** Only the fields that are present change; unassign clears the assignee. */
    public record UpdateRequest(String status, String assignee, Boolean unassign) {}

    public record TaskResponse(UUID id, String title, String status, PersonDto assignee, PersonDto createdBy, Instant createdAt, Instant doneAt) {}
}
