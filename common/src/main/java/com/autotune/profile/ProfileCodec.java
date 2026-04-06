package com.autotune.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Encodes and decodes PerformanceProfiles for compact sharing between players.
 * The encoding pipeline is: JSON (Gson) -> GZIP compress -> Base64 encode.
 * Decoding reverses the process: Base64 decode -> GZIP decompress -> JSON parse.
 */
public class ProfileCodec {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileCodec.class);
    private static final Gson GSON = new GsonBuilder().create();
    // [CODE-REVIEW-FIX] Max decompressed size to prevent GZIP bomb attacks
    private static final int MAX_DECOMPRESSED_SIZE = 1024 * 1024; // 1 MB

    /**
     * Encodes a PerformanceProfile into a compact, shareable string.
     * Steps: serialize to JSON -> GZIP compress -> Base64 encode.
     *
     * @param profile the profile to encode
     * @return the encoded string, or null if encoding fails
     */
    public static String encode(PerformanceProfile profile) {
        if (profile == null) {
            LOGGER.warn("Cannot encode null profile");
            return null;
        }

        try {
            String json = GSON.toJson(profile);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(jsonBytes);
            }

            byte[] compressed = baos.toByteArray();
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);

            LOGGER.debug("Encoded profile '{}': {} chars JSON -> {} bytes compressed -> {} chars Base64",
                    profile.getName(), json.length(), compressed.length, encoded.length());
            return encoded;
        } catch (IOException e) {
            LOGGER.error("Failed to encode profile '{}'", profile.getName(), e);
            return null;
        }
    }

    /**
     * Decodes a previously encoded string back into a PerformanceProfile.
     * Steps: Base64 decode -> GZIP decompress -> deserialize from JSON.
     *
     * @param encoded the Base64-encoded, gzipped JSON string
     * @return the decoded profile, or null if decoding fails
     */
    public static PerformanceProfile decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            LOGGER.warn("Cannot decode null or empty string");
            return null;
        }

        try {
            byte[] compressed = Base64.getUrlDecoder().decode(encoded);

            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // [CODE-REVIEW-FIX] Enforce max decompressed size to prevent GZIP bomb attacks
            try (GZIPInputStream gzis = new GZIPInputStream(bais)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                int totalRead = 0;
                while ((bytesRead = gzis.read(buffer)) != -1) {
                    totalRead += bytesRead;
                    if (totalRead > MAX_DECOMPRESSED_SIZE) {
                        throw new IOException("Decompressed data exceeds maximum allowed size of " + MAX_DECOMPRESSED_SIZE + " bytes (possible GZIP bomb)");
                    }
                    baos.write(buffer, 0, bytesRead);
                }
            }

            String json = baos.toString(StandardCharsets.UTF_8);
            PerformanceProfile profile = GSON.fromJson(json, PerformanceProfile.class);

            LOGGER.debug("Decoded profile '{}': {} chars Base64 -> {} bytes compressed -> {} chars JSON",
                    profile.getName(), encoded.length(), compressed.length, json.length());
            return profile;
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to decode profile: invalid Base64 encoding", e);
            return null;
        } catch (IOException e) {
            LOGGER.error("Failed to decode profile: decompression error", e);
            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to decode profile", e);
            return null;
        }
    }
}
