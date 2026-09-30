package com.collabo.backend.dto;

import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public final class PostDtos {
    private PostDtos() {}

    public record PostRequest(String title, String body, Instant applyBy) {}

    /** applyBy null = indefinite. */
    public record WindowRequest(Instant applyBy) {}

    public record CommentRequest(String body) {}

    public record CommentResponse(UUID id, PersonDto author, String body, Instant createdAt, boolean mine) {}

    /** One page of a feed. next is the cursor for the following page, absent on the last one. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeedPage(java.util.List<PostResponse> items, Instant next) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PostResponse(UUID id, PersonDto author, String title, String body, Instant applyBy,
                               String status, Instant createdAt, boolean mine) {
        public static PostResponse of(Post p, User author, User viewer) {
            return new PostResponse(p.getId(), PersonDto.of(author), p.getTitle(), p.getBody(), p.getApplyBy(),
                    p.status(), p.getCreatedAt(), author.getId().equals(viewer.getId()));
        }
    }
}
