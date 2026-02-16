package com.steganography.jpeg.common;

/**
 * Converts an 8x8 block between matrix form and zigzag-scanned 1-D form
 * as required by the JPEG standard.
 */
public final class ZigZagOrder {

    /**
     * Zigzag-scans a {@code short[][]} matrix into a 1-D {@code int[]} array.
     *
     * @param matrix the matrix to scan
     * @return the 1-D array of integers
     */
    public static int[] toZigZag(short[][] matrix) {
        return scan(matrix.length, matrix[0].length,
                (row, col) -> matrix[row][col]);
    }

    /**
     * Zigzag-scans an {@code int[][]} matrix into a 1-D {@code int[]} array.
     *
     * @param matrix the matrix to scan
     * @return the 1-D array of integers
     */
    public static int[] toZigZag(int[][] matrix) {
        return scan(matrix.length, matrix[0].length,
                (row, col) -> matrix[row][col]);
    }

    @FunctionalInterface
    private interface CellReader {
        int read(int row, int col);
    }

    private static int[] scan(int rows, int cols, CellReader reader) {
        int[] result = new int[rows * cols];
        int index = 0;
        for (int diagonal = 0; diagonal < rows + cols - 1; diagonal++) {
            if ((diagonal & 1) != 0) {
                // top-right to bottom-left
                int row = diagonal < cols ? 0 : diagonal - cols + 1;
                int col = diagonal < cols ? diagonal : cols - 1;
                while (row < rows && col >= 0) {
                    result[index++] = reader.read(row++, col--);
                }
            } else {
                // bottom-left to top-right
                int row = diagonal < rows ? diagonal : rows - 1;
                int col = diagonal < rows ? 0 : diagonal - rows + 1;
                while (row >= 0 && col < cols) {
                    result[index++] = reader.read(row--, col++);
                }
            }
        }
        return result;
    }
}
