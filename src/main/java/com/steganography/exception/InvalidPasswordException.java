package com.steganography.exception;

/**
 * Thrown when the supplied password does not match the one stored in the stego image.
 */
public class InvalidPasswordException extends SteganographyException {

    public InvalidPasswordException() {
        super("The password is incorrect.");
    }
}
