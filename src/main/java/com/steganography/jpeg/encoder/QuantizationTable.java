package com.steganography.jpeg.encoder;

import static com.steganography.config.SteganographyConfig.BLOCK_SIZE;

/**
 * Generates the JPEG quantisation matrix for a given quality level (1-100).
 * Uses the standard ITU-T T.81 base luminance table scaled by the IJG formula.
 */
final class QuantizationTable {

    /** ITU-T T.81 Annex K, Table K.1 - Luminance quantisation base values. */
    private static final int[][] BASE_TABLE = {
            { 16, 11, 10, 16, 24, 40, 51, 61 },
            { 12, 12, 14, 19, 26, 58, 60, 55 },
            { 14, 13, 16, 24, 40, 57, 69, 56 },
            { 14, 17, 22, 29, 51, 87, 80, 62 },
            { 18, 22, 37, 56, 68, 109, 103, 77 },
            { 24, 35, 55, 64, 81, 104, 113, 92 },
            { 49, 64, 78, 87, 103, 121, 120, 101 },
            { 72, 92, 95, 98, 112, 100, 103, 99 },
    };

    /** The scaled quantisation matrix. */
    final int[][] matrix = new int[BLOCK_SIZE][BLOCK_SIZE];

    QuantizationTable(int quality) {
        buildMatrix(quality);
    }

    private void buildMatrix(int quality) {
        if (quality == 100) {
            for (int r = 0; r < BLOCK_SIZE; r++) {
                for (int c = 0; c < BLOCK_SIZE; c++) {
                    matrix[r][c] = 1;
                }
            }
            return;
        }
        int scaleFactor = quality < 50 ? (5000 / quality) : (200 - 2 * quality);
        for (int r = 0; r < BLOCK_SIZE; r++) {
            for (int c = 0; c < BLOCK_SIZE; c++) {
                int value = (scaleFactor * BASE_TABLE[r][c] + 50) / 100;
                matrix[r][c] = Math.max(1, value);
            }
        }
    }
}
