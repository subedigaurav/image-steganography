package com.steganography.jpeg.decoder;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ShortBuffer;
import java.util.Arrays;

import com.steganography.config.SteganographyConfig;
import static com.steganography.config.SteganographyConfig.BLOCK_PIXELS;
import com.steganography.crypto.AESCipher;
import com.steganography.jpeg.common.JpegMarkers;

/**
 * Baseline JPEG decoder that extracts raw quantised DCT coefficients.
 *
 * This decoder is designed for steganographic extraction: it reads the
 * entropy-coded scan data and outputs the quantised coefficients per
 * component without performing IDCT or colour conversion.
 */
public class JpegDecoder {

    private final InputStream source;
    private final byte[] readBuffer = new byte[4096];
    private int readBufferPosition;
    private int readBufferValid;
    private boolean ignoreIOErrors;

    private boolean headerDecoded;
    private boolean insideScan;
    private boolean foundEndOfImage;
    private int currentMcuRow;

    private int codeBuffer;
    private int codeBitsAvailable;
    private int pendingMarker = JpegMarkers.NONE;
    private boolean noMoreData;

    private int imageWidth;
    private int imageHeight;
    private int mcuColumnsCount;
    private int mcuRowsCount;

    private DecoderColorChannel[] components;
    private DecoderColorChannel[] scanOrder;

    private final DecoderHuffmanTable[] huffmanTables = new DecoderHuffmanTable[8];
    private final byte[][] dequantTables = new byte[4][BLOCK_PIXELS];
    private final int[] tempCounts = new int[BLOCK_PIXELS];

    private int restartInterval;
    private int mcusUntilRestart;

    private String storedPassword;
    private int storedMessageLength;

    private final short[] blockData = new short[BLOCK_PIXELS];

    /**
     * Creates a decoder for the given JPEG input stream.
     *
     * @param source the JPEG byte stream
     */
    public JpegDecoder(InputStream source) {
        this.source = source;
    }

    /** Returns the image width in pixels. */
    public int getImageWidth() {
        ensureHeaderDecoded();
        return imageWidth;
    }

    /** Returns the image height in pixels. */
    public int getImageHeight() {
        ensureHeaderDecoded();
        return imageHeight;
    }

    /** Returns the number of colour components (1 or 3). */
    public int getNumComponents() {
        ensureHeaderDecoded();
        return components.length;
    }

    /** Returns the number of MCU rows. */
    public int getMcuRowCount() {
        ensureHeaderDecoded();
        return mcuRowsCount;
    }

    /** Returns the number of MCU columns. */
    public int getMcuColumnCount() {
        ensureHeaderDecoded();
        return mcuColumnsCount;
    }

    /**
     * Returns the password stored in the image (AES-decrypted), or null if absent.
     */
    public String getStoredPassword() {
        return storedPassword;
    }

    /** Returns the embedded message length in bytes, or 0 if absent. */
    public int getStoredMessageLength() {
        return storedMessageLength;
    }

    /**
     * Parses all headers up to (and including) the SOS marker.
     * Returns {@code true} if scan data is ready to be decoded.
     */
    public boolean startDecode() throws IOException {
        if (insideScan) {
            throw new IllegalStateException("Decode already started");
        }
        if (foundEndOfImage) {
            return false;
        }

        decodeHeader();

        int marker = readMarker();
        while (marker != JpegMarkers.EOI) {
            if (marker == JpegMarkers.SOS) {
                processScanHeader();
                insideScan = true;
                currentMcuRow = 0;
                resetBitStream();
                return true;
            }
            processMarker(marker);
            marker = readMarker();
        }
        foundEndOfImage = true;
        return false;
    }

