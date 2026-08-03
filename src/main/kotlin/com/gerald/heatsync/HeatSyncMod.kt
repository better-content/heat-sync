package com.gerald.heatsync

import com.gerald.heatsync.command.HeatSyncCommands
import com.gerald.heatsync.compat.powergrid.PowerGridHeatBridge
import com.gerald.heatsync.compat.pneumaticcraft.PneumaticHeatBridge
import com.gerald.heatsync.content.coolant.LiquidCoolantManager
import com.mojang.logging.LogUtils
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLEnvironment
import org.slf4j.Logger

@Mod(HeatSyncMod.MOD_ID)
class HeatSyncMod(modLoadingContext: FMLJavaModLoadingContext) {
    init {
        val modBus = modLoadingContext.modEventBus
        HeatSyncRegistries.register(modBus)
        if (ModList.get().isLoaded(HeatSyncAe2Registries.MOD_ID)) {
            HeatSyncAe2Registries.register(modBus)
        }
        if (FMLEnvironment.dist.isClient) {
            HeatSyncClient.register(modBus)
        }

        modLoadingContext.registerConfig(ModConfig.Type.COMMON, HeatSyncConfig.SPEC)
        MinecraftForge.EVENT_BUS.addListener(::onAddReloadListeners)
        MinecraftForge.EVENT_BUS.addListener(::onRegisterCommands)
        MinecraftForge.EVENT_BUS.register(HeatSyncPipeThermalController)

        if (ModList.get().isLoaded(HeatSyncColdSweatBridge.COLD_SWEAT_MOD_ID)) {
            HeatSyncColdSweatBridge.initialize(MinecraftForge.EVENT_BUS)
        }
        if (ModList.get().isLoaded(PowerGridHeatBridge.MOD_ID)) {
            PowerGridHeatBridge.initialize(MinecraftForge.EVENT_BUS)
        }
        if (ModList.get().isLoaded(PneumaticHeatBridge.MOD_ID)) {
            PneumaticHeatBridge.initialize(MinecraftForge.EVENT_BUS)
        }

        LOGGER.info("Loaded mod {}", MOD_ID)
    }

    private fun onAddReloadListeners(event: AddReloadListenerEvent) {
        event.addListener(LiquidCoolantManager)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        HeatSyncCommands.register(event.dispatcher)
    }

    companion object {
        const val MOD_ID: String = "heatsync"
        val LOGGER: Logger = LogUtils.getLogger()
    }
}
