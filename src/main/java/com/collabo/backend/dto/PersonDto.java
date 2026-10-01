package com.collabo.backend.dto;

import com.collabo.backend.entity.User;

/** A person in a followers/following list. */
public record PersonDto(String username, String preferredTitle, String fullName) {
    public PersonDto(String username, String preferredTitle) { this(username, preferredTitle, null); }
    public static PersonDto of(User u) { return new PersonDto(u.getUsername(), u.getPreferredTitle(), u.fullName()); }
}
