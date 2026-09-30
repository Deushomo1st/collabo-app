package com.collabo.backend.service;

import com.collabo.backend.entity.Media;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.MediaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pictures and videos on posts. The bytes go to a folder (app.media.dir, a docker volume in production), one file per media id;
 * the type is checked against the file's own first bytes, not just what the browser claimed.
 */
@Service
@Transactional
public class MediaService {

    static final long MAX_IMAGE = 5L * 1024 * 1024;
    static final long MAX_VIDEO = 30L * 1024 * 1024;
    static final int MAX_PER_POST = 6;
    static final int MAX_UNATTACHED = 40;   // ponytail: a cap, not a cleanup job; add a sweep for old unattached uploads if disk fills
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "video/mp4", "video/webm");

    private final MediaRepository media;
    private final Path dir;

    public MediaService(MediaRepository media, @Value("${app.media.dir:./media}") String dir) {
        this.media = media; this.dir = Path.of(dir).toAbsolutePath();
    }

    /** Streams the upload to disk, refusing anything over the limit or not really what it says it is. */
    public Media store(User me, String declared, InputStream in) {
        String type = declared == null ? "" : declared.split(";")[0].trim().toLowerCase();
        if (!TYPES.contains(type)) throw new InvalidProfileException("Attach a JPEG, PNG, GIF or WebP picture, or an MP4 or WebM video.");
        if (media.countByOwnerIdAndPostIdIsNull(me.getId()) >= MAX_UNATTACHED) throw new InvalidProfileException("You have too many unposted uploads. Post or remove some first.");
        long limit = type.startsWith("video/") ? MAX_VIDEO : MAX_IMAGE;
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, "up", ".part");
            long size = copy(in, tmp, limit);
            if (size == 0) throw new InvalidProfileException("That file is empty.");
            if (!sniff(tmp, type)) throw new InvalidProfileException("That file is not really a " + type.substring(type.indexOf('/') + 1) + ".");
            Media saved = media.save(new Media(me.getId(), type, size));
            Files.move(tmp, file(saved.getId()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return saved;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) { /* already moved */ }
        }
    }

    @Transactional(readOnly = true)
    public Media find(UUID id) {
        return media.findById(id).orElseThrow(() -> new ResourceNotFoundException("That file is gone."));
    }

    public Path file(UUID id) { return dir.resolve(id.toString()); }

    /** Removes an upload that is not on a post yet. */
    public void discard(User me, UUID id) {
        Media m = media.findById(id).filter(x -> x.getOwnerId().equals(me.getId()) && x.getPostId() == null)
                .orElseThrow(() -> new ResourceNotFoundException("That file is gone."));
        remove(m);
    }

    /** Checks the ids are the caller's own unposted uploads (in order, no repeats, at most 6) and returns them. */
    List<Media> mine(User me, List<UUID> ids) {
        List<UUID> want = ids == null ? List.of() : ids.stream().distinct().toList();
        if (want.size() > MAX_PER_POST) throw new InvalidProfileException("Attach at most " + MAX_PER_POST + " files.");
        Map<UUID, Media> found = media.findByOwnerIdAndIdIn(me.getId(), want).stream().filter(m -> m.getPostId() == null)
                .collect(Collectors.toMap(Media::getId, m -> m));
        if (found.size() != want.size()) throw new InvalidProfileException("One of the attached files is gone. Attach it again.");
        return want.stream().map(found::get).toList();
    }

    void attach(List<Media> items, UUID postId) { items.forEach(m -> m.setPostId(postId)); media.saveAll(items); }

    /** Uploads named in a draft that is being thrown away. Unknown or already-posted ids are ignored. */
    void discardAll(User me, Collection<UUID> ids) {
        media.findByOwnerIdAndIdIn(me.getId(), ids).stream().filter(m -> m.getPostId() == null).forEach(this::remove);
    }

    void removeOfPost(UUID postId) { media.findByPostId(postId).forEach(this::remove); }

    Map<UUID, List<Media>> ofPosts(Collection<UUID> postIds) {
        return media.findByPostIdIn(postIds).stream().sorted(Comparator.comparing(Media::getCreatedAt))
                .collect(Collectors.groupingBy(Media::getPostId));
    }

    /** Media for drafts: the owner's own unposted uploads among these ids, in the order asked. */
    List<Media> ofDraft(User me, List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        Map<UUID, Media> found = media.findByOwnerIdAndIdIn(me.getId(), ids).stream().filter(m -> m.getPostId() == null)
                .collect(Collectors.toMap(Media::getId, m -> m));
        return ids.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    private void remove(Media m) {
        media.delete(m);
        try { Files.deleteIfExists(file(m.getId())); } catch (IOException ignored) { /* the row is gone; a stray file is harmless */ }
    }

    private static long copy(InputStream in, Path to, long limit) throws IOException {
        long total = 0;
        byte[] buf = new byte[64 * 1024];
        try (OutputStream out = Files.newOutputStream(to)) {
            for (int n; (n = in.read(buf)) > 0; ) {
                total += n;
                if (total > limit) throw new InvalidProfileException("That file is too big (the limit is " + (limit >> 20) + " MB).");
                out.write(buf, 0, n);
            }
        }
        return total;
    }

    /** Does the start of the file match the type it claims? */
    static boolean sniff(Path file, String type) throws IOException {
        byte[] b = new byte[12];
        int n;
        try (InputStream in = Files.newInputStream(file)) { n = in.readNBytes(b, 0, 12); }
        if (n < 12) return false;
        return switch (type) {
            case "image/jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "image/png" -> (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/gif" -> b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8';
            case "image/webp" -> b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            case "video/mp4" -> b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
            case "video/webm" -> (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3;
            default -> false;
        };
    }
}
