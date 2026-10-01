package com.collabo.backend.entity;

/** Who may start a new MySpace yarn with a user. Existing conversations carry on. */
public enum MessagePrivacy {
    EVERYONE,
    FOLLOWERS,      // people who follow me
    FOLLOWING,      // people I follow
    MUTUAL          // we follow each other
}
