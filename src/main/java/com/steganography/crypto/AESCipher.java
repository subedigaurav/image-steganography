package com.steganography.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-128-CBC encryption / decryption utility.
 * Used to protect the password stored inside JPEG comment markers.
 */
public final class AESCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORM = "AES/CBC/PKCS5Padding";

    private AESCipher() {
    }

    /**
     * Encrypts plain text using AES-128-CBC and returns a Base64-encoded string.
     *
     * @param key        16-byte key (UTF-8)
     * @param initVector 16-byte IV (UTF-8)
     * @param plainText  the text to encrypt
     * @return Base64-encoded ciphertext
     */
    public static String encrypt(String key, String initVector, String plainText) {
        try {
            IvParameterSpec ivSpec = new IvParameterSpec(initVector.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new RuntimeException("AES encryption failed", ex);
        }
    }

    /**
     * Decrypts a Base64-encoded ciphertext string.
     *
     * @param key        16-byte key (UTF-8)
     * @param initVector 16-byte IV (UTF-8)
     * @param cipherText Base64-encoded ciphertext
     * @return the decrypted plain text
     */
    public static String decrypt(String key, String initVector, String cipherText) {
        try {
            IvParameterSpec ivSpec = new IvParameterSpec(initVector.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] original = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException("AES decryption failed", ex);
        }
    }
}
