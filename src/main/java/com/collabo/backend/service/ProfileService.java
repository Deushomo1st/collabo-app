package com.collabo.backend.service;

import com.collabo.backend.dto.ProfileResponse;
import com.collabo.backend.dto.UpdateProfileRequest;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/** Profile fields: preferred title, bio (60 words), credentials privacy. Links, picture and follows come in later checkpoints. */
@Service
@Transactional
public class ProfileService {

    static final int MAX_BIO_WORDS = 60;
    static final int MAX_BIO_CHARS = 600;
    static final int MAX_TITLE_CHARS = 40;
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");

    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ProfileResponse view(String username, User viewer) {
        User user = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No one has that username."));
        return ProfileResponse.of(user, user.getId().equals(viewer.getId()));
    }

    public ProfileResponse update(User me, UpdateProfileRequest req) {
        User user = users.findById(me.getId()).orElseThrow();   // fresh copy: `me` came from the request's session
        if (req.preferredTitle() != null) user.setPreferredTitle(cleanTitle(req.preferredTitle()));
        if (req.bio() != null) user.setBio(cleanBio(req.bio()));
        if (req.credentialsPrivacy() != null) user.setCredentialsPrivacy(req.credentialsPrivacy());
        return ProfileResponse.of(users.save(user), true);
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
