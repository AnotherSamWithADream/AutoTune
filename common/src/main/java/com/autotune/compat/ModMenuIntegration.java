package com.autotune.compat;

import com.autotune.ui.AutoTuneMainScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu entrypoint that registers AutoTune's main configuration screen
 * as the config screen for the mod in the Mod Menu interface.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AutoTuneMainScreen::new;
    }
}
