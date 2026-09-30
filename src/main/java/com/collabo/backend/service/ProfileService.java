package com.collabo.backend.service;

import com.collabo.backend.dto.LinkDto;
import com.collabo.backend.dto.ProfileResponse;
import com.collabo.backend.dto.UpdateProfileRequest;
import com.collabo.backend.entity.ProfileLink;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.ProfileLinkRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Profile fields: preferred title, bio (60 words), credentials privacy. Links, picture and follows come in later checkpoints. */
@Service
@Transactional
public class ProfileService {

    static final int MAX_BIO_WORDS = 60;
    static final int MAX_BIO_CHARS = 600;
    static final int MAX_TITLE_CHARS = 40;
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");

    static final int MAX_LINKS = 12;
    static final int MAX_LINK_TITLE = 60;
    static final int MAX_LINK_URL = 500;
    static final int MAX_LINK_NOTE = 120;

    private final UserRepository users;
    private final ProfileLinkRepository links;
    private final AvatarService avatars;
    private final FollowService follows;

    public ProfileService(UserRepository users, ProfileLinkRepository links, AvatarService avatars, FollowService follows) {
        this.avatars = avatars;
        this.follows = follows;
        this.users = users;
        this.links = links;
    }

    @Transactional(readOnly = true)
    public ProfileResponse view(String username, User viewer) {
        User user = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No one has that username."));
        return ProfileResponse.of(user, user.getId().equals(viewer.getId()), linksOf(user), avatars.versionOf(user), follows.stateFor(user, viewer));
    }

    public ProfileResponse update(User me, UpdateProfileRequest req) {
        User user = users.findById(me.getId()).orElseThrow();   // fresh copy: `me` came from the request's session
        if (req.preferredTitle() != null) user.setPreferredTitle(cleanTitle(req.preferredTitle()));
        if (req.bio() != null) user.setBio(cleanBio(req.bio()));
        if (req.credentialsPrivacy() != null) user.setCredentialsPrivacy(req.credentialsPrivacy());
        return ProfileResponse.of(users.save(user), true, linksOf(user), avatars.versionOf(user), follows.stateFor(user, user));
    }

    /** Replaces the whole list; the order sent is the order shown. All-or-nothing: one bad entry rejects the lot. */
    public ProfileResponse replaceLinks(User me, List<LinkDto> incoming) {
        if (incoming == null) throw new InvalidProfileException("Send a list of links.");
        if (incoming.size() > MAX_LINKS) throw new InvalidProfileException("You can add at most " + MAX_LINKS + " links.");
        List<ProfileLink> fresh = new ArrayList<>();
        for (LinkDto l : incoming) {
            if (l == null) throw new InvalidProfileException("A link is empty.");
            fresh.add(new ProfileLink(me.getId(), cleanLinkTitle(l.title()), cleanUrl(l.url()), cleanNote(l.note()), fresh.size()));
        }
        links.deleteByUserId(me.getId());
        links.saveAll(fresh);
        return view(me.getUsername(), me);
    }

    private List<LinkDto> linksOf(User user) {
        return links.findByUserIdOrderByPositionAsc(user.getId()).stream().map(LinkDto::of).toList();
    }

    static String cleanLinkTitle(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) throw new InvalidProfileException("Every link needs a title.");
        if (t.length() > MAX_LINK_TITLE) throw new InvalidProfileException("A link title can be at most " + MAX_LINK_TITLE + " characters.");
        if (CONTROL.matcher(t).find() || t.contains("\n")) throw new InvalidProfileException("A link title can't contain line breaks or control characters.");
        return t;
    }

    static String cleanNote(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.length() > MAX_LINK_NOTE) throw new InvalidProfileException("A link note can be at most " + MAX_LINK_NOTE + " characters.");
        if (CONTROL.matcher(n).find() || n.contains("\n")) throw new InvalidProfileException("A link note can't contain line breaks or control characters.");
        return n;
    }

    /** http or https with a host, nothing else (no javascript:, data:, file:). */
    static String cleanUrl(String raw) {
        String u = raw == null ? "" : raw.trim();
        if (u.length() > MAX_LINK_URL) throw new InvalidProfileException("A link address is too long.");
        try {
            URI uri = new URI(u);
            String scheme = uri.getScheme();
            boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!web || uri.getHost() == null || CONTROL.matcher(u).find()) throw new URISyntaxException(u, "not a web address");
        } catch (URISyntaxException e) {
            throw new InvalidProfileException("Link addresses must start with http:// or https://.");
        }
        return u;
    }

    static String cleanTitle(String raw) {
        String title = raw.trim();
        if (title.length() > MAX_TITLE_CHARS) throw new InvalidProfileException("Your title can be at most " + MAX_TITLE_CHARS + " characters.");
        if (CONTROL.matcher(title).find() || title.contains("\n")) throw new InvalidProfileException("Your title can't contain line breaks or control characters.");
        return title;
    }

    static String cleanBio(String raw) {
        String bio = raw.trim();
        if (CONTROL.matcher(bio).find()) throw new InvalidProfileException("Your bio contains characters that can't be used.");
        if (bio.length() > MAX_BIO_CHARS) throw new InvalidProfileException("Your bio is too long.");
        if (!bio.isEmpty() && bio.split("\\s+").length > MAX_BIO_WORDS) {
            throw new InvalidProfileException("Your bio can be at most " + MAX_BIO_WORDS + " words.");
        }
        return bio;
    }
}
