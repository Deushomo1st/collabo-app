package com.collabo.backend.service;

import com.collabo.backend.dto.PostDtos.CommentResponse;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.entity.Notification;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.PostComment;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.PostCommentRepository;
import com.collabo.backend.repository.PostCommentLikeRepository;
import com.collabo.backend.entity.PostCommentLike;
import com.collabo.backend.repository.UserBlockRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Comments on posts. Blocked pairs never see each other's comments. The commenter or the post's author can remove one. */
@Service
@Transactional
public class CommentService {

    static final int MAX_BODY = 500;

    private final PostCommentRepository comments;
    private final PostCommentLikeRepository likes;
    private final PostService posts;
    private final UserRepository users;
    private final UserBlockRepository blocks;
    private final NotificationService notifications;

    public CommentService(PostCommentRepository comments, PostCommentLikeRepository likes, PostService posts, UserRepository users, UserBlockRepository blocks, NotificationService notifications) {
        this.likes = likes; this.comments = comments; this.posts = posts; this.users = users; this.blocks = blocks; this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(User viewer, UUID postId) {
        posts.visible(viewer, postId);
        Set<UUID> hidden = new HashSet<>(blocks.counterpartsOf(viewer.getId()));
        List<PostComment> rows = comments.findTop200ByPostIdOrderByCreatedAtAsc(postId).stream()
                .filter(c -> !hidden.contains(c.getAuthorId())).toList();
        Map<UUID, User> authors = users.findAllById(rows.stream().map(PostComment::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Set<UUID> ids = rows.stream().map(PostComment::getId).collect(Collectors.toSet());
        Map<UUID, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) for (Object[] row : likes.counts(ids)) counts.put((UUID) row[0], (Long) row[1]);
        Set<UUID> mine = ids.isEmpty() ? Set.of() : new HashSet<>(likes.likedAmong(viewer.getId(), ids));
        Map<UUID, Long> replyCounts = rows.stream().filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(PostComment::getParentId, Collectors.counting()));
        return rows.stream().map(c -> response(c, authors.get(c.getAuthorId()), viewer, counts.getOrDefault(c.getId(), 0L), mine.contains(c.getId()),
                replyCounts.getOrDefault(c.getId(), 0L))).toList();
    }

    public CommentResponse add(User me, UUID postId, String text, UUID parentId) {
        Post post = posts.visible(me, postId);
        if (!post.isCommentsOn()) throw new InvalidProfileException("The author turned comments off for this post.");
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) throw new InvalidProfileException("Write something first.");
        if (body.length() > MAX_BODY) throw new InvalidProfileException("Keep comments under " + MAX_BODY + " characters.");
        UUID parent = null;
        if (parentId != null) parent = visibleComment(me, postId, parentId).getId();   // a reply goes under any comment, however deep
        PostComment saved = comments.save(new PostComment(postId, me.getId(), body, parent));
        tell(me, post, parent);
        return response(saved, me, me, 0, false, 0);
    }

    /** The owner of the comment replied to hears of the reply, the author of the post hears of a comment (one line each, never about yourself, never across a block). */
    private void tell(User me, Post post, UUID parentId) {
        Set<UUID> hidden = new HashSet<>(blocks.counterpartsOf(me.getId()));
        String link = "/HTML-pages/view-post.html?id=" + post.getId();
        UUID replied = parentId == null ? null : comments.findById(parentId).map(PostComment::getAuthorId).orElse(null);
        if (replied != null && !replied.equals(me.getId()) && !hidden.contains(replied)) {
            notifications.notify(replied, Notification.Bucket.ACTIVITY, "New reply", me.getUsername() + " replied to your comment on \"" + post.getTitle() + "\".", link);
        }
        UUID owner = post.getAuthorId();
        if (!owner.equals(me.getId()) && !owner.equals(replied) && !hidden.contains(owner)) {
            notifications.notify(owner, Notification.Bucket.ACTIVITY, "New comment", me.getUsername() + " commented on \"" + post.getTitle() + "\".", link);
        }
    }

    public void delete(User me, UUID postId, UUID commentId) {
        Post post = posts.visible(me, postId);
        PostComment c = comments.findById(commentId).filter(x -> x.getPostId().equals(postId))
                .filter(x -> x.getAuthorId().equals(me.getId()) || post.getAuthorId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("That comment is gone."));
        dropWithReplies(c);
    }

    /** A comment goes with everything under it. */
    private void dropWithReplies(PostComment c) {
        for (PostComment r : comments.findByParentId(c.getId())) dropWithReplies(r);
        likes.deleteByCommentId(c.getId());
        comments.delete(c);
    }

    /** Like a comment you can see: free, idempotent, and its writer hears of it once. */
    public CommentResponse like(User me, UUID postId, UUID commentId) {
        PostComment c = visibleComment(me, postId, commentId);
        if (!likes.existsByCommentIdAndUserId(commentId, me.getId())) {
            likes.save(new PostCommentLike(commentId, postId, me.getId()));
            if (!c.getAuthorId().equals(me.getId())) {
                String title = posts.visible(me, postId).getTitle();
                notifications.notifyUnlessSame(c.getAuthorId(), Notification.Bucket.ACTIVITY, "New like", me.getUsername() + " liked your comment on \"" + title + "\".", "/HTML-pages/view-post.html?id=" + postId);
            }
        }
        return present(c, me);
    }

    public CommentResponse unlike(User me, UUID postId, UUID commentId) {
        PostComment c = visibleComment(me, postId, commentId);
        likes.deleteByCommentIdAndUserId(commentId, me.getId());
        return present(c, me);
    }

    /** The comment, unless it is gone, on another post, or its writer and the viewer have a block between them. */
    private PostComment visibleComment(User me, UUID postId, UUID commentId) {
        posts.visible(me, postId);
        PostComment c = comments.findById(commentId).filter(x -> x.getPostId().equals(postId))
                .orElseThrow(() -> new ResourceNotFoundException("That comment is gone."));
        if (blocks.counterpartsOf(me.getId()).contains(c.getAuthorId())) throw new ResourceNotFoundException("That comment is gone.");
        return c;
    }

    private CommentResponse present(PostComment c, User viewer) {
        User author = users.findById(c.getAuthorId()).orElseThrow(() -> new ResourceNotFoundException("That comment is gone."));
        long n = 0;
        for (Object[] row : likes.counts(Set.of(c.getId()))) n = (Long) row[1];
        return response(c, author, viewer, n, likes.existsByCommentIdAndUserId(c.getId(), viewer.getId()),
                comments.countByParentId(c.getId()));
    }

    private static CommentResponse response(PostComment c, User author, User viewer, long likes, boolean liked, long replies) {
        return new CommentResponse(c.getId(), PersonDto.of(author), c.getBody(), c.getCreatedAt(), author.getId().equals(viewer.getId()), likes, liked, c.getParentId(), replies);
    }
}
