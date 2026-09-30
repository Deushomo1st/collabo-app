package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.LinkDto;
import com.collabo.backend.dto.ProfileResponse;
import com.collabo.backend.dto.UpdateProfileRequest;
import com.collabo.backend.entity.UserAvatar;
import com.collabo.backend.dto.CredentialsResponse;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.service.CredentialService;
import com.collabo.backend.service.AvatarService;
import com.collabo.backend.service.FollowService;
import com.collabo.backend.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Profiles: read anyone's, edit your own. Thin shell over ProfileService. Sign-in required (default rule). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ProfileService profiles;
    private final AvatarService avatars;
    private final FollowService follows;
    private final CredentialService credentials;
    private final CurrentUser current;

    public UserController(ProfileService profiles, AvatarService avatars, FollowService follows,
                          CredentialService credentials, CurrentUser current) {
        this.credentials = credentials;
        this.follows = follows;
        this.avatars = avatars;
        this.profiles = profiles;
        this.current = current;
    }

    @GetMapping("/me")
    public ProfileResponse me() {
        var me = current.require();
        return profiles.view(me.getUsername(), me);
    }

    @PatchMapping("/me")
    public ProfileResponse update(@RequestBody UpdateProfileRequest req) {
        return profiles.update(current.require(), req);
    }

    @PutMapping("/me/links")
    public ProfileResponse replaceLinks(@RequestBody List<LinkDto> links) {
        return profiles.replaceLinks(current.require(), links);
    }

    /** Raw JPEG bytes as the body. Read with a cap so an oversized upload is never held in memory whole. */
    @PutMapping(value = "/me/avatar", consumes = "image/jpeg")
    public ProfileResponse setAvatar(HttpServletRequest request) throws IOException {
        var me = current.require();
        byte[] bytes = request.getInputStream().readNBytes(AvatarService.MAX_BYTES + 1);
        avatars.save(me, bytes);
        return profiles.view(me.getUsername(), me);
    }

    @DeleteMapping("/me/avatar")
    public ProfileResponse removeAvatar() {
        var me = current.require();
        avatars.remove(me);
        return profiles.view(me.getUsername(), me);
    }

    @GetMapping("/{username}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable String username) {
        current.require();
        UserAvatar a = avatars.of(username);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .eTag("\"" + a.getUpdatedAt().toEpochMilli() + "\"")
                .body(a.getImage());
    }

    @PutMapping("/{username}/follow")
    public ProfileResponse follow(@PathVariable String username) {
        var me = current.require();
        follows.follow(me, username);
        return profiles.view(username, me);
    }

    @DeleteMapping("/{username}/follow")
    public ProfileResponse unfollow(@PathVariable String username) {
        var me = current.require();
        follows.unfollow(me, username);
        return profiles.view(username, me);
    }

    @GetMapping("/{username}/followers")
    public List<PersonDto> followers(@PathVariable String username) {
        return follows.followers(username, current.require());
    }

    @GetMapping("/{username}/following")
    public List<PersonDto> following(@PathVariable String username) {
        return follows.following(username, current.require());
    }

    @GetMapping("/{username}/credentials")
    public CredentialsResponse credentials(@PathVariable String username) {
        return credentials.view(username, current.require());
    }

    @GetMapping("/{username}")
    public ProfileResponse view(@PathVariable String username) {
        return profiles.view(username, current.require());
    }
}
