package com.gerald.heatsync

import com.momosoftworks.coldsweat.api.event.core.registry.BlockTempRegisterEvent
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block
import net.minecraftforge.eventbus.api.IEventBus

object HeatSyncColdSweatBridge {
    const val COLD_SWEAT_MOD_ID: String = "cold_sweat"

    val PIPE_COLD_SOURCES: TagKey<Block> =
        BlockTags.create(ResourceLocation.fromNamespaceAndPath(HeatSyncMod.MOD_ID, "pipe_cold_sources"))

    val PIPE_RADIATORS: TagKey<Block> =
        BlockTags.create(ResourceLocation.fromNamespaceAndPath(HeatSyncMod.MOD_ID, "pipe_radiators"))

    fun initialize(modBus: IEventBus) {
        modBus.addListener(::registerBlockTemps)
        HeatSyncMod.LOGGER.info("Enabled Cold Sweat bridge hooks")
    }

    private fun registerBlockTemps(event: BlockTempRegisterEvent) {
        event.register(PipeBlockTemp())
    }
}
