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
import com.collabo.backend.repository.PostLikeRepository;
import com.collabo.backend.entity.PostLike;
import com.collabo.backend.entity.PostComment;
import com.collabo.backend.repository.PostCommentLikeRepository;
import com.collabo.backend.dto.PostDtos.CommentSnippet;
import org.springframework.data.domain.PageRequest;
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
    private final PostLikeRepository likes;
    private final PostCommentLikeRepository commentLikes;
    private final ApplicationRepository applications;
    private final LiveSignals signals;
    private final MediaService media;
    private final DraftService drafts;
    private final YarnService yarns;
    private final FollowRepository follows;

    public PostService(PostRepository posts, UserRepository users, UserBlockRepository blocks, PostCommentRepository comments, ShoutRepository shouts, PostLikeRepository likes, PostCommentLikeRepository commentLikes,
                       ApplicationRepository applications, LiveSignals signals, MediaService media, DraftService drafts, YarnService yarns, FollowRepository follows) {
        this.likes = likes; this.commentLikes = commentLikes; this.follows = follows; this.signals = signals; this.media = media; this.drafts = drafts; this.yarns = yarns;
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
        p.setApplicationsOn(!Boolean.FALSE.equals(req.applicationsOn()));
        p.setAnonymous(Boolean.TRUE.equals(req.anonymous()));
        setAudience(p, req.audience(), req.audienceWith());
        Post saved = posts.save(p);
        media.attach(files, saved.getId());
        drafts.consume(me, req.draftId());
        signals.gaze();
        int delivered = 0;
        String note = (p.isAnonymous() ? "Someone" : me.getUsername()) + " shared a post with you: \"" + title + "\"\n/HTML-pages/post-view.html?id=" + saved.getId();
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
        commentLikes.deleteByPostId(id);
        comments.deleteByPostId(id);
        shouts.deleteByPostId(id);
        likes.deleteByPostId(id);
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
        Map<UUID, Long> likeCounts = new HashMap<>();
        for (Object[] row : likes.counts(ids)) likeCounts.put((UUID) row[0], (Long) row[1]);
        Set<UUID> mineLiked = new HashSet<>(likes.likedAmong(viewer.getId(), ids));
        Map<UUID, Long> commentCounts = new HashMap<>();
        for (Object[] row : comments.counts(ids)) commentCounts.put((UUID) row[0], (Long) row[1]);
        Map<UUID, List<CommentSnippet>> samples = sampleComments(ids, viewer);
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
                    applied.get(p.getId()), mineIds.contains(p.getId()) ? applicants.getOrDefault(p.getId(), 0L) : null, files.getOrDefault(p.getId(), List.of()),
                    likeCounts.getOrDefault(p.getId(), 0L), mineLiked.contains(p.getId()),
                    commentCounts.getOrDefault(p.getId(), 0L), p.isCommentsOn() ? samples.getOrDefault(p.getId(), List.of()) : List.of());
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

    static final int SAMPLE = 8, SAMPLE_CHARS = 140;

    /** The newest few comments per post (never from someone blocked either way), for the feed's fading preview. One query for the comments, one for who wrote them. */
    private Map<UUID, List<CommentSnippet>> sampleComments(Set<UUID> postIds, User viewer) {
        Set<UUID> hidden = new HashSet<>(blocks.counterpartsOf(viewer.getId()));
        Map<UUID, List<PostComment>> picked = new HashMap<>();
        for (PostComment c : comments.recent(postIds, PageRequest.of(0, Math.min(300, postIds.size() * 12)))) {
            if (hidden.contains(c.getAuthorId())) continue;
            List<PostComment> l = picked.computeIfAbsent(c.getPostId(), k -> new ArrayList<>());
            if (l.size() < SAMPLE) l.add(c);
        }
        Map<UUID, User> who = users.findAllById(picked.values().stream().flatMap(List::stream).map(PostComment::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, List<CommentSnippet>> out = new HashMap<>();
        picked.forEach((postId, list) -> out.put(postId, list.stream().filter(c -> who.containsKey(c.getAuthorId()))
                .map(c -> new CommentSnippet(who.get(c.getAuthorId()).getUsername(),
                        c.getBody().length() > SAMPLE_CHARS ? c.getBody().substring(0, SAMPLE_CHARS) + "…" : c.getBody())).toList()));
        return out;
    }

    /** Like a post: free, silent, idempotent. Your own post too. */
    public PostResponse like(User me, UUID id) {
        Post p = visible(me, id);
        if (!likes.existsByPostIdAndUserId(id, me.getId())) likes.save(new PostLike(id, me.getId()));
        return view(p, me);
    }

    public PostResponse unlike(User me, UUID id) {
        Post p = visible(me, id);
        likes.deleteByPostIdAndUserId(id, me.getId());
        return view(p, me);
    }

    static final int MAX_SHARE = 20;

    /** Yarns a post you can see to the people you pick; returns how many it reached. A post with a limited audience is only the author's to share. */
    public int share(User me, UUID id, List<String> usernames) {
        Post p = visible(me, id);
        if (!"EVERYONE".equals(p.getAudience()) && !p.getAuthorId().equals(me.getId())) {
            throw new InvalidProfileException("Only the author can share a post that has a limited audience.");
        }
        List<String> names = DraftService.names(usernames);
        if (names.isEmpty()) throw new InvalidProfileException("Pick at least one person.");
        if (names.size() > MAX_SHARE) throw new InvalidProfileException("Share with up to " + MAX_SHARE + " people at a time.");
        String note = me.getUsername() + " shared a post with you: \"" + p.getTitle() + "\"\n/HTML-pages/post-view.html?id=" + p.getId();
        int delivered = 0;
        for (String name : names) {
            User to = users.findByUsername(name).orElse(null);
            if (to != null && !to.getId().equals(me.getId()) && yarns.tryShare(me, to, note)) delivered++;
        }
        return delivered;
    }

    private Post mine(User me, UUID id) {
        return posts.findById(id).filter(p -> p.getAuthorId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("That post is gone."));
    }

    private static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
}
