package com.steganography.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.steganography.config.SteganographyConfig;
import com.steganography.crypto.CaesarCipher;
import com.steganography.dto.StegoAnalysis;
import com.steganography.exception.InvalidImageException;
import com.steganography.exception.InvalidPasswordException;
import com.steganography.exception.MessageTooLongException;
import com.steganography.exception.SteganographyException;
import com.steganography.jpeg.decoder.JpegDecoder;
import com.steganography.jpeg.encoder.JpegEncoder;

/**
 * Core service that orchestrates encoding (hiding) and decoding (extracting)
 * secret messages in JPEG images using DCT-domain steganography.
 */
@Service
public class SteganographyService {

    /**
     * Encodes a secret message into a cover image and returns the resulting
     * stego-JPEG as a byte array.
     *
     * @param coverImageStream input stream of the cover image (any format
     *                         ImageIO supports)
     * @param message          the secret message to hide
     * @param password         password to protect the message
     * @param quality          JPEG quality (1-100)
     * @return the stego-JPEG bytes
     */
    public byte[] encode(InputStream coverImageStream, String message,
            String password, int quality) throws IOException {

        BufferedImage image = ImageIO.read(coverImageStream);
        if (image == null) {
            throw new InvalidImageException("The uploaded file is not a valid image.");
        }

        image = cropToBlockBoundary(image);

        int capacityBytes = calculateCapacity(image.getWidth(), image.getHeight());
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        if (messageBytes.length > capacityBytes) {
            throw new MessageTooLongException(messageBytes.length, capacityBytes);
        }

        byte[] obfuscatedBytes = CaesarCipher.encrypt(message, SteganographyConfig.CAESAR_KEY);

        ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream();
        JpegEncoder encoder = new JpegEncoder(quality, image, jpegOutput, obfuscatedBytes, password);
        encoder.encode();
        encoder.writeEndOfImage();

        return jpegOutput.toByteArray();
    }

    /**
     * Extracts the hidden message from a stego-JPEG image.
     *
     * @param stegoImageStream input stream of the stego-JPEG
     * @param password         password used during encoding
     * @return the extracted secret message
     */
    public String decode(InputStream stegoImageStream, String password) throws IOException {

        JpegDecoder decoder = new JpegDecoder(stegoImageStream);
        if (!decoder.startDecode()) {
            throw new InvalidImageException("Failed to decode JPEG or no hidden data found.");
        }

        String storedPassword = decoder.getStoredPassword();
        if (storedPassword == null || !storedPassword.equals(password)) {
            throw new InvalidPasswordException();
        }

        int messageLength = decoder.getStoredMessageLength();
        if (messageLength <= 0) {
            throw new SteganographyException("No hidden message found in this image.");
        }

        int mcuRows = decoder.getMcuRowCount();
        int mcuCols = decoder.getMcuColumnCount();
        int blocksPerComponent = mcuRows * mcuCols;

        ShortBuffer[] channelBuffers = new ShortBuffer[SteganographyConfig.NUM_COMPONENTS];
        for (int i = 0; i < SteganographyConfig.NUM_COMPONENTS; i++) {
            channelBuffers[i] = ShortBuffer.allocate(SteganographyConfig.BLOCK_PIXELS * blocksPerComponent);
        }
        decoder.decodeDctCoefficients(channelBuffers, mcuRows);

        byte[] obfuscatedBytes = extractMessageFromCoefficients(
                channelBuffers[SteganographyConfig.STEGO_CHANNEL], messageLength);

        return CaesarCipher.decrypt(obfuscatedBytes, SteganographyConfig.CAESAR_KEY);
    }

    /**
     * Analyzes a stego image and returns metadata for visualization.
     * No password is required; only header metadata is read.
     *
     * @param stegoImageStream input stream of the stego JPEG
     * @return analysis result with dimensions, message length, and block info
     */
    public StegoAnalysis analyze(InputStream stegoImageStream) throws IOException {
        JpegDecoder decoder = new JpegDecoder(stegoImageStream);
        if (!decoder.startDecode()) {
            throw new InvalidImageException("Failed to decode JPEG or invalid image.");
        }

        int mcuRows = decoder.getMcuRowCount();
        int mcuCols = decoder.getMcuColumnCount();
        int totalMcus = mcuRows * mcuCols;
        int messageLength = decoder.getStoredMessageLength();
        int usedMcus = messageLength > 0
                ? (int) Math.ceil(messageLength * 8.0 / SteganographyConfig.BITS_PER_MCU)
                : 0;
        int totalCapacity = totalMcus * SteganographyConfig.BITS_PER_MCU / 8;

        StegoAnalysis.Params params = new StegoAnalysis.Params(
                decoder.getImageWidth(),
                decoder.getImageHeight(),
                mcuCols,
                mcuRows,
                messageLength,
                decoder.getStoredPassword() != null,
                usedMcus,
                totalCapacity);

        return new StegoAnalysis(params);
    }

    /**
     * Calculates the maximum number of message bytes that can be hidden in an
     * image of the given dimensions.
     */
    public int calculateCapacity(int width, int height) {
        int mcuCount = width / SteganographyConfig.BLOCK_SIZE * (height / SteganographyConfig.BLOCK_SIZE);
        return mcuCount * SteganographyConfig.BITS_PER_MCU / 8;
    }

    /**
     * Crops the image so both dimensions are multiples of 8.
     */
    private BufferedImage cropToBlockBoundary(BufferedImage image) {
        int width = image.getWidth() / SteganographyConfig.BLOCK_SIZE * SteganographyConfig.BLOCK_SIZE;
        int height = image.getHeight() / SteganographyConfig.BLOCK_SIZE * SteganographyConfig.BLOCK_SIZE;
        if (width == 0 || height == 0) {
            throw new InvalidImageException("Image is too small (must be at least 8x8 pixels).");
        }
        if (width != image.getWidth() || height != image.getHeight()) {
            return image.getSubimage(0, 0, width, height);
        }
        return image;
    }

    /**
     * Reads message bits from the LSBs of the Cb-channel DCT coefficients at
     * the configured embedding positions.
     */
    private byte[] extractMessageFromCoefficients(ShortBuffer cbBuffer, int messageLength) {
        cbBuffer.position(0);

        byte[] result = new byte[messageLength];
        int byteIndex = 0;
        int remainingBits = messageLength * 8;
        int requiredMcus = (int) Math.ceil(remainingBits / (double) SteganographyConfig.BITS_PER_MCU);

        short[] blockData = new short[SteganographyConfig.BLOCK_PIXELS];

        int assembledByte = 0;
        int bitsCollected = 0;

        for (int mcuIndex = 0; mcuIndex < requiredMcus && remainingBits > 0; mcuIndex++) {
            cbBuffer.get(blockData);
            int[] pattern = SteganographyConfig.EMBEDDING_PATTERNS[mcuIndex
                    % SteganographyConfig.EMBEDDING_PATTERNS.length];

            for (int j = 0; j < SteganographyConfig.BITS_PER_MCU && remainingBits > 0; j++) {
                assembledByte = (assembledByte << 1) | (blockData[pattern[j]] & 0x1);
                bitsCollected++;
                remainingBits--;

                if (bitsCollected == 8) {
                    result[byteIndex++] = (byte) assembledByte;
                    assembledByte = 0;
                    bitsCollected = 0;
                }
            }
        }

        return result;
    }
}
