# Algorithm Specification

This document describes the DCT-based steganography algorithm implemented in this project, following the methodology from the [research paper](https://www.researchgate.net/publication/338912933_A_Secure_and_Effective_Pattern-Based_Steganographic_Method_in_Coloured_JPEG_Images).

---

## Overview

The algorithm embeds secret messages into JPEG images by modifying the least significant bits (LSBs) of quantised DCT coefficients in the **Cb chrominance channel**. The choice of Cb minimises visual impact compared to the luminance (Y) channel.

---

## JPEG Structure

JPEG compresses images in 8×8 pixel blocks:

1. **Colour conversion**: RGB → YCbCr
2. **Forward DCT**: Each 8×8 block is transformed to the frequency domain
3. **Quantisation**: Coefficients are divided by quantisation values (lossy step)
4. **Zigzag scan**: 64 coefficients are ordered from DC (low frequency) to AC (high frequency)
5. **Entropy coding**: Huffman encoding produces the final bitstream

We embed data in the **quantised DCT coefficients** before entropy coding, so the output remains a valid JPEG.

---

## Embedding Domain

| Parameter | Value |
|-----------|-------|
| **Channel** | Cb (chrominance, index 1) |
| **Coefficient positions** | Zigzag indices 25–40 (mid-frequency AC) |
| **Bits per block (MCU)** | 16 |
| **Bytes per block** | 2 |

Mid-frequency coefficients are used because they balance capacity with robustness to compression and visual fidelity.

---

## Embedding Patterns

Four zigzag-index patterns are cycled per MCU to distribute modifications and reduce detectability:

| Pattern | Zigzag indices (0-based) |
|---------|--------------------------|
| 0 | 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40 |
| 1 | 40, 39, 38, 37, 36, 35, 34, 33, 32, 31, 30, 29, 28, 27, 26, 25 |
| 2 | 28, 27, 26, 25, 29, 30, 31, 32, 33, 34, 35, 36, 40, 39, 38, 37 |
| 3 | 25, 26, 27, 28, 36, 35, 34, 33, 32, 31, 30, 29, 37, 38, 39, 40 |

For MCU index `i`, pattern `i mod 4` is used.

---

## LSB Embedding

For each selected coefficient `C` at zigzag position `p`:

- **Embed**: `C' = (C & ~1) | bit` — replace LSB with message bit
- **Extract**: `bit = C & 1` — read LSB

The modification is ±1 at most, preserving visual quality.

---

## Cryptographic Layers

### Message Obfuscation (Caesar Cipher)

Before embedding, the message is obfuscated with a position-dependent Caesar cipher:

- Each byte is shifted by `(key + index) mod 256`
- Key is configurable via `STEGO_CAESAR_KEY` (0–255)
- Ensures fixed byte length for the steganography pipeline

### Password Protection (AES-128)

- Password is AES-128-CBC encrypted
- Stored in a JPEG COM (comment) marker with type ID 2
- Message length stored in COM marker with type ID 1
- Decoding requires the correct password for verification

---

## Metadata Storage

JPEG APP/COM segments carry metadata:

| Type ID | Content |
|---------|---------|
| 0 | Attribution string |
| 1 | Message length (4 bytes, big-endian) |
| 2 | AES-encrypted password |

---

## Capacity Formula

For an image of width `W` and height `H` pixels:

```text
MCU_count = (W ÷ 8) × (H ÷ 8)
Capacity_bytes = MCU_count × 2
```

**Examples:**

| Dimensions | MCUs | Capacity |
|------------|------|----------|
| 256 × 256 | 1,024 | 2,048 bytes |
| 640 × 480 | 4,800 | 9,600 bytes |
| 1920 × 1080 | 32,400 | 64,800 bytes |

---

## Encoding Algorithm (Pseudocode)

```text
1. Load cover image, crop to 8×8 block boundary
2. Convert RGB → YCbCr
3. For each 8×8 block:
   a. Apply forward DCT
   b. Quantise
   c. Zigzag scan
4. Obfuscate message with Caesar cipher
5. For each message bit, select coefficient by pattern, set LSB
6. Encrypt password with AES, write to COM marker
7. Write message length to COM marker
8. Huffman encode, output JPEG
```

---

## Decoding Algorithm (Pseudocode)

```text
1. Parse JPEG headers, read COM markers (message length, password)
2. Verify password (AES decrypt, compare)
3. Huffman decode to quantised DCT coefficients
4. For each MCU, read 16 LSBs from Cb coefficients per pattern
5. Reconstruct message bytes
6. Caesar decipher
7. Return plaintext
```
