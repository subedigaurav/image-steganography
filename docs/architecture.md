# Architecture

This document describes the layered architecture of the Image Steganography application, including system components, data flows, and package structure.

---

## Overview

The application follows a layered design with clear separation between presentation, application logic, and core processing. All steganography operations run server-side; the web interface provides a thin client for upload, configuration, and download.

| Layer | Responsibility |
|-------|----------------|
| **Presentation** | Thymeleaf templates, Bootstrap UI, static assets |
| **Application** | Request handling, orchestration, validation |
| **Processing** | JPEG encode/decode, DCT, LSB embedding, Huffman |
| **Core** | Configuration, crypto primitives, shared utilities |

---

## System Components

```mermaid
flowchart TB
    subgraph Presentation["Presentation Layer"]
        UI[Thymeleaf + Bootstrap]
    end

    subgraph Application["Application Layer"]
        Controller[SteganographyController]
        Service[SteganographyService]
        Controller --> Service
    end

    subgraph Processing["Processing Layer"]
        Encoder[JpegEncoder<br/>RGB→YCbCr • DCT • Quantise • Zigzag • LSB • Huffman]
        Decoder[JpegDecoder<br/>Parse • Huffman decode • LSB extract]
        Crypto[AESCipher + CaesarCipher]
    end

    subgraph Core["Core"]
        Config[SteganographyConfig<br/>Embedding patterns • Zigzag order]
    end

    Presentation --> Application
    Application --> Encoder
    Application --> Decoder
    Application --> Crypto
    Encoder --> Core
    Decoder --> Core
    Crypto --> Core
```

---

## Encoding Pipeline

```mermaid
flowchart LR
    subgraph Input["Input"]
        Cover[Cover Image]
        Msg[Message]
        Pwd[Password]
    end

    subgraph Pipeline["Encoding Pipeline"]
        A[RGB → YCbCr]
        B[8×8 DCT]
        C[Quantise]
        D[Zigzag]
        E[LSB Embed Cb]
        F[Huffman]
    end

    subgraph Output["Output"]
        Stego[Stego JPEG]
    end

    Cover --> A
    A --> B --> C --> D --> E --> F --> Stego
    Msg --> Cipher[Caesar Encrypt] --> E
    Pwd --> AES[AES Encrypt] --> Meta[COM Marker]
```

---

## Decoding Pipeline

```mermaid
flowchart LR
    subgraph Input["Input"]
        Stego[Stego JPEG]
        Pwd[Password]
    end

    subgraph Pipeline["Decoding Pipeline"]
        A[Parse Headers]
        B[Huffman Decode]
        C[DCT Coeffs]
        D[LSB Extract Cb]
        E[Caesar Decrypt]
    end

    subgraph Output["Output"]
        Msg[Message]
    end

    Stego --> A --> B --> C --> D --> E --> Msg
    A --> Check[Verify Password]
    Pwd --> Check
```

---

## Package Structure

```mermaid
flowchart TD
    Controller[controller] --> Service[service]
    Service --> Encoder[jpeg.encoder]
    Service --> Decoder[jpeg.decoder]
    Service --> Crypto[crypto]
    Service --> Config[config]
    Service --> Exception[exception]

    Encoder --> Common[jpeg.common]
    Encoder --> Config
    Decoder --> Common
    Decoder --> Config
    Decoder --> Crypto

    subgraph Core["Core"]
        Config
        Exception
        Crypto
    end

    subgraph JPEG["JPEG Processing"]
        Encoder
        Decoder
        Common
    end
```

| Package | Purpose |
|---------|---------|
| `controller` | HTTP endpoints, form binding, response handling |
| `service` | Business logic, orchestration of encode/decode |
| `jpeg.encoder` | Forward DCT, quantisation, Huffman, LSB embedding |
| `jpeg.decoder` | Huffman decode, LSB extraction, coefficient parsing |
| `jpeg.common` | Markers, zigzag order, colour channels |
| `crypto` | AES-128, Caesar cipher |
| `config` | Embedding patterns, env-based configuration |
| `exception` | Domain exceptions, error handling |

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Cb channel** | Chrominance changes are less perceptible than luminance |
| **Zigzag 25–40** | Mid-frequency AC coefficients balance capacity and robustness |
| **Four patterns** | Cycled per MCU to distribute modifications and reduce detectability |
| **COM markers** | JPEG metadata stores length and encrypted password without altering image data |
| **Server-side processing** | No client-side crypto; keys stay on server; supports large images |
