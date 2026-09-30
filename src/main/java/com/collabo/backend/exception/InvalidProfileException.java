package com.collabo.backend.exception;

/** A profile field broke a rule (too many words, bad characters...). The message is shown to the user. */
public class InvalidProfileException extends RuntimeException {

    public InvalidProfileException(String message) {
        super(message);
    }
}
