package com.morphoaid.backend.integration.ai;

public class UltralyticsException extends RuntimeException {

    public UltralyticsException(String message) {
        super(message);
    }

    public UltralyticsException(String message, Throwable cause) {
        super(message, cause);
    }
}
