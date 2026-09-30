package com.collabo.backend.dto;

/** A table in the admin panel: its row count, and whether the panel may wipe it (most are browse-only). */
public record TableInfo(String name, long rowCount, boolean canClean) {
}
