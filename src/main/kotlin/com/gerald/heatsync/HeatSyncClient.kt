package com.gerald.heatsync

import com.gerald.heatsync.compat.ponder.HeatSyncPonderPlugin
import net.createmod.ponder.foundation.PonderIndex
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

object HeatSyncClient {
    fun register(modBus: IEventBus) {
        modBus.addListener(::onClientSetup)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            PonderIndex.addPlugin(HeatSyncPonderPlugin())
        }
    }
}
