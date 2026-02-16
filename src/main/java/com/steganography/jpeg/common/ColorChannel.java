package com.steganography.jpeg.common;

import java.nio.ShortBuffer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Represents one colour channel (Y, Cb, or Cr) during encoding or decoding.
 * Holds the quantised DCT coefficients in zigzag order for every 8x8 block.
 */
@SuppressFBWarnings(value = { "EI_EXPOSE_REP",
        "EI_EXPOSE_REP2" }, justification = "Buffers intentionally shared for encoder pipeline")
public class ColorChannel {

    private final int id;

    /** Quantised DCT coefficients stored block-by-block (64 shorts per block). */
    private ShortBuffer coefficientBuffer;

    /** Previous DC value for differential coding. */
    private short dcPrediction;

    /** Huffman table indices used during entropy coding. */
    private int dcTableIndex;
    private int acTableIndex;

    /** Dequantisation table (decoder only). */
    private byte[] dequantTable;

    /** Sampling factors and layout (used by the decoder). */
    private int horizontalSampling = 1;
    private int verticalSampling = 1;
    private int width;
    private int height;
    private int minRequiredWidth;
    private int minRequiredHeight;
    private int outputPosition;
    private int upsamplerFlags;

    /**
     * Decoder-side constructor. Use when parsing headers; buffer is not
     * pre-allocated.
     *
     * @param id component ID (1=Y, 2=Cb, 3=Cr)
     */
    public ColorChannel(int id) {
        this.id = id;
    }

    /**
     * Encoder-side constructor with a pre-allocated coefficient buffer.
     *
     * @param id          component ID (1=Y, 2=Cb, 3=Cr)
     * @param imageWidth  image width in pixels
     * @param imageHeight image height in pixels
     */
    public ColorChannel(int id, int imageWidth, int imageHeight) {
        this.id = id;
        this.coefficientBuffer = ShortBuffer.allocate(imageWidth * imageHeight);
    }

    public int getId() {
        return id;
    }

    public ShortBuffer getCoefficientBuffer() {
        return coefficientBuffer;
    }

    public short getDcPrediction() {
        return dcPrediction;
    }

    public void setDcPrediction(short v) {
        this.dcPrediction = v;
    }

    public int getDcTableIndex() {
        return dcTableIndex;
    }

    public void setDcTableIndex(int i) {
        this.dcTableIndex = i;
    }

    public int getAcTableIndex() {
        return acTableIndex;
    }

    public void setAcTableIndex(int i) {
        this.acTableIndex = i;
    }

    public byte[] getDequantTable() {
        return dequantTable;
    }

    public void setDequantTable(byte[] t) {
        this.dequantTable = t;
    }

    public int getHorizontalSampling() {
        return horizontalSampling;
    }

    public void setHorizontalSampling(int v) {
        this.horizontalSampling = v;
    }

    public int getVerticalSampling() {
        return verticalSampling;
    }

    public void setVerticalSampling(int v) {
        this.verticalSampling = v;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int w) {
        this.width = w;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int h) {
        this.height = h;
    }

    public int getMinRequiredWidth() {
        return minRequiredWidth;
    }

    public void setMinRequiredWidth(int w) {
        this.minRequiredWidth = w;
    }

    public int getMinRequiredHeight() {
        return minRequiredHeight;
    }

    public void setMinRequiredHeight(int h) {
        this.minRequiredHeight = h;
    }

    public int getOutputPosition() {
        return outputPosition;
    }

    public void setOutputPosition(int p) {
        this.outputPosition = p;
    }

    public int getUpsamplerFlags() {
        return upsamplerFlags;
    }

    public void setUpsamplerFlags(int f) {
        this.upsamplerFlags = f;
    }

    /** Resets the DC prediction to zero (used at restart markers). */
    public void resetDcPrediction() {
        this.dcPrediction = 0;
    }
}
