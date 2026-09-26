package com.sondhan.util;

import javafx.scene.image.Image;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * Image & Cryptographic Forensics Utility (Sondhan Fact Verification)
 * ──────────────────────────────────────────────────────────────────────────────
 * • Computes SHA-256 cryptographic hashes for exact matching.
 * • Computes 64-bit Difference Hash (dHash) for perceptual near-duplicate matching
 *   (detects modified, cropped, recaptioned, or compressed images).
 * • Extracts image dimensions, format, and human-readable file sizes.
 * ──────────────────────────────────────────────────────────────────────────────
 */
public class ImageHashUtil {

    /** Model representing inspected image properties */
    public static class ImageInfo {
        public final String fileName;
        public final String fileSizeFormatted;
        public final long   fileSizeBytes;
        public final int    width;
        public final int    height;
        public final String hash;
        public final String format;

        public ImageInfo(String fileName, long fileSizeBytes, int width, int height, String hash, String format) {
            this.fileName          = fileName;
            this.fileSizeBytes     = fileSizeBytes;
            this.fileSizeFormatted = formatBytes(fileSizeBytes);
            this.width             = width;
            this.height            = height;
            this.hash              = hash;
            this.format            = format;
        }

        private static String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Computes the SHA-256 hash of a file.
     * Stream-based reading with 8KB buffer ensures low memory footprint.
     */
    public static String hashFile(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
        }
        return toHex(md.digest());
    }

    /**
     * Computes a 64-bit Difference Hash (dHash) for perceptual near-duplicate matching.
     * Resizes the image to 9x8 grayscale and computes horizontal gradient transitions.
     * If two images have a Hamming distance <= 12, they are visually the same image,
     * even if altered, cropped, recolored, or compressed.
     */
    public static long computeDHash(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) return 0L;
            return computeDHash(img);
        } catch (Exception e) {
            return 0L;
        }
    }

    public static long computeDHash(BufferedImage img) {
        BufferedImage small = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, 9, 8, null);
        g.dispose();

        long hash = 0L;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = small.getRaster().getSample(x, y, 0);
                int right = small.getRaster().getSample(x + 1, y, 0);
                if (left > right) {
                    hash |= (1L << (y * 8 + x));
                }
            }
        }
        return hash;
    }

    /**
     * Calculates the Hamming distance between two 64-bit hashes.
     * Returns the number of differing bits (0 to 64).
     */
    public static int hammingDistance(long h1, long h2) {
        return Long.bitCount(h1 ^ h2);
    }

    /**
     * Inspects an image file and extracts metadata + SHA-256 hash.
     */
    public static ImageInfo inspectImage(File file) {
        String hash = "Unknown";
        try {
            hash = hashFile(file);
        } catch (Exception ignored) {}

        int w = 0, h = 0;
        try (FileInputStream fis = new FileInputStream(file)) {
            Image img = new Image(fis);
            w = (int) img.getWidth();
            h = (int) img.getHeight();
        } catch (Exception ignored) {}

        String name = file.getName();
        String ext = "UNKNOWN";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            ext = name.substring(dot + 1).toUpperCase();
        }

        return new ImageInfo(name, file.length(), w, h, hash, ext);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}