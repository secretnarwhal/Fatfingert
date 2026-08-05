package dev.fatfingert.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.fatfingert.gui.FatfingertConfigScreen;

/**
 * Lets Mod Menu open the Fatfingert panel from its mod list ("Configure" on the
 * Fatfingert entry). Mod Menu is a compile-only dependency and is never bundled:
 * this entrypoint is only ever constructed by Mod Menu itself, so the class is
 * simply never loaded when Mod Menu is absent.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FatfingertConfigScreen::new;
    }
}
