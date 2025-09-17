package com.flavorfleet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllExceptions(Exception ex) {
        logger.error("Unhandled exception occurred: {}", ex.getMessage(), ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal server error");
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        logger.error("Illegal argument exception: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "Bad request");
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Invalid argument");
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException ex) {
        logger.error("Authentication failed: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "Authentication failed");
        error.put("message", ex.getMessage() != null ? ex.getMessage() : "Invalid credentials");
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }
}