package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.PostDtos.CommentRequest;
import com.collabo.backend.dto.PostDtos.CommentResponse;
import com.collabo.backend.service.CommentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Comments on a post. Thin shell over CommentService. */
@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final CommentService comments;
    private final CurrentUser current;

    public CommentController(CommentService comments, CurrentUser current) {
        this.comments = comments; this.current = current;
    }

    @GetMapping
    public List<CommentResponse> list(@PathVariable UUID postId) { return comments.list(current.require(), postId); }

    @PostMapping
    public CommentResponse add(@PathVariable UUID postId, @RequestBody CommentRequest req) {
        return comments.add(current.require(), postId, req.body(), req.parentId());
    }

    @PutMapping("/{commentId}/like")
    public CommentResponse like(@PathVariable UUID postId, @PathVariable UUID commentId) {
        return comments.like(current.require(), postId, commentId);
    }

    @DeleteMapping("/{commentId}/like")
    public CommentResponse unlike(@PathVariable UUID postId, @PathVariable UUID commentId) {
        return comments.unlike(current.require(), postId, commentId);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID postId, @PathVariable UUID commentId) {
        comments.delete(current.require(), postId, commentId);
        return ResponseEntity.noContent().build();
    }
}
