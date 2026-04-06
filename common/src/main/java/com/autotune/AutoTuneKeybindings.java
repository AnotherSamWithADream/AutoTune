package com.autotune;

import com.autotune.ui.AutoTuneMainScreen;
import com.autotune.ui.screens.BenchmarkIntroScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTuneKeybindings {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoTuneKeybindings.class);

    private static KeyBinding openMainScreen;
    private static KeyBinding startBenchmark;
    private static KeyBinding toggleLiveMode;
    private static KeyBinding toggleFpsOverlay;

    /**
     * Creates a KeyBinding compatible with both old (String category) and new (Category enum) MC versions.
     * Uses reflection to handle constructor differences across versions.
     */
    // Unchecked cast required: reflection-based constructor invocation returns Object
    @SuppressWarnings("unchecked")
    private static KeyBinding createKeyBinding(String translationKey, InputUtil.Type type, int code, String category) {
        // Try the String-based constructors first (older MC versions)
        for (var ctor : KeyBinding.class.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            // Look for (String, InputUtil.Type, int, String) constructor
            if (params.length == 4 && params[0] == String.class && params[3] == String.class) {
                try {
                    return (KeyBinding) ctor.newInstance(translationKey, type, code, category);
                } catch (Exception ignored) {}
            }
        }

        // Try the Category enum-based constructor (newer MC versions)
        for (var ctor : KeyBinding.class.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 4 && params[0] == String.class && params[3].isEnum()) {
                try {
                    Class<? extends Enum<?>> catClass = (Class<? extends Enum<?>>) params[3];
                    // Try to find MISC category for mod keybindings
                    Object catEnum = null;
                    for (Object c : catClass.getEnumConstants()) {
                        String name = ((Enum<?>) c).name();
                        if (name.equals("MISC") || name.equalsIgnoreCase("misc")) {
                            catEnum = c;
                            break;
                        }
                    }
                    if (catEnum == null) {
                        catEnum = catClass.getEnumConstants()[catClass.getEnumConstants().length - 1];
                    }
                    return (KeyBinding) ctor.newInstance(translationKey, type, code, catEnum);
                } catch (Exception ignored) {}
            }
        }

        // Last resort: try 3-arg constructors via reflection
        for (var ctor : KeyBinding.class.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 3 && params[0] == String.class && params[1] == int.class) {
                try {
                    if (params[2] == String.class) {
                        return (KeyBinding) ctor.newInstance(translationKey, code, category);
                    } else if (params[2].isEnum()) {
                        Class<? extends Enum<?>> catClass = (Class<? extends Enum<?>>) params[2];
                        Object catEnum = catClass.getEnumConstants()[catClass.getEnumConstants().length - 1];
                        return (KeyBinding) ctor.newInstance(translationKey, code, catEnum);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Last resort: try any constructor that takes a String as first arg
        for (var ctor : KeyBinding.class.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length >= 2 && params[0] == String.class) {
                try {
                    Object[] args = new Object[params.length];
                    args[0] = translationKey;
                    for (int i = 1; i < params.length; i++) {
                        if (params[i] == int.class) args[i] = code;
                        else if (params[i] == InputUtil.Type.class) args[i] = type;
                        else if (params[i] == String.class) args[i] = category;
                        else if (params[i].isEnum()) {
                            Object[] constants = params[i].getEnumConstants();
                            args[i] = constants[constants.length - 1]; // Last enum constant as fallback
                        } else args[i] = null;
                    }
                    return (KeyBinding) ctor.newInstance(args);
                } catch (Exception ignored) {}
            }
        }

        LOGGER.error("Failed to create keybinding '{}' - no compatible constructor found. Available constructors:", translationKey);
        for (var ctor : KeyBinding.class.getConstructors()) {
            LOGGER.error("  {}", java.util.Arrays.toString(ctor.getParameterTypes()));
        }
        throw new RuntimeException("Cannot create KeyBinding for " + translationKey);
    }

    public static void register() {
        openMainScreen = KeyBindingHelper.registerKeyBinding(createKeyBinding(
                "key.autotune.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                "category.autotune"
        ));

        startBenchmark = KeyBindingHelper.registerKeyBinding(createKeyBinding(
                "key.autotune.benchmark",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9,
                "category.autotune"
        ));

        toggleLiveMode = KeyBindingHelper.registerKeyBinding(createKeyBinding(
                "key.autotune.live_toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "category.autotune"
        ));

        toggleFpsOverlay = KeyBindingHelper.registerKeyBinding(createKeyBinding(
                "key.autotune.fps_overlay",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                "category.autotune"
        ));
    }

    public static void handleTick(MinecraftClient client) {
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;

        while (openMainScreen.wasPressed()) {
            client.setScreen(new AutoTuneMainScreen(null));
        }

        while (startBenchmark.wasPressed()) {
            if (client.world != null && mod.getBenchmarkRunner() != null
                    && !mod.getBenchmarkRunner().isRunning()) {
                client.setScreen(new BenchmarkIntroScreen(null));
            }
        }

        while (toggleLiveMode.wasPressed()) {
            var engine = mod.getLiveEngine();
            if (engine != null) engine.setEnabled(!engine.isEnabled());
        }

        while (toggleFpsOverlay.wasPressed()) {
            var config = mod.getConfig();
            if (config != null) config.setShowFpsOverlay(!config.isShowFpsOverlay());
        }
    }
}
