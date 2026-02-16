package com.steganography.jpeg.decoder;

import java.io.IOException;
import java.util.Arrays;

/**
 * Decoder-side Huffman table with a fast 9-bit look-up for common symbols
 * and a slow path for longer codes.
 */
final class DecoderHuffmanTable {

    static final int FAST_BITS = 9;

    /** Fast look-up table: symbol index for codes up to FAST_BITS long. */
    final byte[] fastLookup;

    /** Symbol values in code-length order. */
    final byte[] symbolValues;

    /** Code length for each symbol (by index). */
    final byte[] symbolSizes;

    /** Upper bound of canonical codes at each bit length (left-aligned to 16 bits). */
    final int[] maxCodeAtLength;

    /** Delta to convert a code value to a symbol index at each bit length. */
    final int[] deltaAtLength;

    DecoderHuffmanTable(int[] bitLengthCounts) throws IOException {
        int symbolCount = 0;
        for (int i = 0; i < 16; i++) {
            symbolCount += bitLengthCounts[i];
        }

        fastLookup = new byte[1 << FAST_BITS];
        symbolValues = new byte[symbolCount];
        symbolSizes = new byte[symbolCount];
        maxCodeAtLength = new int[18];
        deltaAtLength = new int[17];

        // Build size table
        for (int bitLen = 0, k = 0; bitLen < 16; bitLen++) {
            for (int j = 0; j < bitLengthCounts[bitLen]; j++) {
                symbolSizes[k++] = (byte) (bitLen + 1);
            }
        }

        // Build code table and fast look-up
        int[] codes = new int[256];
        int bitLen = 1;
        int k = 0;
        for (int code = 0; bitLen <= 16; bitLen++) {
            deltaAtLength[bitLen] = k - code;
            if (k < symbolCount && (symbolSizes[k] & 0xFF) == bitLen) {
                do {
                    codes[k++] = code++;
                } while (k < symbolCount && (symbolSizes[k] & 0xFF) == bitLen);
                if (code - 1 >= (1 << bitLen)) {
                    throw new IOException("Bad Huffman code length");
                }
            }
            maxCodeAtLength[bitLen] = code << (16 - bitLen);
            code <<= 1;
        }
        maxCodeAtLength[bitLen] = Integer.MAX_VALUE;

        Arrays.fill(fastLookup, (byte) -1);
        for (int i = 0; i < k; i++) {
            int size = symbolSizes[i] & 0xFF;
            if (size <= FAST_BITS) {
                int baseCode = codes[i] << (FAST_BITS - size);
                int count = 1 << (FAST_BITS - size);
                for (int j = 0; j < count; j++) {
                    fastLookup[baseCode + j] = (byte) i;
                }
            }
        }
    }

    int getNumSymbols() {
        return symbolValues.length;
    }
}
