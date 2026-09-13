package dev.fatfingert;

import net.fabricmc.api.ClientModInitializer;

public class FatfingertClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Fatfingert.loadConfig();
        FatfingertKeys.register();
    }
}
