package com.agribank.auth_service.exception;

/**
 * Base custom runtime exception class for the application.
 */
public class AppException extends RuntimeException {

    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
