package com.collabo.backend.service;

import com.collabo.backend.entity.User;
import com.collabo.backend.entity.UserAvatar;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.UserAvatarRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/** Profile pictures: one JPEG per user, at most 300 KB, checked by actually decoding it. */
@Service
@Transactional
public class AvatarService {

    public static final int MAX_BYTES = 300 * 1024;
    static final int MAX_SIDE = 2000;   // a crop is 640x480; this only stops absurd pixel bombs

    private final UserAvatarRepository avatars;
    private final UserRepository users;

    public AvatarService(UserAvatarRepository avatars, UserRepository users) {
        this.avatars = avatars;
        this.users = users;
    }

    public void save(User me, byte[] bytes) {
        checkJpeg(bytes);
        avatars.findById(me.getId()).ifPresentOrElse(
                a -> { a.replace(bytes); avatars.save(a); },
                () -> avatars.save(new UserAvatar(me.getId(), bytes)));
    }

    public void remove(User me) {
        avatars.deleteById(me.getId());
    }

    @Transactional(readOnly = true)
    public UserAvatar of(String username) {
        User user = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No one has that username."));
        return avatars.findById(user.getId()).orElseThrow(() -> new ResourceNotFoundException("No picture."));
    }

    /** Version stamp for cache-busting the picture URL; null when there is no picture. */
    @Transactional(readOnly = true)
    public Long versionOf(User user) {
        return avatars.findUpdatedAt(user.getId()).map(java.time.Instant::toEpochMilli).orElse(null);
    }

    static void checkJpeg(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new InvalidProfileException("Send a picture.");
        if (bytes.length > MAX_BYTES) throw new InvalidProfileException("The picture can be at most 300 KB.");
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw notJpeg();
            ImageReader reader = readers.next();
            try {
                if (!"jpeg".equalsIgnoreCase(reader.getFormatName())) throw notJpeg();
                reader.setInput(in);
                if (reader.getWidth(0) > MAX_SIDE || reader.getHeight(0) > MAX_SIDE) throw new InvalidProfileException("The picture is too large in pixels.");
                reader.read(0);   // really decode it: a JPEG header with garbage after it fails here
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidProfileException ipe) throw ipe;
            throw notJpeg();
        }
    }

    private static InvalidProfileException notJpeg() {
        return new InvalidProfileException("The picture must be a JPEG.");
    }
}
