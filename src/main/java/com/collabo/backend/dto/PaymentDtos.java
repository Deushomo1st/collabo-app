package com.collabo.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    /** The billing form: the payer names the recipient, the declared amount and a currency label. */
    public record ClaimRequest(String recipient, BigDecimal amount, String currency, String note) {}

    /** state: CLAIMED (the payer says it was paid), CONFIRMED (the recipient says it arrived) or CANCELLED (the payer withdrew it). */
    public record PaymentResponse(UUID id, PersonDto payer, PersonDto recipient, BigDecimal amount, String currency, String note,
                                  String state, Instant createdAt, Instant resolvedAt) {}
}
