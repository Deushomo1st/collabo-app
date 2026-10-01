package com.collabo.backend.service;

import com.collabo.backend.dto.PostDtos.MediaDto;
import com.collabo.backend.dto.ReportDtos.AdminView;
import com.collabo.backend.entity.Media;
import com.collabo.backend.entity.ProblemReport;
import com.collabo.backend.entity.User;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.ProblemReportRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The Report page: a write-up plus up to three screenshots or videos, for the admin console. */
@Service
@Transactional
public class ReportService {

    static final int MIN_SUMMARY = 10, MAX_SUMMARY = 2000, MAX_FILES = 3, PER_HOUR = 5, LIST_LIMIT = 200;

    private final ProblemReportRepository reports;
    private final UserRepository users;
    private final MediaService media;

    public ReportService(ProblemReportRepository reports, UserRepository users, MediaService media) { this.reports = reports; this.users = users; this.media = media; }

    public ProblemReport submit(User me, String summary, String pageUrl, List<UUID> mediaIds, boolean anonymous) {
        String text = summary == null ? "" : summary.trim();
        if (text.length() < MIN_SUMMARY) throw new InvalidProfileException("Tell us a little more: say what went wrong in a sentence or two.");
        if (text.length() > MAX_SUMMARY) throw new InvalidProfileException("Keep the write-up under " + MAX_SUMMARY + " characters.");
        if (mediaIds != null && mediaIds.stream().distinct().count() > MAX_FILES) throw new InvalidProfileException("Attach at most " + MAX_FILES + " files.");
        if (reports.countByReporterIdAndCreatedAtAfter(me.getId(), Instant.now().minus(Duration.ofHours(1))) >= PER_HOUR)
            throw new InvalidProfileException("You have sent several reports in the last hour. Give us a little time to read them.");
        List<Media> files = media.mine(me, mediaIds);   // the caller's own unposted uploads, or it refuses
        ProblemReport r = new ProblemReport(me.getId(), text, onSite(pageUrl));
        r.setAnonymous(anonymous);
        r = reports.save(r);
        media.attach(files, r.getId());                  // a report's files ride on post_id, so no post or draft can pick them up again
        return r;
    }

    /** Only a path on this site is kept: nothing the user typed can send the admin to another host. */
    static String onSite(String url) {
        if (url == null || !url.startsWith("/") || url.startsWith("//") || url.length() > 300) return null;
        return url;
    }

    @Transactional(readOnly = true)
    public List<AdminView> list() {
        List<ProblemReport> all = reports.findAllByOrderByResolvedAscCreatedAtDesc(PageRequest.of(0, LIST_LIMIT));
        Map<UUID, User> people = users.findAllById(all.stream().map(ProblemReport::getReporterId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, List<Media>> files = all.isEmpty() ? Map.of() : media.ofPosts(all.stream().map(ProblemReport::getId).toList());
        return all.stream().map(r -> new AdminView(r.getId(), r.isAnonymous() ? "Anonymous" : people.containsKey(r.getReporterId()) ? people.get(r.getReporterId()).getUsername() : "(deleted account)",
                r.getSummary(), r.getPageUrl(), r.getCreatedAt(), r.isResolved(),
                files.getOrDefault(r.getId(), List.of()).stream().map(m -> new MediaDto(m.getId(), m.kind())).toList())).toList();
    }

    public void resolve(UUID id, boolean resolved) {
        reports.findById(id).orElseThrow(() -> new ResourceNotFoundException("That report is gone.")).setResolved(resolved);
    }

    /** A file of this report, for the admin console. */
    @Transactional(readOnly = true)
    public Media file(UUID reportId, UUID mediaId) {
        Media m = media.find(mediaId);
        if (!reportId.equals(m.getPostId())) throw new ResourceNotFoundException("That file is gone.");
        return m;
    }
}
