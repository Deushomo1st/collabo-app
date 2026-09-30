package com.collabo.backend.dto;

/** featured is required: true promotes the entry into feats, false takes it back. */
public record FeatureRequest(Boolean featured) {}
