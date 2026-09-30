package com.collabo.backend.service;

import com.collabo.backend.dto.DraftDtos.DraftRequest;
import com.collabo.backend.dto.DraftDtos.DraftResponse;
import com.collabo.backend.dto.PostDtos.MediaDto;
import com.collabo.backend.entity.Media;
import com.collabo.backend.entity.PostDraft;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.PostDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Posts saved for later. Private to their author; uploads in a draft stay unposted until the draft is published. */
@Service
@Transactional
public class DraftService {

    static final int MAX_DRAFTS = 25;
    static final int MAX_SHARE = 20;

    private final PostDraftRepository drafts;
    private final MediaService media;

    public DraftService(PostDraftRepository drafts, MediaService media) {
        this.drafts = drafts; this.media = media;
    }

    @Transactional(readOnly = true)
    public List<DraftResponse> list(User me) {
        return drafts.findByAuthorIdOrderByUpdatedAtDesc(me.getId()).stream().map(d -> view(me, d)).toList();
    }

    @Transactional(readOnly = true)
    public DraftResponse get(User me, UUID id) { return view(me, mine(me, id)); }

    /** Creates the draft, or replaces the one named by id. Needs at least a word, a hashtag or a file, so empty drafts do not pile up. */
    public DraftResponse save(User me, DraftRequest r) {
        String title = clean(r.title()), body = clean(r.body());
        if (title != null && title.length() > PostService.MAX_TITLE) throw new InvalidProfileException("Keep the title under " + PostService.MAX_TITLE + " characters.");
        if (body != null && body.length() > PostService.MAX_BODY) throw new InvalidProfileException("Keep the description under " + PostService.MAX_BODY + " characters.");
        List<String> tags = Hashtags.clean(r.hashtags());
        List<Media> files = media.mine(me, r.mediaIds());
        List<String> share = names(r.shareWith());
        if (title == null && body == null && tags.isEmpty() && files.isEmpty()) throw new InvalidProfileException("Write something before saving a draft.");

        PostDraft d;
        if (r.id() == null) {
            if (drafts.countByAuthorId(me.getId()) >= MAX_DRAFTS) throw new InvalidProfileException("You have " + MAX_DRAFTS + " drafts. Finish or delete one first.");
            d = new PostDraft(me.getId());
        } else {
            d = mine(me, r.id());
            Set<UUID> keep = new HashSet<>(); files.forEach(m -> keep.add(m.getId()));
            media.discardAll(me, ids(d.getMediaIds()).stream().filter(x -> !keep.contains(x)).toList());   // files you took out of the draft
        }
        d.fill(title, body, r.applyBy(), tags.isEmpty() ? null : String.join(" ", tags),
                files.isEmpty() ? null : String.join(",", files.stream().map(m -> m.getId().toString()).toList()),
                share.isEmpty() ? null : String.join(",", share), !Boolean.FALSE.equals(r.commentsOn()), !Boolean.FALSE.equals(r.shoutsOn()));
        return view(me, drafts.save(d));
    }

    public void delete(User me, UUID id) {
        PostDraft d = mine(me, id);
        media.discardAll(me, ids(d.getMediaIds()));
        drafts.delete(d);
    }

    /** The draft became a post: it goes, its files stay (they are on the post now). */
    void consume(User me, UUID id) {
        if (id != null) drafts.findById(id).filter(d -> d.getAuthorId().equals(me.getId())).ifPresent(drafts::delete);
    }

    static List<String> names(List<String> raw) {
        if (raw == null) return List.of();
        List<String> out = raw.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (out.size() > MAX_SHARE) throw new InvalidProfileException("Share with at most " + MAX_SHARE + " people at once.");
        return out;
    }

    private PostDraft mine(User me, UUID id) {
        return drafts.findById(id).filter(d -> d.getAuthorId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("That draft is gone."));
    }

    private DraftResponse view(User me, PostDraft d) {
        List<MediaDto> files = media.ofDraft(me, ids(d.getMediaIds())).stream().map(m -> new MediaDto(m.getId(), m.kind())).toList();
        return new DraftResponse(d.getId(), d.getTitle(), d.getBody(), d.getApplyBy(), split(d.getHashtags(), " "), files,
                d.isCommentsOn(), d.isShoutsOn(), split(d.getShareWith(), ","), d.getUpdatedAt());
    }

    private static List<UUID> ids(String csv) { return split(csv, ",").stream().map(UUID::fromString).toList(); }
    private static List<String> split(String s, String by) { return s == null || s.isBlank() ? List.of() : List.of(s.split(by)); }
    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