    /**
     * Decodes the specified number of MCU rows and writes the quantised
     * DCT coefficients into the provided buffers (one per component).
     *
     * @param buffers    one ShortBuffer per component (Y, Cb, Cr)
     * @param numMcuRows number of MCU rows to decode
     * @throws IOException if the scan data is invalid
     */
    public void decodeDctCoefficients(ShortBuffer[] buffers, int numMcuRows) throws IOException {
        if (!insideScan) {
            throw new IllegalStateException("Decode not started");
        }
        if (numMcuRows <= 0 || currentMcuRow + numMcuRows > mcuRowsCount) {
            throw new IllegalArgumentException("Invalid MCU row count");
        }
        int scanComponentCount = scanOrder.length;
        if (scanComponentCount != components.length) {
            throw new UnsupportedOperationException("All components must be decoded at once");
        }
        if (scanComponentCount > buffers.length) {
            throw new IllegalArgumentException("Not enough output buffers");
        }

        // Record starting positions
        for (int i = 0; i < scanComponentCount; i++) {
            scanOrder[i].outputPosition = buffers[i].position();
        }

        outer: for (int mcuRow = 0; mcuRow < numMcuRows; mcuRow++) {
            currentMcuRow++;
            for (int mcuCol = 0; mcuCol < mcuColumnsCount; mcuCol++) {
                for (int compIdx = 0; compIdx < scanComponentCount; compIdx++) {
                    DecoderColorChannel channel = scanOrder[compIdx];
                    ShortBuffer outBuffer = buffers[compIdx];

                    int stride = BLOCK_PIXELS * channel.horizontalBlocks * mcuColumnsCount;
                    int pos = channel.outputPosition
                            + BLOCK_PIXELS * mcuCol * channel.horizontalBlocks
                            + mcuRow * channel.verticalBlocks * stride;

                    for (int vBlock = 0; vBlock < channel.verticalBlocks; vBlock++) {
                        outBuffer.position(pos);
                        for (int hBlock = 0; hBlock < channel.horizontalBlocks; hBlock++) {
                            try {
                                decodeBlock(blockData, channel);
                            } catch (ArrayIndexOutOfBoundsException ex) {
                                throw new IOException("Bad Huffman code in scan data");
                            }
                            outBuffer.put(blockData);
                        }
                        pos += stride;
                    }
                }
                if (--mcusUntilRestart <= 0 && !handleRestart()) {
                    break outer;
                }
            }
        }

        finaliseDecodePass();

        // Advance buffer positions
        for (int i = 0; i < scanComponentCount; i++) {
            DecoderColorChannel ch = scanOrder[i];
            int stride = BLOCK_PIXELS * ch.horizontalBlocks * mcuColumnsCount;
            buffers[i].position(ch.outputPosition + numMcuRows * ch.verticalBlocks * stride);
        }
    }

    private void decodeHeader() throws IOException {
        if (headerDecoded) {
            return;
        }
        headerDecoded = true;

        int marker = readMarker();
        if (marker != JpegMarkers.SOI) {
            throw new IOException("Missing SOI marker");
        }

        marker = readMarker();
        while (marker != JpegMarkers.SOF0 && marker != JpegMarkers.SOF1) {
            processMarker(marker);
            marker = readMarker();
            while (marker == JpegMarkers.NONE) {
                marker = readMarker();
            }
        }
        processStartOfFrame();
    }

    private void processMarker(int marker) throws IOException {
        // APP markers (0xE0 - 0xEF): skip
        if (marker >= 0xE0 && marker <= 0xEF) {
            int length = readUint16() - 2;
            if (length < 0) {
                throw new IOException("Bad APP marker length");
            }
            skipBytes(length);
            return;
        }

        switch (marker) {
            case JpegMarkers.NONE -> throw new IOException("Expected marker");
            case JpegMarkers.SOF2 -> throw new IOException("Progressive JPEG not supported");

            case JpegMarkers.COM -> processCommentMarker();
            case JpegMarkers.DRI -> processRestartInterval();
            case JpegMarkers.DQT -> processQuantizationTable();
            case JpegMarkers.DHT -> processHuffmanTableDefinition();

            default -> throw new IOException("Unknown marker: 0x" + Integer.toHexString(marker));
        }
    }

