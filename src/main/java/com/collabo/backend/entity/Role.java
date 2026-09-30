package com.collabo.backend.entity;

public enum Role {
    USER,
    ADMIN,
    MODERATOR   // legacy: grants nothing. Moderators are separate accounts now (see Moderator). Kept so old rows still load.
    // all this one na just roles and chaising levels.... we go up am later
}