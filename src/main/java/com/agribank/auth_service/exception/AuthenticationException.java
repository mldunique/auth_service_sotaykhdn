package com.agribank.auth_service.exception;

/**
 * Specific exception indicating authentication failures (e.g. invalid tokens, client communication failure).
 */
public class AuthenticationException extends AppException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
