package com.gerald.heatsync

import com.mojang.logging.LogUtils
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.slf4j.Logger

@Mod(HeatSyncMod.MOD_ID)
class HeatSyncMod(modLoadingContext: FMLJavaModLoadingContext) {
    init {
        modLoadingContext.registerConfig(ModConfig.Type.COMMON, HeatSyncConfig.SPEC)

        if (ModList.get().isLoaded(HeatSyncColdSweatBridge.COLD_SWEAT_MOD_ID)) {
            val modBus = modLoadingContext.modEventBus
            HeatSyncColdSweatBridge.initialize(modBus)
            MinecraftForge.EVENT_BUS.register(HeatSyncPipeThermalController)
        }

        LOGGER.info("Loaded mod {}", MOD_ID)
    }

    companion object {
        const val MOD_ID: String = "heatsync"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
