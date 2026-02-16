package com.steganography.jpeg.encoder;

import static com.steganography.config.SteganographyConfig.BLOCK_SIZE;

/**
 * Forward Discrete Cosine Transform for 8x8 pixel blocks.
 * Computes F = C * f * C^T using pre-computed cosine matrices.
 */
final class ForwardDCT {

    private final double[][] cosineMatrix;
    private final double[][] cosineTranspose;

    ForwardDCT() {
        cosineMatrix = new double[BLOCK_SIZE][BLOCK_SIZE];
        cosineTranspose = new double[BLOCK_SIZE][BLOCK_SIZE];
        initCosineMatrices();
    }

    private void initCosineMatrices() {
        double scale = 1.0 / Math.sqrt(BLOCK_SIZE);
        for (int col = 0; col < BLOCK_SIZE; col++) {
            cosineMatrix[0][col] = scale;
            cosineTranspose[col][0] = scale;
        }
        for (int row = 1; row < BLOCK_SIZE; row++) {
            for (int col = 0; col < BLOCK_SIZE; col++) {
                double value = Math.sqrt(2.0 / BLOCK_SIZE)
                        * Math.cos((2.0 * col + 1.0) * row * Math.PI / (2.0 * BLOCK_SIZE));
                cosineMatrix[row][col] = value;
                cosineTranspose[col][row] = value;
            }
        }
    }

    /**
     * Applies the forward DCT to a level-shifted 8x8 block.
     *
     * @param block input block (pixel values already shifted by -128)
     * @return the DCT coefficient block (rounded to short)
     */
    short[][] transform(short[][] block) {
        short[][] output = new short[BLOCK_SIZE][BLOCK_SIZE];
        double[][] intermediate = new double[BLOCK_SIZE][BLOCK_SIZE];

        // Step 1: intermediate = block * C^T
        for (int row = 0; row < BLOCK_SIZE; row++) {
            for (int col = 0; col < BLOCK_SIZE; col++) {
                double sum = 0.0;
                for (int k = 0; k < BLOCK_SIZE; k++) {
                    sum += block[row][k] * cosineTranspose[k][col];
                }
                intermediate[row][col] = sum;
            }
        }

        // Step 2: output = C * intermediate
        for (int row = 0; row < BLOCK_SIZE; row++) {
            for (int col = 0; col < BLOCK_SIZE; col++) {
                double sum = 0.0;
                for (int k = 0; k < BLOCK_SIZE; k++) {
                    sum += cosineMatrix[row][k] * intermediate[k][col];
                }
                output[row][col] = (short) Math.round(sum);
            }
        }
        return output;
    }
}
