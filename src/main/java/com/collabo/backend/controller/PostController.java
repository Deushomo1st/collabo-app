package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.PostDtos.PostRequest;
import com.collabo.backend.dto.PostDtos.PostResponse;
import com.collabo.backend.dto.PostDtos.WindowRequest;
import com.collabo.backend.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Posts on The Gaze. Thin shell over PostService. */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService posts;
    private final CurrentUser current;

    public PostController(PostService posts, CurrentUser current) {
        this.posts = posts; this.current = current;
    }

    @PostMapping
    public PostResponse create(@RequestBody PostRequest req) { return posts.create(current.require(), req); }

    @GetMapping("/{id}")
    public PostResponse get(@PathVariable UUID id) { return posts.get(current.require(), id); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        posts.delete(current.require(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/shout")
    public PostResponse shout(@PathVariable UUID id) { return posts.shout(current.require(), id); }

    @DeleteMapping("/{id}/shout")
    public PostResponse unshout(@PathVariable UUID id) { return posts.unshout(current.require(), id); }

    @PatchMapping("/{id}/window")
    public PostResponse window(@PathVariable UUID id, @RequestBody WindowRequest req) {
        return posts.setWindow(current.require(), id, req.applyBy());
    }
}
