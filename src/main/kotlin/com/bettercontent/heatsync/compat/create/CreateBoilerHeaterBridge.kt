package com.bettercontent.heatsync.compat.create

import com.bettercontent.heatsync.HeatSyncRegistries
import com.bettercontent.heatsync.content.heat.BoilerHeaterBlockEntity
import com.simibubi.create.api.boiler.BoilerHeater
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

object CreateBoilerHeaterBridge {
    fun register() {
        BoilerHeater.REGISTRY.register(HeatSyncRegistries.BOILER_HEATER.get(), ::findHeat)
    }

    internal fun findHeat(level: Level, pos: BlockPos, @Suppress("UNUSED_PARAMETER") state: BlockState): Float {
        val strength = (level.getBlockEntity(pos) as? BoilerHeaterBlockEntity)?.advertisedStrength() ?: 0f
        return if (strength > 0f) strength else BoilerHeater.NO_HEAT.toFloat()
    }
}
