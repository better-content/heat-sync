package com.gerald.heatsync

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.antarcticgardens.cna.content.heat.pipe.HeatPipeBlock
import org.antarcticgardens.cna.content.heat.HeatBlockEntity
import org.antarcticgardens.cna.content.heat.pipe.HeatPipeBlockEntity
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlin.math.abs

object HeatSyncPipeThermalController {
    private const val UPDATE_INTERVAL_TICKS: Long = 20
    private const val MIN_HEAT_DELTA: Double = 0.01
    private val DIRECTIONS: Array<Direction> = Direction.values()
    private val trackedPipePositionsByLevel = mutableMapOf<ResourceKey<Level>, LongOpenHashSet>()

    private data class ThermalStepParameters(
        val ambientBlendRate: Double,
        val networkEqualizationStrength: Double,
        val coldSourcePullRate: Double,
        val pipeLossPerTick: Double,
        val minPipeHeat: Double,
        val maxPipeHeat: Double
    )

    @SubscribeEvent
    fun onLevelLoad(event: LevelEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        trackedPipePositions(level)
    }

    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        trackedPipePositionsByLevel.remove(level.dimension())
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        val chunk = event.chunk as? LevelChunk ?: return
        indexChunk(level, chunk)
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        val tracked = trackedPipePositionsByLevel[level.dimension()] ?: return
        if (tracked.isEmpty()) {
            return
        }

        val chunkPos = event.chunk.pos
        val iterator = tracked.iterator()
        while (iterator.hasNext()) {
            val packedPos = iterator.nextLong()
            if (SectionPos.blockToSectionCoord(BlockPos.getX(packedPos)) != chunkPos.x) {
                continue
            }
            if (SectionPos.blockToSectionCoord(BlockPos.getZ(packedPos)) != chunkPos.z) {
                continue
            }
            iterator.remove()
        }
    }

    @SubscribeEvent
    fun onBlockPlaced(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        if (!isHeatPipeState(event.placedBlock)) {
            return
        }

        trackedPipePositions(level).add(event.pos.asLong())
    }

    @SubscribeEvent
    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val level = event.level as? ServerLevel ?: return
        if ((level.gameTime % UPDATE_INTERVAL_TICKS) != 0L) {
            return
        }

        val tracked = trackedPipePositionsByLevel[level.dimension()] ?: return
        if (tracked.isEmpty()) {
            return
        }

        val parameters = ThermalStepParameters(
            ambientBlendRate = HeatSyncConfig.ambientBlendRate(),
            networkEqualizationStrength = HeatSyncConfig.networkEqualizationStrength(),
            coldSourcePullRate = HeatSyncConfig.coldSourcePullRate(),
            pipeLossPerTick = HeatSyncConfig.pipeLossPerTick(),
            minPipeHeat = HeatSyncConfig.pipeMinHeat(),
            maxPipeHeat = HeatSyncConfig.pipeMaxHeat()
        )

        val iterator = tracked.iterator()
        while (iterator.hasNext()) {
            val pipePos = BlockPos.of(iterator.nextLong())
            if (!level.isLoaded(pipePos)) {
                iterator.remove()
                continue
            }

            val pipe = level.getBlockEntity(pipePos) as? HeatPipeBlockEntity
            if (pipe == null || pipe.isRemoved) {
                iterator.remove()
                continue
            }

            updatePipe(level, pipe, parameters)
        }
    }

    private fun updatePipe(level: Level, pipe: HeatPipeBlockEntity, parameters: ThermalStepParameters) {
        val currentHeat = pipe.heat.toDouble()
        val ambientHeat = ColdSweatAmbientSampler.samplePipeHeat(level, pipe.blockPos)
        val neighborAverage = resolveNeighborAverage(level, pipe.blockPos, pipe)
        val sourceHeat = resolveSourceHeat(level, pipe.blockPos)
        val nextHeat = PipeThermalMath.step(
            pipeHeat = currentHeat,
            ambientCna = ambientHeat,
            neighborAverage = neighborAverage,
            sourceHeat = sourceHeat,
            ambientBlendRate = parameters.ambientBlendRate,
            networkEqualizationStrength = parameters.networkEqualizationStrength,
            coldSourcePullRate = parameters.coldSourcePullRate,
            pipeLossPerTick = parameters.pipeLossPerTick,
            minPipeHeat = parameters.minPipeHeat,
            maxPipeHeat = parameters.maxPipeHeat
        )

        if (abs(nextHeat - currentHeat) < MIN_HEAT_DELTA) {
            return
        }

        pipe.setHeat(nextHeat.toFloat())
        HeatBlockEntity.trySync(pipe)
    }

    private fun resolveNeighborAverage(level: Level, pos: BlockPos, pipe: HeatPipeBlockEntity): Double? {
        var totalHeat = 0.0
        var neighborCount = 0

        for (direction in DIRECTIONS) {
            val neighbor = level.getBlockEntity(pos.relative(direction)) as? HeatBlockEntity ?: continue
            if (!neighbor.canAdd(direction) || !pipe.canAdd(direction.opposite)) {
                continue
            }

            totalHeat += neighbor.heat.toDouble()
            neighborCount++
        }

        return if (neighborCount == 0) null else totalHeat / neighborCount
    }

    private fun resolveSourceHeat(level: Level, pos: BlockPos): Double? =
        PipeThermalSourceResolver.resolveAdjacentAverageTargetHeat(level, pos)

    private fun trackedPipePositions(level: ServerLevel): LongOpenHashSet =
        trackedPipePositionsByLevel.getOrPut(level.dimension(), ::LongOpenHashSet)

    private fun indexChunk(level: ServerLevel, chunk: LevelChunk) {
        val tracked = trackedPipePositions(level)
        for ((pos, blockEntity) in chunk.blockEntities) {
            if (blockEntity is HeatPipeBlockEntity && !blockEntity.isRemoved) {
                tracked.add(pos.asLong())
            }
        }
    }

    private fun isHeatPipeState(state: BlockState): Boolean = state.block is HeatPipeBlock
}
