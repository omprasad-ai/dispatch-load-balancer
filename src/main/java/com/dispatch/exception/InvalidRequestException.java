package com.dispatch.exception;

import java.util.List;

/** Business-level input problem that bean validation cannot express (e.g. duplicate ids). Maps to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    private final List<String> errors;

    public InvalidRequestException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
