package com.dispatch.exception;

/** Raised when a plan is requested but orders or vehicles have not been submitted yet. Maps to HTTP 409. */
public class NoDataException extends RuntimeException {

    public NoDataException(String message) {
        super(message);
    }
}
