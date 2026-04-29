package com.gerald.heatsync

import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import org.antarcticgardens.cna.content.heat.HeatBlockEntity
import org.antarcticgardens.cna.content.heat.pipe.HeatPipeBlock

class PipeBlockTemp : BlockTemp(
    -HeatSyncConfig.pipeBlockTempMaxEffect(),
    HeatSyncConfig.pipeBlockTempMaxEffect(),
    Double.NEGATIVE_INFINITY,
    Double.POSITIVE_INFINITY,
    HeatSyncConfig.pipeBlockTempRange(),
    true
) {
    override fun getTemperature(
        level: Level,
        entity: LivingEntity?,
        state: BlockState,
        pos: BlockPos,
        distance: Double
    ): Double {
        if (ColdSweatAmbientSampler.shouldSuppressPipeBlockTemp()) {
            return 0.0
        }

        val blockEntity = level.getBlockEntity(pos)
        val pipeHeat = when (blockEntity) {
            is HeatBlockEntity -> blockEntity.heat.toDouble()
            is PipeHeatProvider -> blockEntity.pipeHeat
            else -> return 0.0
        }
        val emitted = ColdSweatHeatMapper.pipeHeatToColdSweat(pipeHeat)
        val maxEffect = HeatSyncConfig.pipeBlockTempMaxEffect()
        return emitted.coerceIn(-maxEffect, maxEffect)
    }

    override fun hasBlock(block: Block): Boolean =
        block is HeatPipeBlock || block.defaultBlockState().`is`(HeatSyncColdSweatBridge.PIPE_RADIATORS)

    override fun isValid(level: Level, pos: BlockPos, state: BlockState): Boolean =
        (state.block is HeatPipeBlock || state.`is`(HeatSyncColdSweatBridge.PIPE_RADIATORS))
            && (level.getBlockEntity(pos) is HeatBlockEntity || level.getBlockEntity(pos) is PipeHeatProvider)
}
