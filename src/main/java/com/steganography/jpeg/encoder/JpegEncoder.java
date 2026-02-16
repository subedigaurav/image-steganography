package com.steganography.jpeg.encoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.plugins.jpeg.JPEGHuffmanTable;

import static com.steganography.config.SteganographyConfig.AES_IV;
import static com.steganography.config.SteganographyConfig.AES_KEY;
import static com.steganography.config.SteganographyConfig.BITS_PER_MCU;
import static com.steganography.config.SteganographyConfig.BLOCK_PIXELS;
import static com.steganography.config.SteganographyConfig.BLOCK_SIZE;
import static com.steganography.config.SteganographyConfig.COMMENT_TYPE_GENERIC;
import static com.steganography.config.SteganographyConfig.COMMENT_TYPE_MSG_LEN;
import static com.steganography.config.SteganographyConfig.COMMENT_TYPE_PASSWORD;
import static com.steganography.config.SteganographyConfig.EMBEDDING_PATTERNS;
import static com.steganography.config.SteganographyConfig.NUM_COMPONENTS;
import static com.steganography.config.SteganographyConfig.STEGO_ATTRIBUTION;
import static com.steganography.config.SteganographyConfig.STEGO_CHANNEL;
import com.steganography.crypto.AESCipher;
import com.steganography.jpeg.common.ColorChannel;
import com.steganography.jpeg.common.ZigZagOrder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Baseline JPEG encoder with DCT-domain steganographic embedding.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Image and stream received from caller by design")
public class JpegEncoder {

    private final BufferedImage sourceImage;
    private final OutputStream outputStream;
    private final int imageWidth;
    private final int imageHeight;
    private final int mcuColumnsCount;
    private final int mcuRowsCount;
    private final int totalMcuCount;

    private final byte[] messageBytes;
    private final String password;
    private final QuantizationTable quantTable;
    private final ForwardDCT forwardDct = new ForwardDCT();

    private final byte[][][] ycbcrPixels;
    private final ColorChannel[] channels = new ColorChannel[NUM_COMPONENTS];
    private final EncoderHuffmanTable[] huffmanTables = new EncoderHuffmanTable[4];

    private long bitBuffer;
    private int bitsInBuffer;

    private static final int[] COMPONENT_IDS = { 1, 2, 3 };
    private static final int[] H_SAMPLING_FACTORS = { 1, 1, 1 };
    private static final int[] V_SAMPLING_FACTORS = { 1, 1, 1 };
    private static final int[] QUANT_TABLE_NUMBERS = { 0, 1, 1 };
    private static final int[] DC_TABLE_NUMBERS = { 0, 1, 1 };
    private static final int[] AC_TABLE_NUMBERS = { 0, 1, 1 };

    /**
     * Creates an encoder for the given image and message.
     *
     * @param quality      JPEG quality (1-100)
     * @param image        the source image (dimensions must be multiples of 8)
     * @param out          the output stream for the JPEG bytes
     * @param messageBytes the obfuscated message bytes to embed
     * @param password     the password to store (AES-encrypted in metadata)
     */
    public JpegEncoder(int quality, BufferedImage image, OutputStream out,
            byte[] messageBytes, String password) {
        this.sourceImage = image;
        this.outputStream = out;
        this.messageBytes = messageBytes;
        this.password = password;

        this.imageWidth = image.getWidth();
        this.imageHeight = image.getHeight();
        this.quantTable = new QuantizationTable(quality);
        this.ycbcrPixels = new byte[NUM_COMPONENTS][imageHeight][imageWidth];

        for (int i = 0; i < NUM_COMPONENTS; i++) {
            channels[i] = new ColorChannel(i, imageWidth, imageHeight);
        }

        this.mcuColumnsCount = imageWidth / BLOCK_SIZE;
        this.mcuRowsCount = imageHeight / BLOCK_SIZE;
        this.totalMcuCount = mcuColumnsCount * mcuRowsCount;
    }

