package com.steganography.controller;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.steganography.dto.StegoAnalysis;
import com.steganography.exception.InvalidImageException;
import com.steganography.exception.InvalidPasswordException;
import com.steganography.exception.MessageTooLongException;
import com.steganography.exception.SteganographyException;
import com.steganography.service.SteganographyService;

/**
 * Web controller for the steganography encode and decode pages.
 */
@Controller
public class SteganographyController {

    private final SteganographyService steganographyService;

    /**
     * Creates a new controller with the given steganography service.
     *
     * @param steganographyService the service used for encoding and decoding
     */
    public SteganographyController(SteganographyService steganographyService) {
        this.steganographyService = steganographyService;
    }

    /**
     * Renders the home page with links to encode and decode.
     *
     * @return the index template name
     */
    @GetMapping("/")
    public String showHomePage() {
        return "index";
    }

    /**
     * Renders the encode form.
     *
     * @return the encode template name
     */
    @GetMapping("/encode")
    public String showEncodeForm() {
        return "encode";
    }

    /**
     * Renders the decode form.
     *
     * @return the decode template name
     */
    @GetMapping("/decode")
    public String showDecodeForm() {
        return "decode";
    }

    /**
     * Renders the visualize page for stego image analysis.
     *
     * @return the visualize template name
     */
    @GetMapping("/visualize")
    public String showVisualizeForm() {
        return "visualize";
    }

    /**
     * Analyzes a stego image and returns metadata for visualization (JSON).
     * No password required.
     *
     * @param stegoImage the uploaded stego image
     * @return analysis result or error
     */
    @PostMapping(value = "/analyze", produces = "application/json")
    public ResponseEntity<?> handleAnalyze(@RequestParam("stegoImage") MultipartFile stegoImage) {
        if (stegoImage.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Please select a stego image."));
        }
        try {
            StegoAnalysis analysis = steganographyService.analyze(stegoImage.getInputStream());
            return ResponseEntity.ok(analysis);
        } catch (InvalidImageException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "Failed to process image: " + ex.getMessage()));
        }
    }

    /**
     * Handles the encode form submission. Returns the stego image as a download
     * on success, or re-renders the form with an error message on failure.
     *
     * @param coverImage the uploaded cover image
     * @param message    the secret message to hide
     * @param password   the password for protection
     * @param quality    JPEG quality (1–100)
     * @param model      the view model for error messages
     * @return a ResponseEntity with the stego image, or the encode template
     */
    @PostMapping("/encode")
    public Object handleEncode(
            @RequestParam("coverImage") MultipartFile coverImage,
            @RequestParam("message") String message,
            @RequestParam("password") String password,
            @RequestParam(value = "quality", defaultValue = "80") int quality,
            Model model) {

        if (coverImage.isEmpty()) {
            return encodeError(model, "Please select a cover image.");
        }
        if (message == null || message.isBlank()) {
            return encodeError(model, "Please enter a message to hide.");
        }
        if (password == null || password.isBlank()) {
            return encodeError(model, "Please enter a password.");
        }
        quality = Math.max(1, Math.min(100, quality));

        try {
            byte[] stegoBytes = steganographyService.encode(
                    coverImage.getInputStream(), message, password, quality);

            String filename = buildStegoFilename(coverImage.getOriginalFilename());

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(new ByteArrayResource(stegoBytes));

        } catch (MessageTooLongException ex) {
            return encodeError(model, ex.getMessage());
        } catch (InvalidImageException ex) {
            return encodeError(model, ex.getMessage());
        } catch (IOException ex) {
            return encodeError(model, "Failed to process the image: " + ex.getMessage());
        } catch (SteganographyException ex) {
            return encodeError(model, ex.getMessage());
        }
    }

    /**
     * Handles the decode form submission. Extracts the hidden message and
     * displays it on success, or shows an error on failure.
     *
     * @param stegoImage the uploaded stego image
     * @param password   the password used during encoding
     * @param model      the view model for the result or error
     * @return the decode template name
     */
    @PostMapping("/decode")
    public String handleDecode(
            @RequestParam("stegoImage") MultipartFile stegoImage,
            @RequestParam("password") String password,
            Model model) {

        if (stegoImage.isEmpty()) {
            return decodeError(model, "Please select a stego image.");
        }
        if (password == null || password.isBlank()) {
            return decodeError(model, "Please enter the password.");
        }

        try {
            String extractedMessage = steganographyService.decode(
                    stegoImage.getInputStream(), password);
            model.addAttribute("message", extractedMessage);
            model.addAttribute("success", true);

        } catch (InvalidPasswordException ex) {
            return decodeError(model, "The password is incorrect.");
        } catch (InvalidImageException ex) {
            return decodeError(model, ex.getMessage());
        } catch (IOException ex) {
            return decodeError(model, "Failed to process the image: " + ex.getMessage());
        } catch (SteganographyException ex) {
            return decodeError(model, ex.getMessage());
        }
        return "decode";
    }

    /**
     * Builds the stego image filename: original base name + "_stego_" + timestamp +
     * ".jpg".
     */
    private String buildStegoFilename(String originalFilename) {
        String base = "image";
        if (originalFilename != null && !originalFilename.isBlank()) {
            String name = originalFilename.replaceAll(".*[/\\\\]", "");
            int dot = name.lastIndexOf('.');
            base = dot > 0 ? name.substring(0, dot) : name;
            base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (base.isBlank()) {
                base = "image";
            }
        }
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        return base + "_stego_" + timestamp + ".jpg";
    }

    /**
     * Adds an error message to the model and returns the encode template.
     */
    private String encodeError(Model model, String message) {
        model.addAttribute("error", message);
        return "encode";
    }

    /**
     * Adds an error message to the model and returns the decode template.
     */
    private String decodeError(Model model, String message) {
        model.addAttribute("error", message);
        return "decode";
    }
}
