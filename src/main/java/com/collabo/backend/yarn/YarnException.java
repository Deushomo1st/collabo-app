package com.collabo.backend.yarn;

import org.springframework.http.HttpStatus;

/** A chat rule was broken (blocked, not a member, request pending...). Carries its own HTTP status. */
public class YarnException extends RuntimeException {

    private final HttpStatus status;

    public YarnException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