    /**
     * Encodes the image with the embedded message and writes the full
     * JPEG byte stream (SOI through compressed data) to the output.
     * Call {@link #writeEndOfImage()} afterwards to finalise.
     */
    public void encode() throws IOException {
        convertToYCbCr();
        performForwardDCT();
        embedMessageBits();

        writeStartOfImage();
        writeJfifSegment();
        writeCommentSegment();
        writeMessageLengthSegment();
        writePasswordSegment();
        writeQuantizationTables();
        writeStartOfFrame();
        writeHuffmanTables();
        writeStartOfScan();
        writeEntropyCodedData();
        flushBitBuffer();
    }

    /** Writes the EOI marker to finalise the JPEG stream. */
    public void writeEndOfImage() throws IOException {
        outputStream.write(0xFF);
        outputStream.write(0xD9);
        outputStream.flush();
    }

    /** Converts RGB pixels to YCbCr colour space. */
    private void convertToYCbCr() {
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int rgb = sourceImage.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                ycbcrPixels[0][y][x] = (byte) clamp((int) (0.299 * r + 0.587 * g + 0.114 * b));
                ycbcrPixels[1][y][x] = (byte) clamp((int) (-0.1687 * r - 0.3313 * g + 0.5 * b + 128));
                ycbcrPixels[2][y][x] = (byte) clamp((int) (0.5 * r - 0.4187 * g - 0.0813 * b + 128));
            }
        }
    }

    /** Clamps a value to the range [0, 255]. */
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** Applies forward DCT, quantisation, and zigzag ordering to all blocks. */
    private void performForwardDCT() {
        short[][] block = new short[BLOCK_SIZE][BLOCK_SIZE];

        for (int channelIdx = 0; channelIdx < NUM_COMPONENTS; channelIdx++) {
            int bufferPos = 0;
            for (int mcuRow = 0; mcuRow < mcuRowsCount; mcuRow++) {
                for (int mcuCol = 0; mcuCol < mcuColumnsCount; mcuCol++) {
                    int baseY = mcuRow * BLOCK_SIZE;
                    int baseX = mcuCol * BLOCK_SIZE;

                    for (int row = 0; row < BLOCK_SIZE; row++) {
                        for (int col = 0; col < BLOCK_SIZE; col++) {
                            block[row][col] = (short) ((ycbcrPixels[channelIdx][baseY + row][baseX + col] & 0xFF)
                                    - 128);
                        }
                    }

                    short[][] dctBlock = forwardDct.transform(block);

                    int[] zigzag = ZigZagOrder.toZigZag(dctBlock);
                    int[] quantValues = ZigZagOrder.toZigZag(quantTable.matrix);
                    short[] zigzagBlock = new short[BLOCK_PIXELS];
                    for (int i = 0; i < BLOCK_PIXELS; i++) {
                        zigzagBlock[i] = (short) Math.round((double) zigzag[i] / quantValues[i]);
                    }

                    channels[channelIdx].getCoefficientBuffer().position(bufferPos);
                    channels[channelIdx].getCoefficientBuffer().put(zigzagBlock);
                    bufferPos += BLOCK_PIXELS;
                }
            }
        }
    }

    /**
     * Embeds message LSBs into Cb-channel DCT coefficients at configured positions.
     */
    private void embedMessageBits() {
        int remainingBits = messageBytes.length * 8;
        int requiredMcus = (int) Math.ceil(remainingBits / (double) BITS_PER_MCU);

        for (ColorChannel ch : channels) {
            ch.getCoefficientBuffer().position(0);
        }

        ColorChannel cbChannel = channels[STEGO_CHANNEL];
        short[] blockData = new short[BLOCK_PIXELS];
        int bufferPos = 0;

        int byteIndex = 0;
        int currentByte = 0;
        int bitsRemaining = 0;

        for (int mcuIndex = 0; mcuIndex < requiredMcus; mcuIndex++) {
            cbChannel.getCoefficientBuffer().get(blockData);

            int[] pattern = EMBEDDING_PATTERNS[mcuIndex % EMBEDDING_PATTERNS.length];

            for (int j = 0; j < BITS_PER_MCU && remainingBits > 0; j++) {
                if (bitsRemaining == 0) {
                    currentByte = messageBytes[byteIndex++] & 0xFF;
                    bitsRemaining = 8;
                }
                int bit = (currentByte >> (bitsRemaining - 1)) & 1;
                bitsRemaining--;
                remainingBits--;

                blockData[pattern[j]] = (short) ((blockData[pattern[j]] & ~1) | bit);
            }

            cbChannel.getCoefficientBuffer().position(bufferPos);
            cbChannel.getCoefficientBuffer().put(blockData);
            bufferPos += BLOCK_PIXELS;
        }
    }

    private void writeEntropyCodedData() throws IOException {
        buildAllHuffmanTables();

        channels[0].setDcTableIndex(0);
        channels[0].setAcTableIndex(1); // Y -> luma tables
        channels[1].setDcTableIndex(2);
        channels[1].setAcTableIndex(3); // Cb -> chroma tables
        channels[2].setDcTableIndex(2);
        channels[2].setAcTableIndex(3); // Cr -> chroma tables

        for (ColorChannel ch : channels) {
            ch.getCoefficientBuffer().position(0);
        }

        short[] blockCoeffs = new short[BLOCK_PIXELS];

        for (int mcu = 0; mcu < totalMcuCount; mcu++) {
            for (ColorChannel ch : channels) {
                ch.getCoefficientBuffer().get(blockCoeffs);
                short previousDc = ch.getDcPrediction();
                writeHuffmanBlock(blockCoeffs, previousDc,
                        huffmanTables[ch.getDcTableIndex()],
                        huffmanTables[ch.getAcTableIndex()]);
                ch.setDcPrediction(blockCoeffs[0]);
            }
        }
    }

    private void buildAllHuffmanTables() {
        huffmanTables[0].buildEncodingTables();
        huffmanTables[1].buildEncodingTables();
        huffmanTables[2].buildEncodingTables();
        huffmanTables[3].buildEncodingTables();
    }

    private void writeHuffmanBlock(short[] coeffs, int previousDc,
            EncoderHuffmanTable dcTable,
            EncoderHuffmanTable acTable) throws IOException {
        // DC coefficient (differential)
        int dcDiff = coeffs[0] - previousDc;
        int dcCategory = bitLength(dcDiff);
        writeBits(dcTable.getEncodingCode(dcCategory), dcTable.getEncodingSize(dcCategory));
        if (dcCategory > 0) {
            if (dcDiff < 0) {
                dcDiff += (1 << dcCategory) - 1;
            }
            writeBits(dcDiff, dcCategory);
        }

        // AC coefficients
        int zeroRun = 0;
        for (int k = 1; k < BLOCK_PIXELS; k++) {
            if (coeffs[k] == 0) {
                zeroRun++;
                continue;
            }
            while (zeroRun >= 16) {
                writeBits(acTable.getEncodingCode(0xF0), acTable.getEncodingSize(0xF0));
                zeroRun -= 16;
            }
            int acCategory = bitLength(coeffs[k]);
            int symbol = (zeroRun << 4) | acCategory;
            writeBits(acTable.getEncodingCode(symbol), acTable.getEncodingSize(symbol));
            int acValue = coeffs[k];
            if (acValue < 0) {
                acValue += (1 << acCategory) - 1;
            }
            writeBits(acValue, acCategory);
            zeroRun = 0;
        }
        if (zeroRun > 0) {
            writeBits(acTable.getEncodingCode(0x00), acTable.getEncodingSize(0x00));
        }
    }

    /** Returns the number of bits needed to represent the absolute value. */
    private static int bitLength(int value) {
        if (value < 0) {
            value = -value;
        }
        int length = 0;
        while (value > 0) {
            value >>= 1;
            length++;
        }
        return length;
    }

    /** Appends bits to the output stream with byte-stuffing for 0xFF. */
    private void writeBits(int code, int size) throws IOException {
        long buffer = bitBuffer;
        int bits = bitsInBuffer;

        buffer |= ((long) code) << (64 - bits - size);
        bits += size;

        while (bits >= 8) {
            int byteValue = (int) ((buffer >>> 56) & 0xFF);
            outputStream.write(byteValue);
            if (byteValue == 0xFF) {
                outputStream.write(0); // byte-stuffing
            }
            buffer <<= 8;
            bits -= 8;
        }
        bitBuffer = buffer;
        bitsInBuffer = bits;
    }

    /** Flushes any remaining bits in the bit buffer to the output stream. */
    private void flushBitBuffer() throws IOException {
        long buffer = bitBuffer;
        int bits = bitsInBuffer;
        while (bits >= 8) {
            int byteValue = (int) ((buffer >>> 56) & 0xFF);
            outputStream.write(byteValue);
            if (byteValue == 0xFF) {
                outputStream.write(0);
            }
            buffer <<= 8;
            bits -= 8;
        }
        if (bits > 0) {
            // Pad remaining bits with 1s per JPEG spec
            int byteValue = (int) (((buffer >>> 56) | (0xFF >>> bits)) & 0xFF);
            outputStream.write(byteValue);
            if (byteValue == 0xFF) {
                outputStream.write(0);
            }
        }
        bitBuffer = 0;
        bitsInBuffer = 0;
    }

    private void writeStartOfImage() throws IOException {
        outputStream.write(0xFF);
        outputStream.write(0xD8);
    }

    private void writeJfifSegment() throws IOException {
        byte[] jfif = {
                (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10,
                0x4A, 0x46, 0x49, 0x46, 0x00,
                0x01, 0x01,
                0x00,
                0x00, 0x01, 0x00, 0x01,
                0x00, 0x00
        };
        outputStream.write(jfif);
    }

    /** Writes a generic COM segment (type-ID 0) with attribution. */
    private void writeCommentSegment() throws IOException {
        byte[] commentBytes = STEGO_ATTRIBUTION.getBytes(StandardCharsets.UTF_8);
        int segmentLength = 2 + 1 + commentBytes.length;
        byte[] segment = new byte[2 + segmentLength];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) 0xFE;
        segment[2] = (byte) (segmentLength >> 8);
        segment[3] = (byte) segmentLength;
        segment[4] = (byte) COMMENT_TYPE_GENERIC;
        System.arraycopy(commentBytes, 0, segment, 5, commentBytes.length);
        outputStream.write(segment);
    }

    /** Writes a COM segment containing the message byte-length (type-ID 1). */
    private void writeMessageLengthSegment() throws IOException {
        int messageByteLength = messageBytes.length;
        int segmentLength = 2 + 1 + 4;
        byte[] segment = new byte[2 + segmentLength];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) 0xFE;
        segment[2] = (byte) (segmentLength >> 8);
        segment[3] = (byte) segmentLength;
        segment[4] = (byte) COMMENT_TYPE_MSG_LEN;
        segment[5] = (byte) (messageByteLength >> 24);
        segment[6] = (byte) (messageByteLength >> 16);
        segment[7] = (byte) (messageByteLength >> 8);
        segment[8] = (byte) messageByteLength;
        outputStream.write(segment);
    }

    /** Writes a COM segment containing the AES-encrypted password (type-ID 2). */
    private void writePasswordSegment() throws IOException {
        String encrypted = AESCipher.encrypt(AES_KEY, AES_IV, password);
        byte[] encryptedBytes = encrypted.getBytes(StandardCharsets.UTF_8);
        int segmentLength = 2 + 1 + encryptedBytes.length;
        byte[] segment = new byte[2 + segmentLength];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) 0xFE;
        segment[2] = (byte) (segmentLength >> 8);
        segment[3] = (byte) segmentLength;
        segment[4] = (byte) COMMENT_TYPE_PASSWORD;
        System.arraycopy(encryptedBytes, 0, segment, 5, encryptedBytes.length);
        outputStream.write(segment);
    }

    /** Writes two DQT segments (table 0 and table 1 with identical values). */
    private void writeQuantizationTables() throws IOException {
        int[] zigzagValues = ZigZagOrder.toZigZag(quantTable.matrix);
        byte[] dqtSegment = new byte[69];
        dqtSegment[0] = (byte) 0xFF;
        dqtSegment[1] = (byte) 0xDB;
        dqtSegment[2] = 0x00;
        dqtSegment[3] = 0x43;

        for (int tableId = 0; tableId < 2; tableId++) {
            dqtSegment[4] = (byte) tableId;
            for (int j = 0; j < BLOCK_PIXELS; j++) {
                dqtSegment[5 + j] = (byte) zigzagValues[j];
            }
            outputStream.write(dqtSegment);
        }
    }

    /** Writes the SOF0 (Start of Frame - baseline) segment. */
    private void writeStartOfFrame() throws IOException {
        int componentCount = NUM_COMPONENTS;
        int segmentLength = 8 + 3 * componentCount;
        byte[] sof = new byte[2 + segmentLength];
        int idx = 0;
        sof[idx++] = (byte) 0xFF;
        sof[idx++] = (byte) 0xC0;
        sof[idx++] = (byte) (segmentLength >> 8);
        sof[idx++] = (byte) segmentLength;
        sof[idx++] = 0x08;
        sof[idx++] = (byte) (imageHeight >> 8);
        sof[idx++] = (byte) imageHeight;
        sof[idx++] = (byte) (imageWidth >> 8);
        sof[idx++] = (byte) imageWidth;
        sof[idx++] = (byte) componentCount;
        for (int i = 0; i < componentCount; i++) {
            sof[idx++] = (byte) COMPONENT_IDS[i];
            sof[idx++] = (byte) ((H_SAMPLING_FACTORS[i] << 4) | V_SAMPLING_FACTORS[i]);
            sof[idx++] = (byte) QUANT_TABLE_NUMBERS[i];
        }
        outputStream.write(sof);
    }

    /** Writes four DHT segments (DC luma, AC luma, DC chroma, AC chroma). */
    private void writeHuffmanTables() throws IOException {
        loadStandardTable(JPEGHuffmanTable.StdDCLuminance, 0, 0x00);
        loadStandardTable(JPEGHuffmanTable.StdACLuminance, 1, 0x10);
        loadStandardTable(JPEGHuffmanTable.StdDCChrominance, 2, 0x01);
        loadStandardTable(JPEGHuffmanTable.StdACChrominance, 3, 0x11);
    }

    private void loadStandardTable(JPEGHuffmanTable stdTable, int tableIndex,
            int classAndId) throws IOException {
        short[] lengths = stdTable.getLengths();
        int[] counts = new int[16];
        for (int i = 0; i < 16; i++) {
            counts[i] = lengths[i];
        }

        EncoderHuffmanTable table = new EncoderHuffmanTable(counts);
        int symbolCount = table.getSymbolCount();

        short[] stdValues = stdTable.getValues();
        byte[] values = new byte[symbolCount];
        for (int i = 0; i < symbolCount; i++) {
            values[i] = (byte) (stdValues[i] & 0xFF);
        }
        table.setSymbolValues(values);

        huffmanTables[tableIndex] = table;
        writeDhtSegment(table, classAndId);
    }

    private void writeDhtSegment(EncoderHuffmanTable table, int classAndId) throws IOException {
        int symbolCount = table.getSymbolCount();
        int segmentLength = 2 + 1 + 16 + symbolCount;
        byte[] segment = new byte[2 + segmentLength];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) 0xC4;
        segment[2] = (byte) (segmentLength >> 8);
        segment[3] = (byte) segmentLength;
        segment[4] = (byte) classAndId;
        for (int i = 0; i < 16; i++) {
            segment[5 + i] = (byte) table.getBitCount(i + 1);
        }
        for (int i = 0; i < symbolCount; i++) {
            segment[21 + i] = (byte) table.getSymbolValue(i);
        }
        outputStream.write(segment);
    }

    /** Writes the SOS (Start of Scan) segment. */
    private void writeStartOfScan() throws IOException {
        int componentCount = NUM_COMPONENTS;
        int segmentLength = 6 + 2 * componentCount;
        byte[] sos = new byte[2 + segmentLength];
        int idx = 0;
        sos[idx++] = (byte) 0xFF;
        sos[idx++] = (byte) 0xDA;
        sos[idx++] = (byte) (segmentLength >> 8);
        sos[idx++] = (byte) segmentLength;
        sos[idx++] = (byte) componentCount;
        for (int i = 0; i < componentCount; i++) {
            sos[idx++] = (byte) COMPONENT_IDS[i];
            sos[idx++] = (byte) ((DC_TABLE_NUMBERS[i] << 4) | AC_TABLE_NUMBERS[i]);
        }
        sos[idx++] = 0x00; // Ss (spectral selection start)
        sos[idx++] = 0x3F; // Se (spectral selection end = 63)
        sos[idx] = 0x00; // Ah=0, Al=0
        outputStream.write(sos);
    }
}
