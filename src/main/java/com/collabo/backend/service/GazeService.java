package com.collabo.backend.service;

import com.collabo.backend.dto.PostDtos.FeedPage;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.Shout;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.FollowRepository;
import com.collabo.backend.repository.PostRepository;
import com.collabo.backend.repository.ShoutRepository;
import com.collabo.backend.repository.UserBlockRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Gaze is discovery-led: newest first, never by engagement, so a first-time poster with no followers gets a real chance.
 * Shared Gaze is network only: posts and shout-outs from the people you follow and the people who follow you.
 * Profile tabs (Posts, Reposts) come from here too since they are the same paging over the same rows.
 */
@Service
@Transactional(readOnly = true)
public class GazeService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    /** One line of a feed: a post, when it surfaced, and (for a shout-out) who surfaced it. */
    private record Entry(Post post, Instant at, UUID shouter) {}

    private final PostRepository posts;
    private final ShoutRepository shouts;
    private final FollowRepository follows;
    private final UserBlockRepository blocks;
    private final UserRepository users;
    private final PostService postService;

    public GazeService(PostRepository posts, ShoutRepository shouts, FollowRepository follows, UserBlockRepository blocks,
                       UserRepository users, PostService postService) {
        this.posts = posts; this.shouts = shouts; this.follows = follows; this.blocks = blocks;
        this.users = users; this.postService = postService;
    }

    /**
     * @param feed    "gaze" (default) or "shared"
     * @param before  cursor: only entries older than this (the previous page's `next`); null = from the top
     */
    public FeedPage feed(User viewer, String feed, boolean pendingOnly, Instant before, Integer limit) {
        int n = limit(limit);
        Instant cursor = cursor(before);
        Instant now = Instant.now();
        Set<UUID> hidden = hiddenFor(viewer);
        PageRequest page = PageRequest.of(0, n + 1);   // one extra row tells us whether there is a next page

        List<Entry> entries;
        switch (feed == null || feed.isBlank() ? "gaze" : feed) {
            case "gaze" -> entries = ofPosts(posts.gaze(cursor, hidden, pendingOnly, now, page));
            case "shared" -> {
                Set<UUID> network = new HashSet<>(follows.networkOf(viewer.getId()));
                network.removeAll(hidden);
                entries = network.isEmpty() ? List.of() : merge(
                        ofPosts(posts.network(cursor, network, pendingOnly, now, page)),
                        ofShouts(shouts.byNetwork(cursor, network, hidden, pendingOnly, now, page)));
            }
            default -> throw new InvalidProfileException("Unknown feed.");
        }
        return page(entries, n, viewer);
    }

    /** A person's profile tabs: "posts" (default) or "reposts". A block either way makes the person not found. */
    public FeedPage profile(User viewer, String username, String tab, Instant before, Integer limit) {
        User owner = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No one by that name."));
        Set<UUID> hidden = hiddenFor(viewer);
        if (hidden.contains(owner.getId()) && !owner.getId().equals(viewer.getId())) throw new ResourceNotFoundException("No one by that name.");
        hidden.remove(viewer.getId());   // your own profile shows your own posts and reposts
        int n = limit(limit);
        Instant cursor = cursor(before);
        PageRequest page = PageRequest.of(0, n + 1);
        List<Entry> entries;
        switch (tab == null || tab.isBlank() ? "posts" : tab) {
            case "posts" -> entries = ofPosts(posts.byAuthor(owner.getId(), cursor, page));
            case "reposts" -> entries = ofShouts(shouts.byUser(owner.getId(), cursor, hidden, page));
            default -> throw new InvalidProfileException("Unknown tab.");
        }
        return page(entries, n, viewer);
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private static int limit(Integer limit) { return limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT)); }

    private static Instant cursor(Instant before) { return before == null ? Instant.now().plusSeconds(60) : before; }

    /** Blocked people plus the viewer, plus a sentinel so the set is never empty (JPQL "not in ()" is unsafe). */
    private Set<UUID> hiddenFor(User viewer) {
        Set<UUID> hidden = new HashSet<>(blocks.counterpartsOf(viewer.getId()));
        hidden.add(viewer.getId());
        hidden.add(new UUID(0, 0));
        return hidden;
    }

    private static List<Entry> ofPosts(List<Post> ps) {
        return ps.stream().map(p -> new Entry(p, p.getCreatedAt(), null)).toList();
    }

    private List<Entry> ofShouts(List<Shout> rows) {
        Map<UUID, Post> byId = posts.findAllById(rows.stream().map(Shout::getPostId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Post::getId, Function.identity()));
        return rows.stream().filter(s -> byId.containsKey(s.getPostId()))
                .map(s -> new Entry(byId.get(s.getPostId()), s.getCreatedAt(), s.getUserId())).toList();
    }

    /** Newest first; a post that surfaced twice shows once, at its latest moment. */
    private static List<Entry> merge(List<Entry> a, List<Entry> b) {
        List<Entry> all = new ArrayList<>(a);
        all.addAll(b);
        all.sort(Comparator.comparing(Entry::at).reversed());
        Set<UUID> seen = new HashSet<>();
        return all.stream().filter(e -> seen.add(e.post().getId())).toList();
    }

    // ponytail: cursor is the entry time alone; two entries in the same microsecond could straddle a page. Add an id tiebreak if it ever happens.
    private FeedPage page(List<Entry> entries, int n, User viewer) {
        boolean more = entries.size() > n;
        List<Entry> shown = more ? entries.subList(0, n) : entries;
        Map<UUID, User> shouters = users.findAllById(shown.stream().map(Entry::shouter).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, User> byPost = new HashMap<>();
        shown.stream().filter(e -> e.shouter() != null).forEach(e -> byPost.put(e.post().getId(), shouters.get(e.shouter())));
        return new FeedPage(postService.present(shown.stream().map(Entry::post).toList(), viewer, byPost),
                more ? shown.get(shown.size() - 1).at() : null);
    }
}
