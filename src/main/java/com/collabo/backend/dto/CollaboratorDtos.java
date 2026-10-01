package com.collabo.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CollaboratorDtos {
    private CollaboratorDtos() {}

    public record InviteRequest(String username) {}

    /** state: INVITED (asked, not answered) or ACTIVE. */
    public record CollaboratorResponse(PersonDto person, String state, Instant createdAt) {}

    public record ReasonRequest(String reason) {}

    /** One seat at the collaborators' table. reason is set for FROZEN and DISBANDED. */
    public record SeatView(PersonDto person, String state, String reason, boolean founder) {}

    /**
     * The WeSpace's "about the group": the idea, who decides, and the state of each seat. role is FOUNDER, COLLABORATOR or FROZEN.
     * threadId is the collaborators' room; spaceId is set once a Workspace has formed.
     */
    public record WeSpaceAbout(UUID postId, String title, String body, String postStatus, String role, UUID threadId, UUID spaceId, int responseClockHours, List<SeatView> seats) {}

    public record ClockRequest(int hours) {}

    public record VoteRequest(String choice) {}

    /**
     * A collaborator flagged as quiet. state: RUNNING (clock ticking), VOTING (time ran out, collaborators choose), RESPONDED,
     * CANCELLED or DECIDED. In VOTING the counts and myVote are filled and voters is how many can vote; canVote says whether the viewer may.
     */
    public record CaseView(UUID id, PersonDto target, PersonDto initiator, String reason, String state, Instant startedAt, Instant deadline,
                           RemovalDtos.PleaView plea, int freezeVotes, int disbandVotes, int voters, String myVote, boolean canVote) {}

    /** A collaboration the viewer was asked into (INVITED) or has accepted (ACTIVE). postStatus is the post's pending/closed/formed. */
    public record RequestResponse(UUID postId, String postTitle, String postStatus, PersonDto founder, String state, Instant createdAt) {}
}
