package com.collabo.backend.entity;

/** Where an application stands. Anything other than SUBMITTED means the founder has looked at it. */
public enum ApplicationState { SUBMITTED, SHORTLISTED, ACCEPTED, DECLINED, WITHDRAWN }
