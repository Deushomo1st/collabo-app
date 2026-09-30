package com.collabo.backend.dto;

import com.collabo.backend.entity.CredentialEntry;
import com.collabo.backend.entity.CredentialKind;
import com.collabo.backend.entity.ShippedLink;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Someone's credentials as one viewer may see them. When visible is false the list is empty and says
 * nothing about how many entries exist.
 */
public record CredentialsResponse(boolean visible, List<Entry> entries) {

    public record Shipped(UUID id, String title, String url) {}

    /** nothingShipped is the tag shown when an entry has no proof of shipping. */
    public record Entry(UUID id, CredentialKind kind, String title, String detail, Instant occurredAt,
                        boolean featured, List<Shipped> shipped, boolean nothingShipped) {
        public static Entry of(CredentialEntry e, List<ShippedLink> links) {
            List<Shipped> shipped = links.stream().map(l -> new Shipped(l.getId(), l.getTitle(), l.getUrl())).toList();
            return new Entry(e.getId(), e.getKind(), e.getTitle(), e.getDetail(), e.getOccurredAt(),
                    e.isFeatured(), shipped, shipped.isEmpty());
        }
    }

    public static CredentialsResponse hidden() { return new CredentialsResponse(false, List.of()); }
}
