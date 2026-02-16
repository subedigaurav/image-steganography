package com.steganography.exception;

/**
 * Thrown when the input image is invalid or cannot be processed.
 */
public class InvalidImageException extends SteganographyException {

    /**
     * @param message a description of the error
     */
    public InvalidImageException(String message) {
        super(message);
    }

    /**
     * @param message a description of the error
     * @param cause   the underlying exception
     */
    public InvalidImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
