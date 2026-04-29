package com.gerald.heatsync

import com.momosoftworks.coldsweat.util.world.WorldHelper
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

object ColdSweatAmbientSampler {
    private val suppressPipeBlockTemp = ThreadLocal.withInitial { false }

    fun sampleWorldTemp(level: Level, pos: BlockPos): Double {
        suppressPipeBlockTemp.set(true)
        return try {
            WorldHelper.getTemperatureAt(level, pos)
        } finally {
            suppressPipeBlockTemp.set(false)
        }
    }

    fun samplePipeHeat(level: Level, pos: BlockPos): Double =
        ColdSweatHeatMapper.coldSweatToPipeHeat(sampleWorldTemp(level, pos))

    fun shouldSuppressPipeBlockTemp(): Boolean = suppressPipeBlockTemp.get()
}
