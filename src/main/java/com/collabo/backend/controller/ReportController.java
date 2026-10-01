package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.ReportDtos.Submitted;
import com.collabo.backend.dto.ReportDtos.SubmitRequest;
import com.collabo.backend.service.ReportService;
import org.springframework.web.bind.annotation.*;

/** "Report a problem": a write-up and up to three screenshots or videos (uploaded first through /api/media). The admin reads them in the console. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;
    private final CurrentUser current;

    public ReportController(ReportService reports, CurrentUser current) { this.reports = reports; this.current = current; }

    @PostMapping
    public Submitted submit(@RequestBody SubmitRequest req) {
        return new Submitted(reports.submit(current.require(), req.summary(), req.pageUrl(), req.mediaIds()).getId());
    }
}