    private void processCommentMarker() throws IOException {
        int length = readUint16();
        int remaining = length - 2;
        if (remaining <= 0) {
            return;
        }

        int typeId = readUint8();
        remaining--;

        switch (typeId) {
            case SteganographyConfig.COMMENT_TYPE_PASSWORD -> {
                StringBuilder encrypted = new StringBuilder();
                for (int i = 0; i < remaining; i++) {
                    encrypted.append((char) readUint8());
                }
                storedPassword = AESCipher.decrypt(
                        SteganographyConfig.AES_KEY, SteganographyConfig.AES_IV, encrypted.toString());
            }
            case SteganographyConfig.COMMENT_TYPE_MSG_LEN -> {
                storedMessageLength = (readUint8() << 24) | (readUint8() << 16)
                        | (readUint8() << 8) | readUint8();
                remaining -= 4;
                if (remaining > 0) {
                    skipBytes(remaining);
                }
            }
            default -> {
                if (remaining > 0) {
                    skipBytes(remaining);
                }
            }
        }
    }

    private void processRestartInterval() throws IOException {
        if (readUint16() != 4) {
            throw new IOException("Bad DRI length");
        }
        restartInterval = readUint16();
    }

    private void processQuantizationTable() throws IOException {
        int remaining = readUint16() - 2;
        while (remaining >= 65) {
            int header = readUint8();
            int precision = header >> 4;
            int tableId = header & 0x0F;
            if (precision != 0) {
                throw new IOException("Only 8-bit quantisation supported");
            }
            if (tableId > 3) {
                throw new IOException("Bad quantisation table ID");
            }
            readBytes(dequantTables[tableId], 0, BLOCK_PIXELS);
            remaining -= 65;
        }
        if (remaining != 0) {
            throw new IOException("Bad DQT segment length");
        }
    }

    private void processHuffmanTableDefinition() throws IOException {
        int remaining = readUint16() - 2;
        while (remaining > 17) {
            int header = readUint8();
            int tableClass = header >> 4; // 0 = DC, 1 = AC
            int tableId = header & 0x0F;
            if (tableClass > 1 || tableId > 3) {
                throw new IOException("Bad DHT header");
            }

            for (int i = 0; i < 16; i++) {
                tempCounts[i] = readUint8();
            }

            DecoderHuffmanTable table = new DecoderHuffmanTable(tempCounts);
            int symbolCount = table.getNumSymbols();
            remaining -= 17 + symbolCount;
            if (remaining < 0) {
                throw new IOException("Bad DHT segment length");
            }

            readBytes(table.symbolValues, 0, symbolCount);
            huffmanTables[tableClass * 4 + tableId] = table;
        }
        if (remaining != 0) {
            throw new IOException("Bad DHT segment length");
        }
    }

    private void processStartOfFrame() throws IOException {
        int frameLength = readUint16();
        if (frameLength < 11) {
            throw new IOException("Bad SOF length");
        }
        if (readUint8() != 8) {
            throw new IOException("Only 8-bit JPEG supported");
        }

        imageHeight = readUint16();
        imageWidth = readUint16();
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IOException("Invalid image dimensions");
        }

        int componentCount = readUint8();
        if (componentCount != 3 && componentCount != 1) {
            throw new IOException("Bad component count");
        }
        if (frameLength != 8 + 3 * componentCount) {
            throw new IOException("Bad SOF length");
        }

        int maxH = 1;
        int maxV = 1;
        components = new DecoderColorChannel[componentCount];

        for (int i = 0; i < componentCount; i++) {
            DecoderColorChannel ch = new DecoderColorChannel(readUint8());
            int samplingFactors = readUint8();
            int quantTableId = readUint8();

            ch.horizontalBlocks = samplingFactors >> 4;
            ch.verticalBlocks = samplingFactors & 0x0F;
            if (ch.horizontalBlocks < 1 || ch.horizontalBlocks > 4) {
                throw new IOException("Bad H sampling");
            }
            if (ch.verticalBlocks < 1 || ch.verticalBlocks > 4) {
                throw new IOException("Bad V sampling");
            }
            if (quantTableId > 3) {
                throw new IOException("Bad quantisation table reference");
            }

            ch.dequantTable = dequantTables[quantTableId];
            maxH = Math.max(maxH, ch.horizontalBlocks);
            maxV = Math.max(maxV, ch.verticalBlocks);
            components[i] = ch;
        }

        int mcuWidth = maxH * 8;
        int mcuHeight = maxV * 8;
        mcuColumnsCount = (imageWidth + mcuWidth - 1) / mcuWidth;
        mcuRowsCount = (imageHeight + mcuHeight - 1) / mcuHeight;

