package com.collabo.backend.service;

import com.collabo.backend.dto.PostDtos.CommentResponse;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.PostComment;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.PostCommentRepository;
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
    private final PostService posts;
    private final UserRepository users;
    private final UserBlockRepository blocks;

    public CommentService(PostCommentRepository comments, PostService posts, UserRepository users, UserBlockRepository blocks) {
        this.comments = comments; this.posts = posts; this.users = users; this.blocks = blocks;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(User viewer, UUID postId) {
        posts.visible(viewer, postId);
        Set<UUID> hidden = new HashSet<>(blocks.counterpartsOf(viewer.getId()));
        List<PostComment> rows = comments.findTop200ByPostIdOrderByCreatedAtAsc(postId).stream()
                .filter(c -> !hidden.contains(c.getAuthorId())).toList();
        Map<UUID, User> authors = users.findAllById(rows.stream().map(PostComment::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().map(c -> response(c, authors.get(c.getAuthorId()), viewer)).toList();
    }

    public CommentResponse add(User me, UUID postId, String text) {
        posts.visible(me, postId);
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) throw new InvalidProfileException("Write something first.");
        if (body.length() > MAX_BODY) throw new InvalidProfileException("Keep comments under " + MAX_BODY + " characters.");
        return response(comments.save(new PostComment(postId, me.getId(), body)), me, me);
    }

    public void delete(User me, UUID postId, UUID commentId) {
        Post post = posts.visible(me, postId);
        PostComment c = comments.findById(commentId).filter(x -> x.getPostId().equals(postId))
                .filter(x -> x.getAuthorId().equals(me.getId()) || post.getAuthorId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("That comment is gone."));
        comments.delete(c);
    }

    private static CommentResponse response(PostComment c, User author, User viewer) {
        return new CommentResponse(c.getId(), PersonDto.of(author), c.getBody(), c.getCreatedAt(), author.getId().equals(viewer.getId()));
    }
}
