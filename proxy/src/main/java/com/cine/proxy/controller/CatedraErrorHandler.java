package com.cine.proxy.controller;

import com.cine.proxy.client.CatedraException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Maps CatedraException -> HTTP responses.
 * - timeout -> 504 Gateway Timeout
 * - 4xx from cátedra -> propagate same code
 * - 5xx / other upstream errors -> 502 Bad Gateway
 */
@ControllerAdvice
public class CatedraErrorHandler {

    @ExceptionHandler(CatedraException.class)
    public ResponseEntity<?> handleCatedra(CatedraException ex) {
        int status = ex.getStatus();
        if (status == 504) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(ex.getMessage());
        }
        if (status >= 400 && status < 500) {
            return ResponseEntity.status(status).body(ex.getMessage());
        }
        // treat others as 502
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ex.getMessage());
    }
}