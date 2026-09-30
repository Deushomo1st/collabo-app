package com.collabo.backend.dto;

import com.collabo.backend.entity.CredentialKind;

import java.time.Instant;

/** Seeding an entry by hand until spaces and milestones record their own. occurredAt defaults to now. */
public record AdminCredentialRequest(String username, CredentialKind kind, String title, String detail,
                                     String sourceType, String sourceId, Instant occurredAt) {}
