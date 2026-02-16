package com.steganography;

import com.steganography.service.SteganographyService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class RoundtripTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        SteganographyService service = new SteganographyService();

        testRoundtrip(service, 64, 64, "hello world", "test1234", 80, "Basic message");
        testRoundtrip(service, 64, 64, "Hello World 123! @#$%", "pass", 80, "Mixed characters");
        testRoundtrip(service, 128, 128,
            "This is a longer message that tests the capacity of the steganography system.",
            "longpass", 50, "Longer message");
        testRoundtrip(service, 64, 64, "A", "x", 90, "Single character");
        testRoundtrip(service, 64, 64, "quality test", "qtest", 10, "Low quality");
        testRoundtrip(service, 64, 64, "quality test", "qtest", 100, "Max quality");
        testRoundtrip(service, 128, 64, "non-square", "nsq", 80, "Non-square image");
        testRoundtrip(service, 256, 256,
            "big image test with more data to encode", "bigimg", 75, "Large image");

        // Wrong password test
        try {
            byte[] stego = encodeImage(service, 64, 64, "secret", "right", 80);
            service.decode(new ByteArrayInputStream(stego), "wrong");
            System.out.println("FAIL: Wrong password - should have thrown");
            failed++;
        } catch (Exception e) {
            System.out.println("PASS: Wrong password correctly rejected");
            passed++;
        }

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    static byte[] encodeImage(SteganographyService service, int w, int h,
                              String msg, String pwd, int quality) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int r = (x * 4 + y) % 256;
                int g = (y * 3 + x * 2) % 256;
                int b = ((x + y) * 5) % 256;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        ByteArrayOutputStream bmpOut = new ByteArrayOutputStream();
        ImageIO.write(img, "bmp", bmpOut);
        return service.encode(new ByteArrayInputStream(bmpOut.toByteArray()), msg, pwd, quality);
    }

    static void testRoundtrip(SteganographyService service, int w, int h,
                               String msg, String pwd, int quality, String testName) {
        try {
            byte[] stego = encodeImage(service, w, h, msg, pwd, quality);
            String decoded = service.decode(new ByteArrayInputStream(stego), pwd);
            if (msg.equals(decoded)) {
                System.out.println("PASS: " + testName + " - '" + msg + "'");
                passed++;
            } else {
                System.out.println("FAIL: " + testName);
                System.out.println("  Expected: '" + msg + "'");
                System.out.println("  Got:      '" + decoded + "'");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + testName + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            failed++;
        }
    }
}
