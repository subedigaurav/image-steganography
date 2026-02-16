package com.steganography.jpeg.common;

/**
 * Standard JPEG marker constants. The high byte is always 0xFF; the low byte
 * identifies the marker type.
 */
public final class JpegMarkers {
    /** Start of Image. */
    public static final int SOI = 0xD8;
    /** End of Image. */
    public static final int EOI = 0xD9;
    /** Start of Frame (baseline DCT). */
    public static final int SOF0 = 0xC0;
    /** Start of Frame (extended sequential). */
    public static final int SOF1 = 0xC1;
    /** Start of Frame (progressive, unsupported). */
    public static final int SOF2 = 0xC2;
    /** Define Huffman Table. */
    public static final int DHT = 0xC4;
    /** Define Quantization Table. */
    public static final int DQT = 0xDB;
    /** Define Restart Interval. */
    public static final int DRI = 0xDD;
    /** Start of Scan. */
    public static final int SOS = 0xDA;
    /** Comment. */
    public static final int COM = 0xFE;
    /** JFIF application marker. */
    public static final int APP0 = 0xE0;
    /** Sentinel when no marker has been read. */
    public static final int NONE = 0xFF;
}
