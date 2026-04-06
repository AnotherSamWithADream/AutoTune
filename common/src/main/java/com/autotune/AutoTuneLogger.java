package com.autotune;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AutoTune's dedicated verbose logger. Writes to both SLF4J (Minecraft's log) and
 * a dedicated file at config/autotune/autotune-debug.log.
 *
 * Usage: AutoTuneLogger.info("message"), AutoTuneLogger.debug("detail"), etc.
 * All messages go to the file regardless of Minecraft's log level.
 */
public final class AutoTuneLogger {

    private static final Logger SLF4J = LoggerFactory.getLogger("AutoTune");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static BufferedWriter fileWriter;
    private static boolean initialized;
    private static boolean verbose = true;

    private AutoTuneLogger() {}

    /** Initialize the file logger. Call once during mod init. */
    public static void init() {
        if (initialized) return;
        try {
            Path logDir = Path.of("config", "autotune");
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("autotune-debug.log");

            // Rotate: if file > 5MB, rename to .old
            if (Files.exists(logFile) && Files.size(logFile) > 5_000_000) {
                Path oldFile = logDir.resolve("autotune-debug.old.log");
                Files.deleteIfExists(oldFile);
                Files.move(logFile, oldFile);
            }

            fileWriter = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            writeToFile("=== AutoTune Debug Log Started: " + LocalDateTime.now() + " ===");
            writeToFile("Java: " + System.getProperty("java.version") + " | OS: " + System.getProperty("os.name"));
            writeToFile("");

            initialized = true;
        } catch (IOException e) {
            SLF4J.warn("Could not initialize AutoTune file logger: {}", e.toString());
        }
    }

    public static void setVerbose(boolean v) { verbose = v; }

    // --- Log levels ---

    public static void info(String msg) {
        SLF4J.info(msg);
        writeToFile("[INFO] " + msg);
    }

    public static void info(String fmt, Object... args) {
        SLF4J.info(fmt, args);
        writeToFile("[INFO] " + format(fmt, args));
    }

    public static void debug(String msg) {
        if (verbose) SLF4J.info("[debug] " + msg); // SLF4J debug often filtered, use info
        writeToFile("[DEBUG] " + msg);
    }

    public static void debug(String fmt, Object... args) {
        if (verbose) SLF4J.info("[debug] " + format(fmt, args));
        writeToFile("[DEBUG] " + format(fmt, args));
    }

    public static void warn(String msg) {
        SLF4J.warn(msg);
        writeToFile("[WARN] " + msg);
    }

    public static void warn(String fmt, Object... args) {
        SLF4J.warn(fmt, args);
        writeToFile("[WARN] " + format(fmt, args));
    }

    public static void error(String msg) {
        SLF4J.error(msg);
        writeToFile("[ERROR] " + msg);
    }

    public static void error(String msg, Throwable t) {
        SLF4J.error(msg, t);
        writeToFile("[ERROR] " + msg + " | " + t.toString());
    }

    public static void error(String fmt, Object... args) {
        SLF4J.error(fmt, args);
        writeToFile("[ERROR] " + format(fmt, args));
    }

    /** Log a section header for readability */
    public static void section(String title) {
        String line = "--- " + title + " ---";
        if (verbose) SLF4J.info(line);
        writeToFile("");
        writeToFile(line);
    }

    // --- Internal ---

    private static void writeToFile(String msg) {
        if (fileWriter == null) return;
        try {
            String timestamp = LocalDateTime.now().format(TIME_FMT);
            fileWriter.write("[" + timestamp + "] " + msg);
            fileWriter.newLine();
            fileWriter.flush();
        } catch (IOException ignored) {}
    }

    /** Simple SLF4J-style {} placeholder replacement */
    private static String format(String fmt, Object... args) {
        if (args == null || args.length == 0) return fmt;
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        int i = 0;
        while (i < fmt.length()) {
            if (i < fmt.length() - 1 && fmt.charAt(i) == '{' && fmt.charAt(i + 1) == '}' && argIdx < args.length) {
                sb.append(args[argIdx++]);
                i += 2;
            } else {
                sb.append(fmt.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    /** Flush and close the file writer. */
    public static void shutdown() {
        if (fileWriter != null) {
            try {
                writeToFile("");
                writeToFile("=== AutoTune Debug Log Ended: " + LocalDateTime.now() + " ===");
                fileWriter.flush();
                fileWriter.close();
            } catch (IOException ignored) {}
            fileWriter = null;
        }
        initialized = false;
    }
}
