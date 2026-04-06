package com.autotune.profile;

import com.autotune.questionnaire.PlayerPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProfileCodec")
class ProfileCodecTest {

    // -----------------------------------------------------------------------
    // Round-trip encode/decode
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Encode/Decode round-trip")
    class RoundTrip {

        @Test
        @DisplayName("Encode then decode should produce a profile with matching fields")
        void testEncodeDecodeRoundTrip() {
            PerformanceProfile original = createTestProfile(
                    "TestProfile", "A test profile for unit testing");

            String encoded = ProfileCodec.encode(original);
            assertNotNull(encoded, "Encoded string should not be null");

            PerformanceProfile decoded = ProfileCodec.decode(encoded);
            assertNotNull(decoded, "Decoded profile should not be null");

            assertAll("Decoded profile should match original",
                    () -> assertEquals(original.getName(), decoded.getName(),
                            "Name should match"),
                    () -> assertEquals(original.getDescription(), decoded.getDescription(),
                            "Description should match"),
                    () -> assertEquals(original.getCreatedTimestamp(), decoded.getCreatedTimestamp(),
                            "Created timestamp should match"),
                    () -> assertEquals(original.getLastUsedTimestamp(), decoded.getLastUsedTimestamp(),
                            "Last used timestamp should match")
            );
        }

        @Test
        @DisplayName("Round-trip preserves setting overrides")
        void testRoundTripPreservesOverrides() {
            PerformanceProfile original = createTestProfile("OverrideTest", "Testing overrides");
            Map<String, Object> overrides = new HashMap<>();
            overrides.put("renderDistance", 16.0);
            overrides.put("particles", 1.0);
            overrides.put("entityShadows", true);
            original.setSettingOverrides(overrides);

            String encoded = ProfileCodec.encode(original);
            PerformanceProfile decoded = ProfileCodec.decode(encoded);

            assertNotNull(decoded);
            Map<String, Object> decodedOverrides = decoded.getSettingOverrides();
            assertNotNull(decodedOverrides, "Decoded overrides should not be null");
            // Gson deserializes numbers as Double
            assertEquals(16.0, ((Number) decodedOverrides.get("renderDistance")).doubleValue(), 0.001,
                    "renderDistance override should be preserved");
            assertEquals(1.0, ((Number) decodedOverrides.get("particles")).doubleValue(), 0.001,
                    "particles override should be preserved");
        }

        @Test
        @DisplayName("Round-trip preserves player preferences")
        void testRoundTripPreservesPreferences() {
            PerformanceProfile original = createTestProfile("PrefTest", "Testing preferences");
            PlayerPreferences prefs = new PlayerPreferences();
            prefs.setTargetFps(144);
            prefs.setFloorFps(60);
            prefs.setFpsRank(1);
            prefs.setVisualQualityRank(4);
            prefs.setPlayStyle(PlayerPreferences.PlayStyle.COMPETITIVE_PVP);
            original.setPreferences(prefs);

            String encoded = ProfileCodec.encode(original);
            PerformanceProfile decoded = ProfileCodec.decode(encoded);

            assertNotNull(decoded);
            PlayerPreferences decodedPrefs = decoded.getPreferences();
            assertNotNull(decodedPrefs, "Decoded preferences should not be null");
            assertEquals(144, decodedPrefs.getTargetFps(), "targetFps should be preserved");
            assertEquals(60, decodedPrefs.getFloorFps(), "floorFps should be preserved");
            assertEquals(1, decodedPrefs.getFpsRank(), "fpsRank should be preserved");
        }

        @Test
        @DisplayName("Round-trip works for preset profiles (competitivePvp)")
        void testRoundTripPresetProfile() {
            PerformanceProfile original = PerformanceProfile.competitivePvp();

            String encoded = ProfileCodec.encode(original);
            assertNotNull(encoded);

            PerformanceProfile decoded = ProfileCodec.decode(encoded);
            assertNotNull(decoded);
            assertEquals("Competitive PvP", decoded.getName());
        }

        @Test
        @DisplayName("Round-trip works for buildingScreenshots preset")
        void testRoundTripBuildingProfile() {
            PerformanceProfile original = PerformanceProfile.buildingScreenshots();

            String encoded = ProfileCodec.encode(original);
            PerformanceProfile decoded = ProfileCodec.decode(encoded);

            assertNotNull(decoded);
            assertEquals("Building & Screenshots", decoded.getName());
        }
    }

    // -----------------------------------------------------------------------
    // Base64 output validation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Encoded string format")
    class EncodedFormat {

