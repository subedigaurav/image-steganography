package com.steganography.exception;

/**
 * Base exception for all steganography-related errors.
 */
public class SteganographyException extends RuntimeException {

    /**
     * @param message a description of the error
     */
    public SteganographyException(String message) {
        super(message);
    }

    /**
     * @param message a description of the error
     * @param cause   the underlying exception
     */
    public SteganographyException(String message, Throwable cause) {
        super(message, cause);
    }
}
