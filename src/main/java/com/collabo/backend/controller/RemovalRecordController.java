package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.RemovalRecordDtos.AddressRequest;
import com.collabo.backend.dto.RemovalRecordDtos.AddressView;
import com.collabo.backend.dto.RemovalRecordDtos.RecordView;
import com.collabo.backend.service.RemovalRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Removal records on a profile, and addresses on them. Thin shell over RemovalRecordService. */
@RestController
@RequestMapping("/api")
public class RemovalRecordController {

    private final RemovalRecordService records;
    private final CurrentUser current;

    public RemovalRecordController(RemovalRecordService records, CurrentUser current) { this.records = records; this.current = current; }

    @GetMapping("/users/{username}/removals")
    public List<RecordView> of(@PathVariable String username) { return records.of(current.require(), username); }

    @PostMapping("/removals/{recordId}/addresses")
    public AddressView address(@PathVariable UUID recordId, @RequestBody AddressRequest req) { return records.address(current.require(), recordId, req.body()); }
}
