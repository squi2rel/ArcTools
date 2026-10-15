package com.github.squi2rel.arctools.fabric;

import com.github.squi2rel.arctools.ArcToolsClient;
import net.fabricmc.api.ClientModInitializer;

public final class ArcToolsFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ArcToolsClient.init();
    }
}
