package com.bettercontent.heatsync

import com.bettercontent.heatsync.compat.fiahi.FiahiHeatBridge
import com.bettercontent.heatsync.compat.fiahi.FiahiTooltipBridge
import com.bettercontent.heatsync.compat.ponder.HeatSyncPonderPlugin
import net.createmod.ponder.foundation.PonderIndex
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

object HeatSyncClient {
    fun register(modBus: IEventBus) {
        modBus.addListener(::onClientSetup)
        if (ModList.get().isLoaded(FiahiHeatBridge.MOD_ID)) {
            MinecraftForge.EVENT_BUS.addListener(FiahiTooltipBridge::onTooltip)
        }
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            PonderIndex.addPlugin(HeatSyncPonderPlugin())
        }
    }
}
