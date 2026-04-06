package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase 20: Iris Shader Phase.
 * If Iris is installed, discovers all available shader packs and tests each one
 * at multiple shadow resolution levels (512, 1024, 2048, 4096).
 * Records 600 frames per combination.
 * If Iris is not loaded, the phase is skipped.
 */
public class IrisShaderPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrisShaderPhase.class);

    private static final int[] SHADOW_RESOLUTIONS = {512, 1024, 2048, 4096};

    private List<String> cachedSubTests;
    private List<String> availableShaderPacks;
    private String originalShaderPack;
    private int originalShadowResolution;
    // [CODE-REVIEW-FIX] H-021: Boolean guard to save originals unconditionally on first call
    private boolean savedOriginals;

    @Override
    public String getPhaseId() {
        return "phase_20_iris_shaders";
    }

    @Override
    public String getPhaseName() {
        return "Iris Shader Packs";
    }

    @Override
    public int getPhaseNumber() {
        return 20;
    }

    @Override
    public List<String> getSubTestLabels() {
        if (cachedSubTests != null) {
            return cachedSubTests;
        }
        cachedSubTests = new ArrayList<>();
        availableShaderPacks = discoverShaderPacks();

        if (availableShaderPacks.isEmpty()) {
            // At minimum test the internal shader (no shaders / vanilla)
            cachedSubTests.add("none_shadow_1024");
            return cachedSubTests;
        }

        for (String pack : availableShaderPacks) {
            for (int shadowRes : SHADOW_RESOLUTIONS) {
                String sanitized = pack.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                cachedSubTests.add(sanitized + "_shadow_" + shadowRes);
            }
        }
        return cachedSubTests;
    }

    @Override
    public int getFramesPerSubTest() {
        return BenchmarkConstants.FRAMES_MEDIUM;
    }

    @Override
    public String shouldSkip() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return "Iris is not installed";
        }
        return null;
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        // [CODE-REVIEW-FIX] H-021: Save originalShaderPack unconditionally using a boolean guard.
        // The old null check (originalShaderPack == null) would miss saving when the pack was
        // already "(internal)" or null. Use a dedicated boolean to ensure we capture once.
        if (!savedOriginals) {
            originalShaderPack = getCurrentShaderPack();
            originalShadowResolution = getCurrentShadowResolution();
            savedOriginals = true;
            LOGGER.info("Saving original shader pack: {}, shadow res: {}", originalShaderPack, originalShadowResolution);
        }

        // Parse the sub-test label to extract shader pack name and shadow resolution
        int shadowIdx = subTestLabel.lastIndexOf("_shadow_");
        if (shadowIdx < 0) {
            LOGGER.warn("Could not parse sub-test label: {}", subTestLabel);
            return;
        }

        String packSanitized = subTestLabel.substring(0, shadowIdx);
        int shadowRes;
        try {
            shadowRes = Integer.parseInt(subTestLabel.substring(shadowIdx + "_shadow_".length()));
        } catch (NumberFormatException e) {
            LOGGER.warn("Could not parse shadow resolution from: {}", subTestLabel);
            return;
        }

        // Map sanitized name back to actual pack name
        String actualPack = resolvePackName(packSanitized);

        LOGGER.info("Testing shader pack '{}' at shadow resolution {}", actualPack, shadowRes);

        setShaderPack(actualPack);
        setShadowResolution(shadowRes);
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring shader pack to '{}', shadow resolution to {}", originalShaderPack, originalShadowResolution);
        if (originalShaderPack != null) {
            setShaderPack(originalShaderPack);
        }
        setShadowResolution(originalShadowResolution);
        originalShaderPack = null;
        savedOriginals = false; // [CODE-REVIEW-FIX] H-021: Reset guard on teardown
        cachedSubTests = null;
        availableShaderPacks = null;
    }

    /**
     * Discovers installed shader packs from the shaderpacks directory.
     */
    private List<String> discoverShaderPacks() {
        List<String> packs = new ArrayList<>();
        try {
            Path shaderpacksDir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
            if (Files.isDirectory(shaderpacksDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacksDir)) {
                    for (Path entry : stream) {
                        String name = entry.getFileName().toString();
                        if (name.endsWith(".zip") || Files.isDirectory(entry)) {
                            packs.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not discover shader packs: {}", e.toString());
        }

        // Also try querying Iris for its known packs
        if (packs.isEmpty()) {
            packs.addAll(queryIrisShaderPacks());
        }

        LOGGER.info("Discovered {} shader packs: {}", packs.size(), packs);
        return packs;
    }

    /**
     * [CODE-REVIEW-FIX] M-008: Removed dead reflection scanning code. The previous implementation
     * scanned Iris API methods containing "shaderpack" but never invoked any of them, so it
     * always returned an empty list. Shader packs are discovered from the filesystem only
     * (via discoverShaderPacks). This method is retained as a no-op fallback stub.
     */
    private List<String> queryIrisShaderPacks() {
        // Shader packs are discovered from the filesystem (shaderpacks/ directory) only.
        // The Iris API does not expose a reliable method to enumerate all installed packs.
        return new ArrayList<>();
    }

    /**
     * Resolves a sanitized pack name back to the actual file name.
     */
    private String resolvePackName(String sanitized) {
        if ("none".equals(sanitized)) {
            return "(internal)";
        }
        if (availableShaderPacks != null) {
            for (String pack : availableShaderPacks) {
                String s = pack.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                if (s.equals(sanitized)) {
                    return pack;
                }
            }
        }
        return sanitized;
    }

    private String getCurrentShaderPack() {
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method instanceMethod = irisApiClass.getMethod("getInstance");
            Object irisApi = instanceMethod.invoke(null);
            Method getConfig = irisApi.getClass().getMethod("getConfiguredShaderpackName");
            Object result = getConfig.invoke(irisApi);
            if (result instanceof Optional<?> opt) {
                return opt.map(Object::toString).orElse("(internal)");
            }
            return result != null ? result.toString() : "(internal)";
        } catch (Exception e) {
            LOGGER.debug("Could not get current shader pack: {}", e.toString());
            return "(internal)";
        }
    }

    private void setShaderPack(String packName) {
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method instanceMethod = irisApiClass.getMethod("getInstance");
            Object irisApi = instanceMethod.invoke(null);
            Method setConfig = irisApi.getClass().getMethod("setConfiguredShaderpackName", String.class);
            setConfig.invoke(irisApi, packName);
        } catch (Exception e) {
            LOGGER.warn("Could not set shader pack to '{}': {}", packName, e.toString());
        }
    }

    private int getCurrentShadowResolution() {
        try {
            Class<?> irisConfigClass = Class.forName("net.irisshaders.iris.config.IrisConfig");
            Method getInstanceMethod = irisConfigClass.getMethod("getInstance");
            Object config = getInstanceMethod.invoke(null);
            Method getShadowRes = config.getClass().getMethod("getShadowMapResolution");
            return (int) getShadowRes.invoke(config);
        } catch (Exception e) {
            LOGGER.debug("Could not get current shadow resolution: {}", e.toString());
            return 1024;
        }
    }

    private void setShadowResolution(int resolution) {
        try {
            Class<?> irisConfigClass = Class.forName("net.irisshaders.iris.config.IrisConfig");
            Method getInstanceMethod = irisConfigClass.getMethod("getInstance");
            Object config = getInstanceMethod.invoke(null);
            Method setShadowRes = config.getClass().getMethod("setShadowMapResolution", int.class);
            setShadowRes.invoke(config, resolution);
        } catch (Exception e) {
            LOGGER.debug("Could not set shadow resolution to {}: {}", resolution, e.toString());
        }
    }
}
