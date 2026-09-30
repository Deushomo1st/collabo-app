package com.collabo.backend.dto;

import com.collabo.backend.entity.Application;
import com.collabo.backend.entity.Post;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public final class ApplicationDtos {
    private ApplicationDtos() {}

    public record ApplyRequest(String statement) {}

    /** decision: ACCEPT, DECLINE or SHORTLIST. */
    public record DecisionRequest(String decision) {}

    /** One card in the founder's review stack. */
    public record ReviewResponse(UUID id, PersonDto applicant, String statement, String state, Instant createdAt) {}

    /** An application with the post it is for; postStatus is that post's own pending/closed. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApplicationResponse(UUID id, UUID postId, String postTitle, String postStatus, PersonDto postAuthor,
                                      String statement, String state, Instant createdAt) {
        public static ApplicationResponse of(Application a, Post p, PersonDto author) {
            return new ApplicationResponse(a.getId(), a.getPostId(), p.getTitle(), p.status(), author,
                    a.getStatement(), a.getState().name(), a.getCreatedAt());
        }
    }
}
