package com.collabo.backend.dto;

import java.time.Instant;
import java.util.List;

public final class SpaceMemberDtos {
    private SpaceMemberDtos() {}

    /** Every field is optional; a missing one stays as it is. confirm must be true to hand out a weighty permission. */
    public record UpdateRequest(String title, List<String> permissions, Boolean confirm) {}

    public record MemberResponse(PersonDto person, boolean owner, String title, List<String> permissions, Instant joinedAt) {}
}
