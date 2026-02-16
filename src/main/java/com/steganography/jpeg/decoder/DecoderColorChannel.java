package com.steganography.jpeg.decoder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Decoder-specific colour channel that extends the common model
 * with Huffman table references and dequantisation data.
 */
@SuppressFBWarnings(value = "URF_UNREAD_FIELD", justification = "Fields reserved for future decoder features")
final class DecoderColorChannel {

    final int id;

    int dcPrediction;
    DecoderHuffmanTable huffmanDC;
    DecoderHuffmanTable huffmanAC;
    byte[] dequantTable;

    int horizontalBlocks = 1;
    int verticalBlocks = 1;
    int width;
    int height;
    int minRequiredWidth;
    int minRequiredHeight;
    int outputPosition;
    int upsamplerFlags;

    DecoderColorChannel(int id) {
        this.id = id;
    }

    void resetDcPrediction() {
        this.dcPrediction = 0;
    }
}
