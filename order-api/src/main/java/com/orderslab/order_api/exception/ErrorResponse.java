package com.orderslab.order_api.exception;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
        List<String> validationErrors) {

    public ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this(timestamp, status, error, message, path, null);
    }
}
