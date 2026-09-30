package com.collabo.backend.entity;

/** What a member may do in a space. The owner has all of them; the first two can be handed out freely. */
public enum SpacePermission {
    LOG_MILESTONES(false), LOG_PAYMENTS(false),
    ACCEPT_MEMBERS(true), EDIT_SETTINGS(true), POST_IN_ROOM(true), MANAGE_RECRUITMENT(true);

    private final boolean weighty;
    SpacePermission(boolean weighty) { this.weighty = weighty; }
    /** Granting these asks the owner to confirm. */
    public boolean isWeighty() { return weighty; }
}
