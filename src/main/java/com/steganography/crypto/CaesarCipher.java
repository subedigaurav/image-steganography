package com.steganography.crypto;

import java.nio.charset.StandardCharsets;

/**
 * Improved Caesar cipher for message obfuscation before embedding.
 *
 * Unlike a classic Caesar cipher with a fixed shift, this variant uses a
 * position-dependent shift: each byte is shifted by
 * {@code (key + index) % 256},
 * making frequency analysis harder. The key is an integer (0–255) configurable
 * via {@code STEGO_CAESAR_KEY} in .env or environment.
 *
 * Operates on raw UTF-8 bytes to guarantee that the obfuscated output has
 * exactly the same byte length as the input — critical for the steganography
 * pipeline which stores and retrieves a fixed byte count.
 */
public final class CaesarCipher {

    private static final int BYTE_RANGE = 256;

    private CaesarCipher() {
    }

    /**
     * Encrypts the UTF-8 encoded text using position-dependent Caesar shifts.
     *
     * @param plainText the text to obfuscate
     * @param key       the base shift key (0–255, applied as {@code key & 0xFF})
     * @return the encrypted byte array (same length as UTF-8 input)
     */
    public static byte[] encrypt(String plainText, int key) {
        byte[] data = plainText.getBytes(StandardCharsets.UTF_8);
        int k = key & 0xFF;
        for (int i = 0; i < data.length; i++) {
            int shift = (k + i) % BYTE_RANGE;
            data[i] = (byte) ((Byte.toUnsignedInt(data[i]) + shift) % BYTE_RANGE);
        }
        return data;
    }

    /**
     * Decrypts a byte array encrypted with {@link #encrypt}.
     *
     * @param cipherBytes the encrypted byte array
     * @param key         the key used during encryption
     * @return the original plain text
     */
    public static String decrypt(byte[] cipherBytes, int key) {
        int k = key & 0xFF;
        byte[] data = new byte[cipherBytes.length];
        for (int i = 0; i < cipherBytes.length; i++) {
            int shift = (k + i) % BYTE_RANGE;
            int u = Byte.toUnsignedInt(cipherBytes[i]);
            data[i] = (byte) ((u - shift + BYTE_RANGE) % BYTE_RANGE);
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
