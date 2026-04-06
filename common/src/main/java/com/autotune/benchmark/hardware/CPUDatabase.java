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
 * Loads CPU data from assets/autotune/cpu_database.json and matches against CPU name strings.
 */
public final class CPUDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(CPUDatabase.class);
    private static final String RESOURCE_PATH = "/assets/autotune/cpu_database.json";

    private final List<CPUEntryInternal> entries;

    /**
     * A CPU entry from the database.
     */
    public record CPUEntry(
        String pattern,
        int coresPhysical,
        float baseClockGhz,
        float boostClockGhz,
        int l3CacheMb,
        int tierHint,
        String architecture
    ) {}

    private record CPUEntryInternal(
        Pattern compiledPattern,
        CPUEntry entry
    ) {}

    private CPUDatabase(List<CPUEntryInternal> entries) {
        this.entries = entries;
    }

    /**
     * Loads the CPU database from the mod's resource path.
     * Returns an empty database if loading fails.
     */
    public static CPUDatabase load() {
        try (InputStream is = CPUDatabase.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                LOGGER.warn("CPU database resource not found at {}", RESOURCE_PATH);
                return new CPUDatabase(Collections.emptyList());
            }

            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                // [CODE-REVIEW-FIX] Handle both formats: plain array [...] or wrapped {"cpus": [...]}
                JsonElement parsed = gson.fromJson(reader, JsonElement.class);
                JsonArray cpuArray;
                if (parsed.isJsonArray()) {
                    cpuArray = parsed.getAsJsonArray();
                } else if (parsed.isJsonObject()) {
                    cpuArray = parsed.getAsJsonObject().getAsJsonArray("cpus");
                } else {
                    LOGGER.warn("CPU database JSON has unexpected format");
                    return new CPUDatabase(Collections.emptyList());
                }

                if (cpuArray == null) {
                    LOGGER.warn("CPU database JSON missing 'cpus' array");
                    return new CPUDatabase(Collections.emptyList());
                }

                List<CPUEntryInternal> entries = new ArrayList<>();
                for (JsonElement element : cpuArray) {
                    try {
                        JsonObject obj = element.getAsJsonObject();
                        String patternStr = obj.get("pattern").getAsString();
                        // Support both snake_case and camelCase field names
                        int cores = obj.has("cores_physical") ? obj.get("cores_physical").getAsInt()
                                : obj.has("coresPhysical") ? obj.get("coresPhysical").getAsInt() : 0;
                        float baseClock = obj.has("base_clock_ghz") ? obj.get("base_clock_ghz").getAsFloat()
                                : obj.has("baseClockGhz") ? obj.get("baseClockGhz").getAsFloat() : 0.0f;
                        float boostClock = obj.has("boost_clock_ghz") ? obj.get("boost_clock_ghz").getAsFloat()
                                : obj.has("boostClockGhz") ? obj.get("boostClockGhz").getAsFloat() : 0.0f;
                        int l3Cache = obj.has("l3_cache_mb") ? obj.get("l3_cache_mb").getAsInt()
                                : obj.has("l3CacheMb") ? obj.get("l3CacheMb").getAsInt() : 0;
                        int tierHint = obj.has("tier_hint") ? obj.get("tier_hint").getAsInt()
                                : obj.has("tierHint") ? obj.get("tierHint").getAsInt() : 0;
                        String architecture = obj.has("architecture") ? obj.get("architecture").getAsString() : "Unknown";

                        CPUEntry cpuEntry = new CPUEntry(patternStr, cores, baseClock, boostClock, l3Cache, tierHint, architecture);
                        Pattern compiled = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                        entries.add(new CPUEntryInternal(compiled, cpuEntry));
                    } catch (Exception e) {
                        LOGGER.warn("Skipping invalid CPU database entry: {}", element, e);
                    }
                }

                LOGGER.info("Loaded {} CPU entries from database", entries.size());
                return new CPUDatabase(Collections.unmodifiableList(entries));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load CPU database", e);
            return new CPUDatabase(Collections.emptyList());
        }
    }

    /**
     * Matches a CPU name string against the database entries using regex patterns.
     * Returns the first matching entry, or empty if none match.
     */
    public Optional<CPUEntry> match(String cpuName) {
        if (cpuName == null || cpuName.isEmpty()) {
            return Optional.empty();
        }

        for (CPUEntryInternal internal : entries) {
            try {
                if (internal.compiledPattern().matcher(cpuName).find()) {
                    return Optional.of(internal.entry());
                }
            } catch (Exception e) {
                LOGGER.debug("Error matching CPU pattern '{}' against '{}'",
                    internal.entry().pattern(), cpuName, e);
            }
        }

        return Optional.empty();
    }

}
