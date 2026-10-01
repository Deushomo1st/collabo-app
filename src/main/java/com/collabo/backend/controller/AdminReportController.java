package com.collabo.backend.controller;

import com.collabo.backend.dto.ReportDtos.AdminView;
import com.collabo.backend.dto.ReportDtos.ResolveRequest;
import com.collabo.backend.entity.Media;
import com.collabo.backend.service.MediaService;
import com.collabo.backend.service.ReportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Reports for the admin console. Gated by AdminKeyFilter like the rest of /api/admin. */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService reports;
    private final MediaService media;

    public AdminReportController(ReportService reports, MediaService media) { this.reports = reports; this.media = media; }

    @GetMapping
    public List<AdminView> list() { return reports.list(); }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID id, @RequestBody ResolveRequest req) {
        reports.resolve(id, req.resolved());
        return ResponseEntity.noContent().build();
    }

    /** The console fetches this with its key and shows it as a blob (an img tag cannot send the key header). */
    @GetMapping("/{id}/media/{mediaId}")
    public ResponseEntity<Resource> file(@PathVariable UUID id, @PathVariable UUID mediaId) {
        Media m = reports.file(id, mediaId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(m.getContentType())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(media.file(mediaId)));
    }
}