        for (DecoderColorChannel ch : components) {
            ch.width = (imageWidth * ch.horizontalBlocks + maxH - 1) / maxH;
            ch.height = (imageHeight * ch.verticalBlocks + maxV - 1) / maxV;
            ch.minRequiredWidth = mcuColumnsCount * ch.horizontalBlocks * 8;
            ch.minRequiredHeight = mcuRowsCount * ch.verticalBlocks * 8;
            if (ch.horizontalBlocks < maxH) {
                ch.upsamplerFlags |= 1;
            }
            if (ch.verticalBlocks < maxV) {
                ch.upsamplerFlags |= 2;
            }
        }
    }

    private void processScanHeader() throws IOException {
        int length = readUint16();
        int componentCount = readUint8();
        if (componentCount < 1 || componentCount > 4) {
            throw new IOException("Bad SOS component count");
        }
        if (length != 6 + 2 * componentCount) {
            throw new IOException("Bad SOS length");
        }

        scanOrder = new DecoderColorChannel[componentCount];

        for (int i = 0; i < componentCount; i++) {
            int componentId = readUint8();
            int tableSpec = readUint8();
            int dcTableId = tableSpec >> 4;
            int acTableId = tableSpec & 0x0F;

            for (DecoderColorChannel ch : components) {
                if (ch.id == componentId) {
                    if (dcTableId > 3 || acTableId > 3) {
                        throw new IOException("Bad Huffman table index");
                    }
                    ch.huffmanDC = huffmanTables[dcTableId];
                    ch.huffmanAC = huffmanTables[acTableId + 4];
                    if (ch.huffmanDC == null || ch.huffmanAC == null) {
                        throw new IOException("Referenced Huffman table not defined");
                    }
                    scanOrder[i] = ch;
                    break;
                }
            }
            if (scanOrder[i] == null) {
                throw new IOException("Unknown component in SOS");
            }
        }

        if (readUint8() != 0) {
            throw new IOException("Bad SOS spectral selection start");
        }
        readUint8(); // Se
        if (readUint8() != 0) {
            throw new IOException("Bad SOS successive approximation");
        }
    }

    private void decodeBlock(short[] data, DecoderColorChannel channel) throws IOException {
        Arrays.fill(data, (short) 0);

        // DC coefficient (differential, quantised)
        int dcCategory = decodeHuffmanSymbol(channel.huffmanDC);
        int dc = channel.dcPrediction;
        if (dcCategory > 0) {
            dc += receiveAndExtend(dcCategory);
            channel.dcPrediction = dc;
        }
        data[0] = (short) dc;

        // AC coefficients (quantised)
        DecoderHuffmanTable acTable = channel.huffmanAC;
        int k = 1;
        do {
            int runSizeSymbol = decodeHuffmanSymbol(acTable);
            int zeroPadding = runSizeSymbol >> 4;
            int acCategory = runSizeSymbol & 0x0F;
            k += zeroPadding;
            if (acCategory != 0) {
                data[k] = (short) receiveAndExtend(acCategory);
            } else if (runSizeSymbol != 0xF0) {
                break; // EOB
            }
        } while (++k < BLOCK_PIXELS);
    }

    private int decodeHuffmanSymbol(DecoderHuffmanTable table) throws IOException {
        if (codeBitsAvailable < 16) {
            fillCodeBuffer();
        }

        int index = table.fastLookup[codeBuffer >>> (32 - DecoderHuffmanTable.FAST_BITS)] & 0xFF;
        if (index < 0xFF) {
            int size = table.symbolSizes[index] & 0xFF;
            codeBuffer <<= size;
            codeBitsAvailable -= size;
            return table.symbolValues[index] & 0xFF;
        }
        return decodeHuffmanSlow(table);
    }

    private int decodeHuffmanSlow(DecoderHuffmanTable table) throws IOException {
        int codeValue = codeBuffer >>> 16;
        int bitLen = DecoderHuffmanTable.FAST_BITS + 1;
        while (codeValue >= table.maxCodeAtLength[bitLen]) {
            bitLen++;
        }
        int index = (codeValue >>> (16 - bitLen)) + table.deltaAtLength[bitLen];
        codeBuffer <<= bitLen;
        codeBitsAvailable -= bitLen;
        return table.symbolValues[index] & 0xFF;
    }

    private int receiveAndExtend(int numBits) throws IOException {
        if (codeBitsAvailable < 24) {
            fillCodeBuffer();
        }
        int value = codeBuffer >>> (32 - numBits);
        codeBuffer <<= numBits;
        codeBitsAvailable -= numBits;
        int threshold = 1 << (numBits - 1);
        if (value < threshold) {
            value -= threshold * 2 - 1;
        }
        return value;
    }

    private void fillCodeBuffer() throws IOException {
        do {
            int byteValue = 0;
            if (!noMoreData) {
                byteValue = readUint8();
                if (byteValue == 0xFF) {
                    checkForMarkerInStream();
                }
            }
            codeBuffer |= byteValue << (24 - codeBitsAvailable);
            codeBitsAvailable += 8;
        } while (codeBitsAvailable <= 24);
    }

    private void checkForMarkerInStream() throws IOException {
        int next = readUint8();
        if (next != 0) {
            pendingMarker = next;
            noMoreData = true;
        }
    }

    private void resetBitStream() {
        codeBitsAvailable = 0;
        codeBuffer = 0;
        noMoreData = false;
        pendingMarker = JpegMarkers.NONE;
        mcusUntilRestart = restartInterval != 0 ? restartInterval : Integer.MAX_VALUE;
        for (DecoderColorChannel ch : components) {
            ch.resetDcPrediction();
        }
    }

    private boolean handleRestart() throws IOException {
        if (codeBitsAvailable < 24) {
            fillCodeBuffer();
        }
        if (pendingMarker >= 0xD0 && pendingMarker <= 0xD7) {
            resetBitStream();
            return true;
        }
        return false;
    }

    private void finaliseDecodePass() throws IOException {
        if (currentMcuRow >= mcuRowsCount || pendingMarker != JpegMarkers.NONE) {
            insideScan = false;
            if (pendingMarker == JpegMarkers.NONE) {
                skipPaddingBytes();
            }
        }
    }

    private void skipPaddingBytes() throws IOException {
        int value;
        do {
            value = readUint8();
        } while (value == 0);
        if (value == 0xFF) {
            pendingMarker = readUint8();
        }
    }

    private void fetchBuffer() throws IOException {
        try {
            readBufferPosition = 0;
            readBufferValid = source.read(readBuffer);
            if (readBufferValid <= 0) {
                throw new EOFException();
            }
        } catch (IOException ex) {
            readBufferValid = 2;
            readBuffer[0] = (byte) 0xFF;
            readBuffer[1] = (byte) 0xD9;
            if (!ignoreIOErrors) {
                throw ex;
            }
        }
    }

    private int readUint8() throws IOException {
        if (readBufferPosition == readBufferValid) {
            fetchBuffer();
        }
        return readBuffer[readBufferPosition++] & 0xFF;
    }

    private int readUint16() throws IOException {
        return (readUint8() << 8) | readUint8();
    }

    private void readBytes(byte[] dest, int offset, int length) throws IOException {
        while (length > 0) {
            int available = readBufferValid - readBufferPosition;
            if (available == 0) {
                fetchBuffer();
                continue;
            }
            int toCopy = Math.min(available, length);
            System.arraycopy(readBuffer, readBufferPosition, dest, offset, toCopy);
            offset += toCopy;
            length -= toCopy;
            readBufferPosition += toCopy;
        }
    }

    private void skipBytes(int count) throws IOException {
        while (count > 0) {
            int available = readBufferValid - readBufferPosition;
            if (count > available) {
                count -= available;
                fetchBuffer();
            } else {
                readBufferPosition += count;
                return;
            }
        }
    }

    private int readMarker() throws IOException {
        int marker = pendingMarker;
        if (marker != JpegMarkers.NONE) {
            pendingMarker = JpegMarkers.NONE;
            return marker;
        }
        marker = readUint8();
        if (marker != 0xFF) {
            return JpegMarkers.NONE;
        }
        do {
            marker = readUint8();
        } while (marker == 0xFF);
        return marker;
    }

    private void ensureHeaderDecoded() {
        if (!headerDecoded) {
            throw new IllegalStateException("Header must be decoded first");
        }
    }
}
