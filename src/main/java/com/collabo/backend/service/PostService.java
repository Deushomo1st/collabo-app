package com.collabo.backend.service;

import com.collabo.backend.dto.PostDtos.PostRequest;
import com.collabo.backend.dto.PostDtos.PostResponse;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.PostCommentRepository;
import com.collabo.backend.repository.PostRepository;
import com.collabo.backend.repository.ShoutRepository;
import com.collabo.backend.entity.Shout;
import com.collabo.backend.repository.UserBlockRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Posts to The Gaze. Blocked pairs never see each other's posts, and are never told why. */
@Service
@Transactional
public class PostService {

    static final int MAX_TITLE = 120;
    static final int MAX_BODY = 2000;
    static final Duration GRACE = Duration.ofHours(24);   // shortening a window never closes anyone out sooner than this

    private final PostRepository posts;
    private final UserRepository users;
    private final UserBlockRepository blocks;
    private final PostCommentRepository comments;
    private final ShoutRepository shouts;

    public PostService(PostRepository posts, UserRepository users, UserBlockRepository blocks, PostCommentRepository comments, ShoutRepository shouts) {
        this.posts = posts; this.users = users; this.blocks = blocks; this.comments = comments; this.shouts = shouts;
    }

    public PostResponse create(User me, PostRequest req) {
        String title = req.title() == null ? "" : req.title().trim();
        String body = req.body() == null ? "" : req.body().trim();
        if (title.isEmpty() || body.isEmpty()) throw new InvalidProfileException("A post needs a title and a description.");
        if (title.length() > MAX_TITLE) throw new InvalidProfileException("Keep the title under " + MAX_TITLE + " characters.");
        if (body.length() > MAX_BODY) throw new InvalidProfileException("Keep the description under " + MAX_BODY + " characters.");
        if (req.applyBy() != null && !req.applyBy().isAfter(Instant.now())) throw new InvalidProfileException("The application deadline must be in the future.");
        return view(posts.save(new Post(me.getId(), title, body, req.applyBy())), me);
    }

    @Transactional(readOnly = true)
    public PostResponse get(User viewer, UUID id) {
        return view(visible(viewer, id), viewer);
    }

    public void delete(User me, UUID id) {
        Post p = mine(me, id);
        comments.deleteByPostId(id);
        shouts.deleteByPostId(id);
        posts.delete(p);
    }

    /** null = indefinite. Extending is free; shortening waits at least 24 hours so nobody is closed out mid-draft. */
    public PostResponse setWindow(User me, UUID id, Instant requested) {
        Post p = mine(me, id);
        Instant current = p.getApplyBy();
        boolean shortens = requested != null && (current == null || requested.isBefore(current));
        if (shortens) requested = max(requested, Instant.now().plus(GRACE));
        p.setApplyBy(requested);
        return view(posts.save(p), me);
    }

    // ---- shared with the feed and later checkpoints ---------------------------------------------------------

    /** The post, unless it is missing or the viewer and its author have a block between them. */
    Post visible(User viewer, UUID id) {
        Post p = posts.findById(id).orElseThrow(() -> new ResourceNotFoundException("That post is gone."));
        if (blocked(viewer.getId(), p.getAuthorId())) throw new ResourceNotFoundException("That post is gone.");
        return p;
    }

    boolean blocked(UUID a, UUID b) {
        return blocks.existsByBlockerIdAndBlockedId(a, b) || blocks.existsByBlockerIdAndBlockedId(b, a);
    }

    PostResponse view(Post p, User viewer) {
        return present(List.of(p), viewer, Map.of()).get(0);
    }

    /**
     * Turns posts into responses in three queries however many there are.
     * shouters: postId -> the network member whose shout-out put it in this list (empty for plain posts).
     */
    List<PostResponse> present(List<Post> ps, User viewer, Map<UUID, User> shouters) {
        if (ps.isEmpty()) return List.of();
        Set<UUID> ids = ps.stream().map(Post::getId).collect(Collectors.toSet());
        Map<UUID, User> authors = users.findAllById(ps.stream().map(Post::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : shouts.counts(ids)) counts.put((UUID) row[0], (Long) row[1]);
        Set<UUID> mineShouted = new HashSet<>(shouts.shoutedAmong(viewer.getId(), ids));
        return ps.stream().map(p -> {
            User author = authors.get(p.getAuthorId());
            if (author == null) throw new ResourceNotFoundException("That post is gone.");
            return PostResponse.of(p, author, viewer, counts.getOrDefault(p.getId(), 0L), mineShouted.contains(p.getId()), shouters.get(p.getId()));
        }).toList();
    }

    /** Shout a post out to your network. Free, silent, idempotent; not your own post. */
    public PostResponse shout(User me, UUID id) {
        Post p = visible(me, id);
        if (p.getAuthorId().equals(me.getId())) throw new InvalidProfileException("That one is already yours.");
        if (!shouts.existsByPostIdAndUserId(id, me.getId())) shouts.save(new Shout(id, me.getId()));
        return view(p, me);
    }

    public PostResponse unshout(User me, UUID id) {
        Post p = visible(me, id);
        shouts.deleteByPostIdAndUserId(id, me.getId());
        return view(p, me);
    }

    private Post mine(User me, UUID id) {
        return posts.findById(id).filter(p -> p.getAuthorId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("That post is gone."));
    }

    private static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
}
