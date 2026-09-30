package com.collabo.backend.entity;

/** Who may see a user's credentials. Applying to a post overrides this in both directions (phase 4). */
public enum CredentialsPrivacy {
    EVERYONE,
    FOLLOWERS,      // people who follow me
    FOLLOWING,      // people I follow
    MUTUAL,         // we follow each other
    APPLICANTS      // only people who applied to my posts
}
