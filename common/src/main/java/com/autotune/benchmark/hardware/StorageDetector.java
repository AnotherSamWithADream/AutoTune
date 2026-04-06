package com.autotune.benchmark.hardware;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Path;

public final class StorageDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageDetector.class);

    private StorageDetector() {}

    /**
     * Detects free disk space in the Minecraft run directory, in bytes.
     */
    public static long detectFreeSpace(MinecraftClient client) {
        try {
            File runDir = client.runDirectory;
            if (runDir != null && runDir.exists()) {
                return runDir.getUsableSpace();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to detect free disk space", e);
        }
        return -1;
    }

    /**
     * Heuristic to guess whether the game is running on an SSD or HDD.
     * Performs a small sequential read benchmark of a temporary file.
     * SSDs typically complete this much faster than HDDs.
     *
     * @return "SSD", "HDD", or "Unknown"
     */
    public static String detectStorageType(MinecraftClient client) {
        try {
            File runDir = client.runDirectory;
            if (runDir == null || !runDir.exists()) {
                return "Unknown";
            }

            Path tempPath = runDir.toPath().resolve(".autotune_storage_probe");
            File tempFile = tempPath.toFile();

            // Write a small test file (1 MB)
            int testSizeBytes = 1024 * 1024;
            byte[] data = new byte[testSizeBytes];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) (i & 0xFF);
            }

            try (RandomAccessFile raf = new RandomAccessFile(tempFile, "rw")) {
                raf.write(data);
                raf.getFD().sync();
            }

            // Sequential read benchmark
            long startNanos = System.nanoTime();
            int iterations = 5;
            for (int i = 0; i < iterations; i++) {
                try (RandomAccessFile raf = new RandomAccessFile(tempFile, "r")) {
                    byte[] readBuf = new byte[testSizeBytes];
                    raf.readFully(readBuf);
                }
            }
            long elapsedNanos = System.nanoTime() - startNanos;

            // Clean up
            if (!tempFile.delete()) {
                tempFile.deleteOnExit();
            }

            // Calculate throughput: MB/s
            double totalMb = (double) (testSizeBytes * iterations) / (1024.0 * 1024.0);
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            double throughputMbPerSec = totalMb / elapsedSeconds;

            // [CODE-REVIEW-FIX] SLF4J does not support {:.1f} format specifiers; use String.format
            LOGGER.debug("Storage probe throughput: {} MB/s", String.format("%.1f", throughputMbPerSec));

            // Heuristic: SSDs typically read > 200 MB/s, HDDs around 80-150 MB/s
            // Use a conservative threshold
            if (throughputMbPerSec > 150.0) {
                return "SSD";
            } else if (throughputMbPerSec > 0) {
                return "HDD";
            }
        } catch (Exception e) {
            LOGGER.debug("Storage type detection failed", e);
        }
        return "Unknown";
    }
}
