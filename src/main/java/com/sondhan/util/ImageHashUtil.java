package com.sondhan.util;

import javafx.scene.image.Image;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * ──────────────────────────────────────────────────────────────────────────────
 * Image & Cryptographic Utility (Course Project: Multithreading & Security)
 * ──────────────────────────────────────────────────────────────────────────────
 * Computes SHA-256 cryptographic hashes for uploaded images to detect duplicates
 * and enable O(1) matching against preloaded fact-check databases.
 * Also extracts image dimensions, format, and human-readable file sizes.
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