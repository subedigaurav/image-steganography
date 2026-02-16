package com.steganography.config;

import java.nio.charset.StandardCharsets;

import org.springframework.core.env.Environment;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Central configuration constants for the steganography system.
 * Crypto values are read via env vars or .env (spring-dotenv). AES values must
 * be
 * exactly 16 bytes (UTF-8). See .env.example.
 */
@SuppressFBWarnings(value = "MS_MUTABLE_ARRAY", justification = "Constant lookup table, never modified")
public final class SteganographyConfig {

    private static final int AES_BYTES = 16;
    private static final String DEFAULT_AES_KEY = "juccqhjyodhhfymt";
    private static final String DEFAULT_AES_IV = "blnzllpshgivhxjk";
    private static final int DEFAULT_CAESAR_KEY = 2;

    /** AES-128 key for password encryption in JPEG comments (16 bytes). */
    public static final String AES_KEY = getAesConfig("STEGO_AES_KEY", DEFAULT_AES_KEY);
    /** AES-128 IV for password encryption (16 bytes). */
    public static final String AES_IV = getAesConfig("STEGO_AES_IV", DEFAULT_AES_IV);

    /** Caesar cipher key applied to the message before embedding (0–255). */
    public static final int CAESAR_KEY = getConfigInt("STEGO_CAESAR_KEY", DEFAULT_CAESAR_KEY);

    /** Attribution string embedded in JPEG comment. */
    public static final String STEGO_ATTRIBUTION = "created using gaurav's image-steganography";

    /**
     * Reads an AES config value (16 bytes UTF-8). Falls back if missing or invalid
     * length.
     */
    private static String getAesConfig(String name, String fallback) {
        String value = getConfig(name, null);
        if (value == null) {
            return fallback;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length == AES_BYTES) {
            return value;
        }
        return fallback;
    }

    /**
     * Reads a string config from env vars, system props, or Spring Environment.
     */
    private static String getConfig(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        if (value == null || value.isBlank()) {
            Environment env = EnvironmentHolder.getEnvironment();
            value = env != null ? env.getProperty(name) : null;
        }
        return (value != null && !value.isBlank()) ? value.trim() : fallback;
    }

    /**
     * Reads an integer config from env vars, system props, or Spring Environment.
     */
    private static int getConfigInt(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        if (value == null || value.isBlank()) {
            Environment env = EnvironmentHolder.getEnvironment();
            value = env != null ? env.getProperty(name) : null;
        }
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** JPEG block dimension (8×8 pixels). */
    public static final int BLOCK_SIZE = 8;
    /** Pixels per block (64). */
    public static final int BLOCK_PIXELS = BLOCK_SIZE * BLOCK_SIZE;

    /** Number of colour components (Y, Cb, Cr). */
    public static final int NUM_COMPONENTS = 3;

    /** Channel index used for embedding (Cb = 1). */
    public static final int STEGO_CHANNEL = 1;

    /** Bits embedded per MCU block (16 zigzag positions). */
    public static final int BITS_PER_MCU = 16;

    /** Comment type ID for generic JPEG comments. */
    public static final int COMMENT_TYPE_GENERIC = 0;
    /** Comment type ID for message length metadata. */
    public static final int COMMENT_TYPE_MSG_LEN = 1;
    /** Comment type ID for encrypted password. */
    public static final int COMMENT_TYPE_PASSWORD = 2;

    /**
     * Zigzag-index patterns used to embed / extract message bits.
     * Four patterns are cycled through MCU-by-MCU so that the
     * modified coefficients are spread across mid-frequency positions.
     */
    public static final int[][] EMBEDDING_PATTERNS = {
            { 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40 },
            { 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25 },
            { 28, 27, 26, 25, 29, 30, 31, 32, 33, 34, 35, 36, 40, 39, 38, 37 },
            { 25, 26, 27, 28, 36, 35, 34, 33, 32, 31, 30, 29, 37, 38, 39, 40 }
    };
}
