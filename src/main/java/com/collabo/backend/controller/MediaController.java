package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.PostDtos.MediaDto;
import com.collabo.backend.entity.Media;
import com.collabo.backend.entity.User;
import com.collabo.backend.service.MediaService;
import com.collabo.backend.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

/** Pictures and videos for posts. The upload is the raw file as the request body, with its own Content-Type. */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService media;
    private final PostService posts;
    private final CurrentUser current;

    public MediaController(MediaService media, PostService posts, CurrentUser current) { this.media = media; this.posts = posts; this.current = current; }

    @PostMapping
    public MediaDto upload(HttpServletRequest req) throws IOException {
        Media m = media.store(current.require(), req.getContentType(), req.getInputStream());
        return new MediaDto(m.getId(), m.kind());
    }

    /** Range requests work (a video can seek); the owner sees their unposted uploads, everyone else only files on posts they can see. */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        User me = current.require();
        Media m = media.find(id);
        if (!m.getOwnerId().equals(me.getId())) {
            if (m.getPostId() == null) throw new com.collabo.backend.exception.ResourceNotFoundException("That file is gone.");
            posts.checkVisible(me, m.getPostId());
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(m.getContentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePrivate().immutable())
                .body(new FileSystemResource(media.file(id)));
    }

    /** Takes back an upload that is not on a post yet. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> discard(@PathVariable UUID id) {
        media.discard(current.require(), id);
        return ResponseEntity.noContent().build();
    }
}
