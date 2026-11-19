package com.dispatch.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(String status, String message, List<String> errors, Instant timestamp) {

    public static ErrorResponse of(String message, List<String> errors) {
        return new ErrorResponse("error", message, errors, Instant.now());
    }
}
