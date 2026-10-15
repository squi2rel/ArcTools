package com.github.squi2rel.arctools.forge;

import com.github.squi2rel.arctools.ArcTools;
import com.github.squi2rel.arctools.ArcToolsClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(ArcTools.MOD_ID)
public final class ArcToolsForge {
    public ArcToolsForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) ArcToolsClient.init();
    }
}
