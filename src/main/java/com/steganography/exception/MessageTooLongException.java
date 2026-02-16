package com.steganography.exception;

/**
 * Thrown when the message exceeds the embedding capacity of the cover image.
 */
public class MessageTooLongException extends SteganographyException {

    /**
     * @param messageBytes  the message length in bytes
     * @param capacityBytes the maximum capacity in bytes
     */
    public MessageTooLongException(int messageBytes, int capacityBytes) {
        super("Message is too long (" + messageBytes + " bytes). "
                + "Maximum capacity for this image is " + capacityBytes + " bytes.");
    }
}
