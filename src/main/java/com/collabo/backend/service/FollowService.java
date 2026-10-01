package com.collabo.backend.service;

import com.collabo.backend.dto.ConnectionsDto;
import com.collabo.backend.dto.FollowState;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.entity.Follow;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.exception.YarnException;
import com.collabo.backend.repository.FollowRepository;
import com.collabo.backend.repository.UserBlockRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Follow, unfollow, counts and lists. Following is the base of the "who sees my credentials" settings. */
@Service
@Transactional
public class FollowService {

    /** Same words whichever side blocked, so a block is never revealed. */
    static final String CANT_FOLLOW = "You can't follow this person.";

    private final FollowRepository follows;
    private final UserBlockRepository blocks;
    private final UserRepository users;

    public FollowService(FollowRepository follows, UserBlockRepository blocks, UserRepository users) {
        this.follows = follows; this.blocks = blocks; this.users = users;
    }

    public void follow(User me, String username) {
        User target = find(username);
        if (target.getId().equals(me.getId())) throw new InvalidProfileException("You can't follow yourself.");
        if (blocked(me.getId(), target.getId())) throw new YarnException(HttpStatus.FORBIDDEN, CANT_FOLLOW);
        if (!follows.existsByFollowerIdAndFollowedId(me.getId(), target.getId())) follows.save(new Follow(me.getId(), target.getId()));
    }

    /** Silent: unfollowing someone you don't follow is fine. */
    public void unfollow(User me, String username) {
        follows.deleteByFollowerIdAndFollowedId(me.getId(), find(username).getId());
    }

    /** Called when someone blocks someone: any follow between them, either way, ends. */
    public void endBetween(UUID a, UUID b) {
        follows.deleteByFollowerIdAndFollowedId(a, b);
        follows.deleteByFollowerIdAndFollowedId(b, a);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(UUID follower, UUID followed) { return follows.existsByFollowerIdAndFollowedId(follower, followed); }

    public FollowState stateFor(User profile, User viewer) {
        UUID p = profile.getId(), v = viewer.getId();
        boolean self = p.equals(v);
        return new FollowState(follows.countByFollowedId(p), follows.countByFollowerId(p),
                !self && follows.existsByFollowerIdAndFollowedId(v, p),
                !self && follows.existsByFollowerIdAndFollowedId(p, v),
                !self && !blocked(v, p));
    }

    @Transactional(readOnly = true)
    public List<PersonDto> followers(String username, User viewer) {
        return people(follows.findTop50ByFollowedIdOrderByCreatedAtDesc(find(username).getId()), Follow::getFollowerId, viewer);
    }

    @Transactional(readOnly = true)
    public List<PersonDto> following(String username, User viewer) {
        return people(follows.findTop50ByFollowerIdOrderByCreatedAtDesc(find(username).getId()), Follow::getFollowedId, viewer);
    }

    /** Up to 500 each way; MyGuy is whoever is in both lists. */
    @Transactional(readOnly = true)
    public ConnectionsDto connections(String username, User viewer) {
        UUID id = find(username).getId();
        var in = people(follows.findTop500ByFollowedIdOrderByCreatedAtDesc(id), Follow::getFollowerId, viewer);
        var out = people(follows.findTop500ByFollowerIdOrderByCreatedAtDesc(id), Follow::getFollowedId, viewer);
        var inNames = new HashSet<String>(); in.forEach(p -> inNames.add(p.username()));
        return new ConnectionsDto(in, out, out.stream().filter(p -> inNames.contains(p.username())).toList());
    }

    /** Newest first; anyone who blocked the viewer is left out. */
    private List<PersonDto> people(List<Follow> rows, Function<Follow, UUID> who, User viewer) {
        Map<UUID, User> byId = users.findAllById(rows.stream().map(who).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().map(who).filter(byId::containsKey)
                .filter(id -> !blocks.existsByBlockerIdAndBlockedId(id, viewer.getId()))
                .map(id -> PersonDto.of(byId.get(id))).toList();
    }

    private boolean blocked(UUID a, UUID b) {
        return blocks.existsByBlockerIdAndBlockedId(a, b) || blocks.existsByBlockerIdAndBlockedId(b, a);
    }

    private User find(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No one has that username."));
    }
}
