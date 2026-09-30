package com.collabo.backend.service;

import com.collabo.backend.dto.PostDtos.FeedPage;
import com.collabo.backend.dto.PostDtos.PostResponse;
import com.collabo.backend.entity.Post;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.repository.FollowRepository;
import com.collabo.backend.repository.PostRepository;
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
 * Shared Gaze is network only: posts from the people you follow and the people who follow you.
 */
@Service
@Transactional(readOnly = true)
public class GazeService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;

    private final PostRepository posts;
    private final FollowRepository follows;
    private final UserBlockRepository blocks;
    private final UserRepository users;

    public GazeService(PostRepository posts, FollowRepository follows, UserBlockRepository blocks, UserRepository users) {
        this.posts = posts; this.follows = follows; this.blocks = blocks; this.users = users;
    }

    /**
     * @param feed    "gaze" (default) or "shared"
     * @param before  cursor: only posts older than this (the previous page's `next`); null = from the top
     */
    public FeedPage feed(User viewer, String feed, boolean pendingOnly, Instant before, Integer limit) {
        int n = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        Instant cursor = before == null ? Instant.now().plusSeconds(60) : before;
        Instant now = Instant.now();
        Set<UUID> hidden = new HashSet<>(blocks.counterpartsOf(viewer.getId()));
        PageRequest page = PageRequest.of(0, n + 1);   // one extra row tells us whether there is a next page

        List<Post> rows;
        switch (feed == null || feed.isBlank() ? "gaze" : feed) {
            case "gaze" -> {
                hidden.add(viewer.getId());   // never empty (JPQL "not in ()" is unsafe); the viewer's own posts live on their profile
                hidden.add(new UUID(0, 0));
                rows = posts.gaze(cursor, hidden, pendingOnly, now, page);
            }
            case "shared" -> {
                Set<UUID> network = new HashSet<>(follows.networkOf(viewer.getId()));
                network.removeAll(hidden);
                network.remove(viewer.getId());
                rows = network.isEmpty() ? List.of() : posts.network(cursor, network, pendingOnly, now, page);
            }
            default -> throw new InvalidProfileException("Unknown feed.");
        }
        // ponytail: cursor is createdAt alone; two posts in the same microsecond could straddle a page. Add an id tiebreak if it ever happens.
        boolean more = rows.size() > n;
        List<Post> shown = more ? rows.subList(0, n) : rows;
        Map<UUID, User> authors = users.findAllById(shown.stream().map(Post::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<PostResponse> items = shown.stream().map(p -> PostResponse.of(p, authors.get(p.getAuthorId()), viewer)).toList();
        return new FeedPage(items, more ? shown.get(shown.size() - 1).getCreatedAt() : null);
    }
}
