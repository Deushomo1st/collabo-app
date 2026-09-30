package com.collabo.backend.dto;

import com.collabo.backend.entity.CredentialEntry;
import com.collabo.backend.entity.CredentialKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Someone's credentials as one viewer may see them. When visible is false the list is empty and says
 * nothing about how many entries exist.
 */
public record CredentialsResponse(boolean visible, List<Entry> entries) {

    public record Entry(UUID id, CredentialKind kind, String title, String detail, Instant occurredAt, boolean featured) {
        static Entry of(CredentialEntry e) {
            return new Entry(e.getId(), e.getKind(), e.getTitle(), e.getDetail(), e.getOccurredAt(), e.isFeatured());
        }
    }

    public static CredentialsResponse hidden() { return new CredentialsResponse(false, List.of()); }

    public static CredentialsResponse of(List<CredentialEntry> rows) {
        return new CredentialsResponse(true, rows.stream().map(Entry::of).toList());
    }
}
