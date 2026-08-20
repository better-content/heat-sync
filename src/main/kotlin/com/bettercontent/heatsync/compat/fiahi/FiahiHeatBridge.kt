package com.bettercontent.heatsync.compat.fiahi

import com.bettercontent.heatsync.HeatSyncConfig
import com.bettercontent.heatsync.HeatSyncMod
import com.bettercontent.heatsync.api.HeatCapabilities
import com.bettercontent.heatsync.api.IHeatStorage
import com.hexagram2021.fiahi.common.config.FIAHICommonConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.items.IItemHandlerModifiable
import net.minecraftforge.registries.ForgeRegistries
import java.util.IdentityHashMap

object FiahiHeatBridge {
    const val MOD_ID = "fiahi"
    private const val UPDATE_INTERVAL_TICKS = 20L
    private val indexedSources = mutableMapOf<ResourceKey<Level>, MutableSet<Long>>()

    fun initialize(forgeBus: IEventBus) {
        forgeBus.register(this)
        HeatSyncMod.LOGGER.info("Enabled Fiahi food heat hooks")
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val level = event.level as? net.minecraft.server.level.ServerLevel ?: return
        val chunk = event.chunk as? LevelChunk ?: return
        chunk.blockEntities.values
            .filter(::hasHeatCapability)
            .forEach { index(level, it.blockPos) }
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as? net.minecraft.server.level.ServerLevel ?: return
        val chunk = event.chunk as? LevelChunk ?: return
        indexedSources[level.dimension()]?.removeAll(chunk.blockEntities.keys.map { it.asLong() }.toSet())
    }

    @SubscribeEvent
    fun onBlockPlaced(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? net.minecraft.server.level.ServerLevel ?: return
        // Capability attachment can finish after the place event, so keep the position as a cheap candidate.
        index(level, event.pos)
    }

    @SubscribeEvent
    fun onBlockBroken(event: BlockEvent.BreakEvent) {
        val level = event.level as? net.minecraft.server.level.ServerLevel ?: return
        indexedSources[level.dimension()]?.remove(event.pos.asLong())
    }

    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        val level = event.level as? net.minecraft.server.level.ServerLevel ?: return
        indexedSources.remove(level.dimension())
    }

    @SubscribeEvent
    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val level = event.level as? net.minecraft.server.level.ServerLevel ?: return
        if (level.gameTime % UPDATE_INTERVAL_TICKS != 0L) return
        processLevel(level)
    }

    private fun processLevel(level: net.minecraft.server.level.ServerLevel) {
        val positions = indexedSources[level.dimension()] ?: return
        val targets = IdentityHashMap<ItemStack, TargetExposure>()
        val iterator = positions.iterator()
        while (iterator.hasNext()) {
            val pos = BlockPos.of(iterator.next())
            if (!level.hasChunkAt(pos)) continue
            val source = level.getBlockEntity(pos)
            if (source == null || !hasHeatCapability(source)) {
                iterator.remove()
                continue
            }
            collectOwnInventory(source, targets)
            for (direction in Direction.values()) {
                collectAdjacentInventory(level, source, direction, targets)
            }
        }
        targets.values.forEach(::applyExposure)
    }

    private fun collectOwnInventory(
        source: BlockEntity,
        targets: IdentityHashMap<ItemStack, TargetExposure>,
    ) {
        if (isStableContainer(source)) return
        val heat = extractableHeat(source) ?: return
        val handler = source.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null)
            as? IItemHandlerModifiable ?: return
        collectSlots(source, handler, heat, targets)
    }

    private fun collectAdjacentInventory(
        level: net.minecraft.server.level.ServerLevel,
        source: BlockEntity,
        direction: Direction,
        targets: IdentityHashMap<ItemStack, TargetExposure>,
    ) {
        val heat = source.getCapability(HeatCapabilities.HEAT, direction).resolve().orElse(null) ?: return
        if (!heat.canConnect(direction) || !heat.canExtract(direction)) return
        val targetPos = source.blockPos.relative(direction)
        if (!level.hasChunkAt(targetPos)) return
        val target = level.getBlockEntity(targetPos) ?: return
        if (isStableContainer(target)) return
        val handler = target.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.opposite)
            .resolve().orElse(null) as? IItemHandlerModifiable ?: return
        collectSlots(target, handler, heat, targets)
    }

    private fun collectSlots(
        owner: BlockEntity,
        handler: IItemHandlerModifiable,
        heat: IHeatStorage,
        targets: IdentityHashMap<ItemStack, TargetExposure>,
    ) {
        val sample = ThermalSample(
            temperature = FiahiHeatMath.heatToFoodTemperature(
                heat = heat.getHeat().toDouble(),
                absoluteZeroOffset = HeatSyncConfig.absoluteZeroOffset(),
                heatPerMinecraftUnit = HeatSyncConfig.csToCnaScale(),
            ),
            capacity = heat.getThermalCapacity().toDouble(),
            resistance = heat.getThermalResistance().toDouble(),
        )
        for (slot in 0 until handler.slots) {
            val stack = handler.getStackInSlot(slot)
            if (stack.isEmpty) continue
            val exposure = targets.computeIfAbsent(stack) { TargetExposure(owner, handler, slot, stack) }
            exposure.samples.putIfAbsent(heat, sample)
        }
    }

    private fun applyExposure(exposure: TargetExposure) {
        val target = FiahiHeatMath.weightedTarget(exposure.samples.values) ?: return
        val result = FiahiFoodTemperatureAdapter.heat(exposure.stack, target)
        if (!result.changed) return
        if (result.stack !== exposure.stack) {
            exposure.handler.setStackInSlot(exposure.slot, result.stack)
        }
        exposure.owner.setChanged()
    }

    private fun hasHeatCapability(blockEntity: BlockEntity): Boolean =
        blockEntity.getCapability(HeatCapabilities.HEAT).isPresent ||
            Direction.values().any { blockEntity.getCapability(HeatCapabilities.HEAT, it).isPresent }

    private fun extractableHeat(blockEntity: BlockEntity): IHeatStorage? {
        blockEntity.getCapability(HeatCapabilities.HEAT).resolve().orElse(null)?.let { heat ->
            if (heat.canConnect(null) && heat.canExtract(null)) return heat
        }
        for (direction in Direction.values()) {
            blockEntity.getCapability(HeatCapabilities.HEAT, direction).resolve().orElse(null)?.let { heat ->
                if (heat.canConnect(direction) && heat.canExtract(direction)) return heat
            }
        }
        return null
    }

    private fun isStableContainer(blockEntity: BlockEntity): Boolean {
        val id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.type) ?: return false
        return FIAHICommonConfig.STABLE_TEMPERATURE_CONTAINERS.get().contains(id.toString())
    }

    private fun index(level: net.minecraft.server.level.ServerLevel, pos: BlockPos) {
        indexedSources.getOrPut(level.dimension()) { mutableSetOf() }.add(pos.asLong())
    }

    private class TargetExposure(
        val owner: BlockEntity,
        val handler: IItemHandlerModifiable,
        val slot: Int,
        val stack: ItemStack,
    ) {
        val samples: IdentityHashMap<IHeatStorage, ThermalSample> = IdentityHashMap()
    }
}
