package com.bettercontent.heatsync

import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.block.Block

/** Thermal datapack contracts shared by core behavior and optional integrations. */
object HeatSyncThermalTags {
    val PIPE_COLD_SOURCES: TagKey<Block> =
        BlockTags.create(ResourceLocation.fromNamespaceAndPath(HeatSyncMod.MOD_ID, "pipe_cold_sources"))

    val PIPE_RADIATORS: TagKey<Block> =
        BlockTags.create(ResourceLocation.fromNamespaceAndPath(HeatSyncMod.MOD_ID, "pipe_radiators"))

    val THERMAL_EMITTERS: TagKey<Block> =
        BlockTags.create(ResourceLocation.fromNamespaceAndPath(HeatSyncMod.MOD_ID, "thermal_emitters"))
}
