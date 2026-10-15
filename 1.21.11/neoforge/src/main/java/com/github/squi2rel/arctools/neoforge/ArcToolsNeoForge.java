package com.github.squi2rel.arctools.neoforge;

import com.github.squi2rel.arctools.ArcTools;
import com.github.squi2rel.arctools.ArcToolsClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = ArcTools.MOD_ID, dist = Dist.CLIENT)
public final class ArcToolsNeoForge {
    public ArcToolsNeoForge(IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(ArcToolsClient::init));
    }
}
