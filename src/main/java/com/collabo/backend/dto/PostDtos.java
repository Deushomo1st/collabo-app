package com.collabo.backend.dto;

import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

public final class PostDtos {
    private PostDtos() {}

    /**
     * hashtags, mediaIds (your own unposted uploads), commentsOn/shoutsOn (default on), shareWith (usernames to yarn the post to once it is up)
     * and draftId (the draft this came from, deleted on success) are all optional.
     */
    public record PostRequest(String title, String body, Instant applyBy, java.util.List<String> hashtags, java.util.List<UUID> mediaIds,
                              Boolean commentsOn, Boolean shoutsOn, java.util.List<String> shareWith, UUID draftId,
                              String audience, java.util.List<String> audienceWith, Boolean anonymous, Boolean applicationsOn) {}

    /** Who to yarn an existing post to. */
    public record ShareRequest(java.util.List<String> usernames) {}
    /** Posts that scrolled into view in a feed. */
    public record SeenRequest(java.util.List<java.util.UUID> ids) {}

    public record ShareResponse(int shared) {}

    public record MediaDto(UUID id, String kind) {}

    /** applyBy null = indefinite. */
    public record WindowRequest(Instant applyBy) {}

    /** parentId (optional): the comment this replies to. */
    public record CommentRequest(String body, UUID parentId) {}

    /** parentId: set on a reply. replies: how many replies a top-level comment has (the ones you can see). */
    public record CommentResponse(UUID id, PersonDto author, String body, Instant createdAt, boolean mine, long likes, boolean liked, UUID parentId, long replies) {}

    /** One comment as the feed previews it: who, and the start of what they said. */
    public record CommentSnippet(String username, String body) {}

    /** One page of a feed. next is the cursor for the following page, absent on the last one. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FeedPage(java.util.List<PostResponse> items, Instant next) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PostResponse(UUID id, PersonDto author, String title, String body, Instant applyBy,
                               String status, Instant createdAt, boolean mine, long shouts, boolean shouted,
                               PersonDto shoutedBy, String applied, Long applicants,
                               java.util.List<String> hashtags, java.util.List<MediaDto> media, boolean commentsOn, boolean shoutsOn, Integer shared, long views, boolean anonymous,
                               long likes, boolean liked, boolean applicationsOn, long commentCount, java.util.List<CommentSnippet> sample) {
        /** shoutedBy: who in your network shouted this out, when it appears in Shared Gaze or a Reposts tab. */
        /** applied: the viewer's own application state. applicants: how many applied, shown to the author only (nobody else learns it). */
        public static PostResponse of(Post p, User author, User viewer, long shouts, boolean shouted, User shoutedBy,
                                      String applied, Long applicants, java.util.List<com.collabo.backend.entity.Media> media, long likes, boolean liked,
                                      long commentCount, java.util.List<CommentSnippet> sample) {
            boolean hide = p.isAnonymous() && !author.getId().equals(viewer.getId());   // only the author knows it is theirs
            return new PostResponse(p.getId(), hide ? new PersonDto("Anonymous", null) : PersonDto.of(author), p.getTitle(), p.getBody(), p.getApplyBy(),
                    p.status(), p.getCreatedAt(), author.getId().equals(viewer.getId()), shouts, shouted,
                    shoutedBy == null ? null : PersonDto.of(shoutedBy), applied, applicants,
                    p.tags(), media.stream().map(m -> new MediaDto(m.getId(), m.kind())).toList(), p.isCommentsOn(), p.isShoutsOn(), null, p.getViews(), p.isAnonymous(),
                    likes, liked, p.isApplicationsOn(), commentCount, sample);
        }

        /** On the response to creating a post: how many of the people you picked got it as a yarn. */
        public PostResponse withShared(int n) {
            return new PostResponse(id, author, title, body, applyBy, status, createdAt, mine, shouts, shouted, shoutedBy, applied, applicants,
                    hashtags, media, commentsOn, shoutsOn, n, views, anonymous, likes, liked, applicationsOn, commentCount, sample);
        }
    }
}
