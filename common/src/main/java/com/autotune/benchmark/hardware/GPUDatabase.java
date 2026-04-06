package com.autotune.benchmark.hardware;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Loads GPU data from assets/autotune/gpu_database.json and matches against GL_RENDERER strings.
 */
public final class GPUDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GPUDatabase.class);
    private static final String RESOURCE_PATH = "/assets/autotune/gpu_database.json";

    private final List<GPUEntryInternal> entries;

    /**
     * A GPU entry from the database.
     */
    public record GPUEntry(
        String pattern,
        int vramMb,
        float tflopsFp32,
        String arch,
        int generation,
        int tierHint
    ) {}

    private record GPUEntryInternal(
        Pattern compiledPattern,
        GPUEntry entry
    ) {}

    private GPUDatabase(List<GPUEntryInternal> entries) {
        this.entries = entries;
    }

    /**
     * Loads the GPU database from the mod's resource path.
     * Returns an empty database if loading fails.
     */
    public static GPUDatabase load() {
        try (InputStream is = GPUDatabase.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                LOGGER.warn("GPU database resource not found at {}", RESOURCE_PATH);
                return new GPUDatabase(Collections.emptyList());
            }

            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                // [CODE-REVIEW-FIX] Handle both formats: plain array [...] or wrapped {"gpus": [...]}
                JsonElement parsed = gson.fromJson(reader, JsonElement.class);
                JsonArray gpuArray;
                if (parsed.isJsonArray()) {
                    gpuArray = parsed.getAsJsonArray();
                } else if (parsed.isJsonObject()) {
                    gpuArray = parsed.getAsJsonObject().getAsJsonArray("gpus");
                } else {
                    LOGGER.warn("GPU database JSON has unexpected format");
                    return new GPUDatabase(Collections.emptyList());
                }

                if (gpuArray == null) {
                    LOGGER.warn("GPU database JSON missing 'gpus' array");
                    return new GPUDatabase(Collections.emptyList());
                }

                List<GPUEntryInternal> entries = new ArrayList<>();
                for (JsonElement element : gpuArray) {
                    try {
                        JsonObject obj = element.getAsJsonObject();
                        String patternStr = obj.get("pattern").getAsString();
                        // Support both snake_case (vram_mb) and camelCase (vramMb)
                        int vramMb = obj.has("vram_mb") ? obj.get("vram_mb").getAsInt()
                                : obj.has("vramMb") ? obj.get("vramMb").getAsInt() : 0;
                        float tflops = obj.has("tflops_fp32") ? obj.get("tflops_fp32").getAsFloat()
                                : obj.has("tflopsFp32") ? obj.get("tflopsFp32").getAsFloat() : 0.0f;
                        String arch = obj.has("arch") ? obj.get("arch").getAsString() : "Unknown";
                        int generation = obj.has("generation") ? obj.get("generation").getAsInt() : 0;
                        int tierHint = obj.has("tier_hint") ? obj.get("tier_hint").getAsInt()
                                : obj.has("tierHint") ? obj.get("tierHint").getAsInt() : 0;

                        GPUEntry gpuEntry = new GPUEntry(patternStr, vramMb, tflops, arch, generation, tierHint);
                        Pattern compiled = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                        entries.add(new GPUEntryInternal(compiled, gpuEntry));
                    } catch (Exception e) {
                        LOGGER.warn("Skipping invalid GPU database entry: {}", element, e);
                    }
                }

                LOGGER.info("Loaded {} GPU entries from database", entries.size());
                return new GPUDatabase(Collections.unmodifiableList(entries));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load GPU database", e);
            return new GPUDatabase(Collections.emptyList());
        }
    }

    /**
     * Matches a GL_RENDERER string against the database entries using regex patterns.
     * Returns the first matching entry, or empty if none match.
     */
    public Optional<GPUEntry> match(String glRenderer) {
        if (glRenderer == null || glRenderer.isEmpty()) {
            return Optional.empty();
        }

        for (GPUEntryInternal internal : entries) {
            try {
                if (internal.compiledPattern().matcher(glRenderer).find()) {
                    return Optional.of(internal.entry());
                }
            } catch (Exception e) {
                LOGGER.debug("Error matching GPU pattern '{}' against '{}'",
                    internal.entry().pattern(), glRenderer, e);
            }
        }

        return Optional.empty();
    }

}
