package com.collabo.backend.dto;

/** Follow counts plus how the viewer relates to this profile. canFollow is false for yourself and across a block (either way). */
public record FollowState(long followers, long following, boolean iFollow, boolean followsMe, boolean canFollow) {}
