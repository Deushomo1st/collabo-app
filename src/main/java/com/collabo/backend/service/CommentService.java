package com.collabo.backend.service;

import com.collabo.backend.dto.PostDtos.CommentResponse;
import com.collabo.backend.dto.PersonDto;
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

    public CommentService(PostCommentRepository comments, PostCommentLikeRepository likes, PostService posts, UserRepository users, UserBlockRepository blocks) {
        this.likes = likes; this.comments = comments; this.posts = posts; this.users = users; this.blocks = blocks;
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
        if (!posts.visible(me, postId).isCommentsOn()) throw new InvalidProfileException("The author turned comments off for this post.");
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) throw new InvalidProfileException("Write something first.");
        if (body.length() > MAX_BODY) throw new InvalidProfileException("Keep comments under " + MAX_BODY + " characters.");
        UUID parent = null;
        if (parentId != null) parent = visibleComment(me, postId, parentId).getId();   // a reply goes under any comment, however deep
        return response(comments.save(new PostComment(postId, me.getId(), body, parent)), me, me, 0, false, 0);
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

    /** Like a comment you can see: free, silent, idempotent. */
    public CommentResponse like(User me, UUID postId, UUID commentId) {
        PostComment c = visibleComment(me, postId, commentId);
        if (!likes.existsByCommentIdAndUserId(commentId, me.getId())) likes.save(new PostCommentLike(commentId, postId, me.getId()));
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