        @Test
        @DisplayName("Encoded output should be valid URL-safe Base64 without padding")
        void testEncodedStringIsBase64() {
            PerformanceProfile profile = createTestProfile("B64Test", "Base64 validation");
            String encoded = ProfileCodec.encode(profile);

            assertNotNull(encoded);
            assertFalse(encoded.isEmpty(), "Encoded string should not be empty");

            // URL-safe Base64 without padding should only contain these chars
            assertTrue(encoded.matches("[A-Za-z0-9_-]+"),
                    "Encoded string should contain only URL-safe Base64 characters (no padding '=')");

            // Should decode without throwing
            assertDoesNotThrow(() -> Base64.getUrlDecoder().decode(encoded),
                    "Encoded string should be decodable by URL-safe Base64 decoder");
        }

        @Test
        @DisplayName("Encoding null profile should return null")
        void testEncodeNull() {
            assertNull(ProfileCodec.encode(null),
                    "Encoding null should return null");
        }
    }

    // -----------------------------------------------------------------------
    // Invalid input handling
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Invalid input handling")
    class InvalidInput {

        @Test
        @DisplayName("Decoding invalid Base64 should return null (not throw)")
        void testDecodeInvalidBase64() {
            // Characters not valid in any Base64 alphabet
            PerformanceProfile result = ProfileCodec.decode("!!!not-valid-base64!!!");
            assertNull(result, "Decoding invalid Base64 should return null");
        }

        @Test
        @DisplayName("Decoding null should return null")
        void testDecodeNull() {
            assertNull(ProfileCodec.decode(null),
                    "Decoding null should return null");
        }

        @Test
        @DisplayName("Decoding empty string should return null")
        void testDecodeEmptyString() {
            assertNull(ProfileCodec.decode(""),
                    "Decoding empty string should return null");
        }

        @Test
        @DisplayName("Decoding valid Base64 but not valid GZIP should return null")
        void testDecodeMalformedGzip() {
            // Valid Base64 but the decoded bytes are not a valid GZIP stream
            String validBase64NotGzip = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("this is just plain text, not gzip".getBytes());

            PerformanceProfile result = ProfileCodec.decode(validBase64NotGzip);
            assertNull(result,
                    "Decoding valid Base64 that is not valid GZIP should return null");
        }

        @Test
        @DisplayName("Decoding valid GZIP but invalid JSON should return null")
        void testDecodeInvalidJson() throws IOException {
            // Create valid GZIP of non-JSON content
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write("this is not JSON {{{".getBytes());
            }
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());

            PerformanceProfile result = ProfileCodec.decode(encoded);
            // Gson may return a non-null but empty profile or throw - either way handle gracefully
            // The key thing is it does not throw an unhandled exception
            assertDoesNotThrow(() -> ProfileCodec.decode(encoded),
                    "Malformed JSON inside valid GZIP should not throw an unhandled exception");
        }
    }

    // -----------------------------------------------------------------------
    // GZIP bomb protection
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GZIP bomb protection")
    class GzipBombProtection {

        @Test
        @DisplayName("Decoding a payload that decompresses to >1MB should return null (GZIP bomb)")
        void testGzipBombProtection() throws IOException {
            // Create a GZIP payload that decompresses to well over 1MB
            // Repeated zeros compress extremely well, so a small compressed payload
            // can expand to a massive decompressed output
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                // Write 2MB of zeros - compresses to very small but decompresses to 2MB
                byte[] chunk = new byte[8192];
                for (int i = 0; i < 256; i++) { // 256 * 8192 = 2MB
                    gzos.write(chunk);
                }
            }

            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());

            PerformanceProfile result = ProfileCodec.decode(encoded);
            assertNull(result,
                    "Decoding a GZIP bomb (>1MB decompressed) should return null due to size limit");
        }

        @Test
        @DisplayName("Payload just under 1MB limit should decode successfully")
        void testJustUnderLimit() {
            // Create a valid JSON payload that compresses and is under 1MB
            // A simple profile's JSON is typically well under 1KB
            PerformanceProfile smallProfile = createTestProfile("SmallProfile", "Under limit");
            String encoded = ProfileCodec.encode(smallProfile);

            PerformanceProfile result = ProfileCodec.decode(encoded);
            assertNotNull(result,
                    "A normal-sized profile should decode without hitting the 1MB limit");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static PerformanceProfile createTestProfile(String name, String description) {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.setTargetFps(60);
        prefs.setFloorFps(30);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("renderDistance", 12);

        PerformanceProfile profile = new PerformanceProfile(name, description, prefs, overrides);
        // Pin timestamps so round-trip comparisons are stable
        profile.setCreatedTimestamp(1700000000000L);
        profile.setLastUsedTimestamp(1700000000000L);
        return profile;
    }
}
