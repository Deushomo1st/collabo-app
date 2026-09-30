package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.PaymentDtos.ClaimRequest;
import com.collabo.backend.dto.PaymentDtos.PaymentResponse;
import com.collabo.backend.service.PaymentRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** A space's payment records. Thin shell over PaymentRecordService. */
@RestController
@RequestMapping("/api/spaces/{spaceId}/payments")
public class PaymentRecordController {

    private final PaymentRecordService payments;
    private final CurrentUser current;

    public PaymentRecordController(PaymentRecordService payments, CurrentUser current) { this.payments = payments; this.current = current; }

    @PostMapping
    public PaymentResponse claim(@PathVariable UUID spaceId, @RequestBody ClaimRequest req) {
        return payments.claim(current.require(), spaceId, req.recipient(), req.amount(), req.currency(), req.note());
    }

    @GetMapping
    public List<PaymentResponse> list(@PathVariable UUID spaceId) { return payments.list(current.require(), spaceId); }

    @PostMapping("/{id}/confirm")
    public PaymentResponse confirm(@PathVariable UUID spaceId, @PathVariable UUID id) { return payments.confirm(current.require(), spaceId, id); }

    @PostMapping("/{id}/cancel")
    public PaymentResponse cancel(@PathVariable UUID spaceId, @PathVariable UUID id) { return payments.cancel(current.require(), spaceId, id); }
}
