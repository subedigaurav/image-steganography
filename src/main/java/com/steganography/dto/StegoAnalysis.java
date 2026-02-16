package com.steganography.dto;

/**
 * Result of analyzing a stego image for visualization.
 *
 * Provides dimensions, block layout, message metadata, and computed values
 * for rendering the block overlay and statistics.
 *
 * @param imageWidth         image width in pixels
 * @param imageHeight        image height in pixels
 * @param mcuCols            number of MCU columns (8×8 blocks)
 * @param mcuRows            number of MCU rows
 * @param messageLengthBytes length of embedded message in bytes
 * @param hasPassword        whether the image is password-protected
 * @param usedMcus           number of MCUs containing embedded data
 * @param totalCapacityBytes maximum message capacity in bytes
 */
public record StegoAnalysis(
        int imageWidth,
        int imageHeight,
        int mcuCols,
        int mcuRows,
        int messageLengthBytes,
        boolean hasPassword,
        int usedMcus,
        int totalCapacityBytes) {

    /**
     * Raw parameters from JPEG decoding.
     * Used to construct {@link StegoAnalysis} without passing individual arguments.
     *
     * @param imageWidth         image width in pixels
     * @param imageHeight        image height in pixels
     * @param mcuCols            number of MCU columns
     * @param mcuRows            number of MCU rows
     * @param messageLengthBytes embedded message length in bytes
     * @param hasPassword        whether password-protected
     * @param usedMcus           MCUs with embedded data
     * @param totalCapacityBytes maximum capacity in bytes
     */
    public record Params(
            int imageWidth,
            int imageHeight,
            int mcuCols,
            int mcuRows,
            int messageLengthBytes,
            boolean hasPassword,
            int usedMcus,
            int totalCapacityBytes) {
    }

    /**
     * Creates an analysis from a params object.
     *
     * @param p the decoded analysis parameters
     * @return the analysis instance
     */
    public StegoAnalysis(Params p) {
        this(p.imageWidth(), p.imageHeight(), p.mcuCols(), p.mcuRows(),
                p.messageLengthBytes(), p.hasPassword(), p.usedMcus(), p.totalCapacityBytes());
    }

    /** Total number of 8×8 blocks in the image. */
    public int totalMcus() {
        return mcuCols * mcuRows;
    }

    /** Whether any message data is embedded. */
    public boolean hasEmbeddedData() {
        return messageLengthBytes > 0;
    }

    /** Capacity usage as a percentage (0–100). */
    public double capacityUsedPercent() {
        return totalMcus() > 0 ? 100.0 * usedMcus / totalMcus() : 0;
    }

    /** Formatted dimensions string (e.g. "640 × 480"). */
    public String dimensions() {
        return imageWidth + " × " + imageHeight;
    }
}
