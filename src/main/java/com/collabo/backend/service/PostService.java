package com.collabo.backend.service;

import com.collabo.backend.live.LiveSignals;
import com.collabo.backend.dto.PostDtos.PostRequest;
import com.collabo.backend.dto.PostDtos.PostResponse;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.entity.ApplicationState;
import com.collabo.backend.repository.ApplicationRepository;
import com.collabo.backend.repository.FollowRepository;
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
    private final ApplicationRepository applications;
    private final LiveSignals signals;
    private final MediaService media;
    private final DraftService drafts;
    private final YarnService yarns;
    private final FollowRepository follows;

    public PostService(PostRepository posts, UserRepository users, UserBlockRepository blocks, PostCommentRepository comments, ShoutRepository shouts,
                       ApplicationRepository applications, LiveSignals signals, MediaService media, DraftService drafts, YarnService yarns, FollowRepository follows) {
        this.follows = follows; this.signals = signals; this.media = media; this.drafts = drafts; this.yarns = yarns;
        this.posts = posts; this.users = users; this.blocks = blocks; this.comments = comments; this.shouts = shouts; this.applications = applications;
    }

    public PostResponse create(User me, PostRequest req) {
        String title = req.title() == null ? "" : req.title().trim();
        String body = req.body() == null ? "" : req.body().trim();
        if (title.isEmpty() || body.isEmpty()) throw new InvalidProfileException("A post needs a title and a description.");
        if (title.length() > MAX_TITLE) throw new InvalidProfileException("Keep the title under " + MAX_TITLE + " characters.");
        if (body.length() > MAX_BODY) throw new InvalidProfileException("Keep the description under " + MAX_BODY + " characters.");
        if (req.applyBy() != null && !req.applyBy().isAfter(Instant.now())) throw new InvalidProfileException("The application deadline must be in the future.");
        List<String> tags = Hashtags.clean(req.hashtags());
        List<String> recipients = DraftService.names(req.shareWith());
        List<com.collabo.backend.entity.Media> files = media.mine(me, req.mediaIds());
        Post p = new Post(me.getId(), title, body, req.applyBy());
        p.setTags(tags);
        p.setCommentsOn(!Boolean.FALSE.equals(req.commentsOn()));
        p.setShoutsOn(!Boolean.FALSE.equals(req.shoutsOn()));
        p.setAnonymous(Boolean.TRUE.equals(req.anonymous()));
        setAudience(p, req.audience(), req.audienceWith());
        Post saved = posts.save(p);
        media.attach(files, saved.getId());
        drafts.consume(me, req.draftId());
        signals.gaze();
        int delivered = 0;
        String note = (p.isAnonymous() ? "Someone" : me.getUsername()) + " shared a post with you: \"" + title + "\"\n/HTML-pages/gaze.html?post=" + saved.getId();
        for (String name : recipients) {
            User to = users.findByUsername(name).orElse(null);
            if (to != null && yarns.tryShare(me, to, note)) delivered++;
        }
        return view(saved, me).withShared(delivered);
    }

    @Transactional
    public PostResponse get(User viewer, UUID id) {
        Post p = visible(viewer, id);
        if (!p.getAuthorId().equals(viewer.getId())) posts.addView(id);   // opening your own post is not a view
        return view(p, viewer);
    }

    public void delete(User me, UUID id) {
        Post p = mine(me, id);
        if (p.isFormed()) throw new InvalidProfileException("A space was formed from this post, so it stays.");
        comments.deleteByPostId(id);
        shouts.deleteByPostId(id);
        applications.deleteByPostId(id);
        media.removeOfPost(id);
        posts.delete(p);
        signals.postGone(id);
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
        if (blocked(viewer.getId(), p.getAuthorId()) || !sees(viewer, p)) throw new ResourceNotFoundException("That post is gone.");
        return p;
    }

    static final int MAX_AUDIENCE = 200;

    private void setAudience(Post p, String kind, List<String> names) {
        String k = kind == null ? "EVERYONE" : kind.trim().toUpperCase();
        if (!List.of("EVERYONE", "FOLLOWERS", "ONLY", "EXCEPT").contains(k)) throw new InvalidProfileException("Unknown audience.");
        List<UUID> ids = k.equals("ONLY") || k.equals("EXCEPT") ? DraftService.names(names).stream().limit(MAX_AUDIENCE)
                .map(n -> users.findByUsername(n).map(User::getId).orElse(null)).filter(Objects::nonNull).toList() : List.of();
        if (k.equals("ONLY") && ids.isEmpty()) throw new InvalidProfileException("Pick at least one person who can see this post.");
        p.setAudience(k, ids);
    }

    /** The author always; otherwise by the post's audience. A listed person who has been deleted simply matches nothing. */
    boolean sees(User viewer, Post p) {
        if (p.getAuthorId().equals(viewer.getId())) return true;
        String id = viewer.getId().toString();
        return switch (p.getAudience()) {
            case "FOLLOWERS" -> follows.existsByFollowerIdAndFollowedId(viewer.getId(), p.getAuthorId());
            case "ONLY" -> p.audienceIds().contains(id);
            case "EXCEPT" -> !p.audienceIds().contains(id);
            default -> true;
        };
    }

    /** For other layers: throws "gone" unless the viewer may see this post. */
    @Transactional(readOnly = true)
    public void checkVisible(User viewer, UUID id) { visible(viewer, id); }

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
        Map<UUID, String> applied = new HashMap<>();
        for (Object[] row : applications.statesOf(viewer.getId(), ids, ApplicationState.WITHDRAWN)) applied.put((UUID) row[0], row[1].toString());
        Map<UUID, Long> applicants = new HashMap<>();
        Set<UUID> mineIds = ps.stream().filter(p -> p.getAuthorId().equals(viewer.getId())).map(Post::getId).collect(Collectors.toSet());
        if (!mineIds.isEmpty()) for (Object[] row : applications.counts(mineIds, ApplicationState.WITHDRAWN)) applicants.put((UUID) row[0], (Long) row[1]);
        Map<UUID, List<com.collabo.backend.entity.Media>> files = media.ofPosts(ids);
        return ps.stream().map(p -> {
            User author = authors.get(p.getAuthorId());
            if (author == null) throw new ResourceNotFoundException("That post is gone.");
            return PostResponse.of(p, author, viewer, counts.getOrDefault(p.getId(), 0L), mineShouted.contains(p.getId()), shouters.get(p.getId()),
                    applied.get(p.getId()), mineIds.contains(p.getId()) ? applicants.getOrDefault(p.getId(), 0L) : null, files.getOrDefault(p.getId(), List.of()));
        }).toList();
    }

    /** Shout a post out to your network. Free, silent, idempotent; not your own post. */
    public PostResponse shout(User me, UUID id) {
        Post p = visible(me, id);
        if (p.getAuthorId().equals(me.getId())) throw new InvalidProfileException("That one is already yours.");
        if (!p.isShoutsOn()) throw new InvalidProfileException("The author turned shout-outs off for this post.");
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
