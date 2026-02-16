package com.steganography.jpeg.encoder;

import java.io.IOException;

/**
 * Encoder-side Huffman table.
 *
 * Follows the JPEG standard procedure:
 * 1. Generate HUFFSIZE from the bit-length counts.
 * 2. Generate HUFFCODE from HUFFSIZE.
 * 3. Build the encoding look-up tables (EHUFCO / EHUFSI).
 */
final class EncoderHuffmanTable {

    /** Number of codes of each bit length (index 1..16). */
    private final byte[] bitLengthCounts = new byte[17];

    /** Symbol values in code-length order. */
    private byte[] symbolValues;

    private int symbolCount;
    private final int[] codewordSizes;
    private final int[] codewords;
    private int lastSymbolIndex;

    /** Encoding look-up: codeword for each symbol value. */
    private final int[] encodingCode = new int[256];
    /** Encoding look-up: codeword bit-length for each symbol value. */
    private final int[] encodingSize = new int[256];

    EncoderHuffmanTable(int[] lengthCounts) throws IOException {
        for (int i = 0; i < 16; i++) {
            this.symbolCount += lengthCounts[i];
            this.bitLengthCounts[i + 1] = (byte) lengthCounts[i];
        }
        this.symbolValues = new byte[symbolCount];
        this.codewordSizes = new int[symbolCount + 1];
        this.codewords = new int[symbolCount + 1];
    }

    void setSymbolValues(byte[] values) {
        this.symbolValues = values;
    }

    int getSymbolCount() {
        return symbolCount;
    }

    int getSymbolValue(int index) {
        return symbolValues[index] & 0xFF;
    }

    int getBitCount(int length) {
        return bitLengthCounts[length] & 0xFF;
    }

    int getEncodingCode(int symbol) {
        return encodingCode[symbol];
    }

    int getEncodingSize(int symbol) {
        return encodingSize[symbol];
    }

    /**
     * Builds the encoding tables from the symbol values and bit-length counts.
     * Must be called after {@link #setSymbolValues}.
     */
    void buildEncodingTables() {
        generateSizeTable();
        generateCodeTable();
        buildEncodingLookup();
    }

    /** Figure C.1 - Generate table of Huffman code sizes. */
    private void generateSizeTable() {
        int k = 0;
        for (int bitLen = 1; bitLen <= 16; bitLen++) {
            int count = bitLengthCounts[bitLen] & 0xFF;
            for (int j = 0; j < count; j++) {
                codewordSizes[k++] = bitLen;
            }
        }
        codewordSizes[k] = 0;
        lastSymbolIndex = k;
    }

    /** Figure C.2 - Generate table of Huffman codes. */
    private void generateCodeTable() {
        int k = 0;
        int code = 0;
        int currentSize = codewordSizes[0];
        while (true) {
            codewords[k] = code;
            code++;
            k++;
            if (codewordSizes[k] == currentSize) {
                continue;
            }
            if (codewordSizes[k] == 0) {
                return;
            }
            do {
                code <<= 1;
                currentSize++;
            } while (codewordSizes[k] != currentSize);
        }
    }

    /** Figure C.3 - Order codes for encoding. */
    private void buildEncodingLookup() {
        for (int k = 0; k < lastSymbolIndex; k++) {
            int symbol = symbolValues[k] & 0xFF;
            encodingCode[symbol] = codewords[k];
            encodingSize[symbol] = codewordSizes[k];
        }
    }
}
