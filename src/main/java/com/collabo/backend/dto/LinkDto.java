package com.collabo.backend.dto;

import com.collabo.backend.entity.ProfileLink;

/** A profile link, in and out. On the way in, note may be null (treated as empty). */
public record LinkDto(String title, String url, String note) {

    public static LinkDto of(ProfileLink l) {
        return new LinkDto(l.getTitle(), l.getUrl(), l.getNote());
    }
}
