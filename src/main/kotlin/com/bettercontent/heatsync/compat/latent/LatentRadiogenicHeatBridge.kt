package com.bettercontent.heatsync.compat.latent

import com.bettercontent.heatsync.HeatSyncMod
import com.bettercontent.heatsync.api.HeatCapabilities
import com.bettercontent.heatsync.api.IHeatStorage
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus
import java.util.Collections
import java.util.IdentityHashMap
import java.util.function.Consumer

/** Consumes Latent's stable emission event without a binary dependency back from Latent to Heat Sync. */
object LatentRadiogenicHeatBridge {
    const val MOD_ID = "latent_chemlib"
    private const val EVENT_CLASS =
        "com.bettercontent.latentchemlib.sim.LatentRadiationService\u0024RadiationEmissionEvent"

    fun initialize(forgeBus: IEventBus) {
        try {
            registerEvent(forgeBus, Class.forName(EVENT_CLASS).asSubclass(Event::class.java))
            HeatSyncMod.LOGGER.info("Enabled Latent ChemLib radiogenic emission bridge")
        } catch (exception: ReflectiveOperationException) {
            HeatSyncMod.LOGGER.error("Latent ChemLib is loaded but its emission event API is unavailable", exception)
        }
    }

    private fun <T : Event> registerEvent(forgeBus: IEventBus, eventClass: Class<T>) {
        val levelMethod = eventClass.getMethod("level")
        val posMethod = eventClass.getMethod("pos")
        val heatMethod = eventClass.getMethod("heatStrength")
        forgeBus.addListener(EventPriority.NORMAL, false, eventClass, Consumer { event ->
            val level = levelMethod.invoke(event) as? ServerLevel ?: return@Consumer
            val pos = posMethod.invoke(event) as? BlockPos ?: return@Consumer
            val heat = (heatMethod.invoke(event) as? Number)?.toFloat() ?: return@Consumer
            if (heat <= 0f || !heat.isFinite()) return@Consumer
            RadiogenicHeatDistribution.distribute(heat, adjacentTargets(level, pos))
        })
    }

    private fun adjacentTargets(level: ServerLevel, pos: BlockPos): List<IHeatStorage> {
        val targets = mutableListOf<IHeatStorage>()
        val seen = Collections.newSetFromMap(IdentityHashMap<IHeatStorage, Boolean>())
        for (direction in Direction.values()) {
            val target = level.getBlockEntity(pos.relative(direction)) ?: continue
            target.getCapability(HeatCapabilities.HEAT, direction.opposite).ifPresent { storage ->
                if (storage.canAdd(direction.opposite) && seen.add(storage)) targets += storage
            }
        }
        return targets
    }
}
