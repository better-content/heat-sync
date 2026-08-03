package com.gerald.heatsync.compat.pneumaticcraft

import com.gerald.heatsync.HeatSyncConfig
import com.gerald.heatsync.HeatSyncMod
import com.gerald.heatsync.api.HeatCapabilities
import com.gerald.heatsync.api.IHeatStorage
import me.desht.pneumaticcraft.api.PNCCapabilities
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.eventbus.api.IEventBus
import java.util.function.BiPredicate
import kotlin.math.roundToInt

/** Exposes every HeatSync storage as a conservative PneumaticCraft thermal body. */
object PneumaticHeatBridge {
    const val MOD_ID = "pneumaticcraft"
    private val ID = ResourceLocation.fromNamespaceAndPath(HeatSyncMod.MOD_ID, "pneumatic_heat")

    fun initialize(forgeBus: IEventBus) {
        forgeBus.addGenericListener(BlockEntity::class.java, ::attachCapabilities)
    }

    private fun attachCapabilities(event: AttachCapabilitiesEvent<BlockEntity>) {
        val heat = event.`object`.getCapability(HeatCapabilities.HEAT)
        if (!heat.isPresent) return
        val provider = PneumaticHeatProvider(heat)
        event.addCapability(ID, provider)
        event.addListener(provider::invalidate)
    }

    private class PneumaticHeatProvider(
        private val heat: LazyOptional<IHeatStorage>,
    ) : ICapabilityProvider {
        private val logic: LazyOptional<IHeatExchangerLogic> = LazyOptional.of {
            HeatSyncPneumaticLogic { heat.orElseThrow { IllegalStateException("HeatSync capability invalidated") } }
        }

        override fun <T : Any> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> =
            if (cap === PNCCapabilities.HEAT_EXCHANGER_CAPABILITY) logic.cast() else LazyOptional.empty()

        fun invalidate() {
            logic.invalidate()
        }
    }

    private class HeatSyncPneumaticLogic(
        private val storage: () -> IHeatStorage,
    ) : IHeatExchangerLogic {
        private var thermalResistance = 1.0
        private var thermalCapacity = 1.0

        override fun tick() = Unit

        override fun initializeAsHull(
            level: Level,
            pos: BlockPos,
            blocks: BiPredicate<LevelAccessor, BlockPos>,
            vararg validSides: Direction,
        ) = Unit

        override fun initializeAmbientTemperature(level: Level, pos: BlockPos) = Unit

        override fun setTemperature(temperature: Double) {
            storage().setHeat(toHeatSync(temperature).toFloat())
        }

        override fun getTemperature(): Double = toPneumatic(storage().getHeat().toDouble())

        override fun getTemperatureAsInt(): Int = temperature.roundToInt()

        override fun getAmbientTemperature(): Double = HeatSyncConfig.pneumaticAmbientTemperature()

        override fun setThermalResistance(resistance: Double) {
            thermalResistance = resistance.coerceAtLeast(0.0)
        }

        override fun getThermalResistance(): Double =
            maxOf(thermalResistance, storage().getThermalResistance().toDouble())

        override fun setThermalCapacity(capacity: Double) {
            thermalCapacity = capacity.coerceAtLeast(0.0001)
        }

        override fun getThermalCapacity(): Double =
            maxOf(thermalCapacity, storage().getThermalCapacity().toDouble())

        override fun addHeat(amount: Double) {
            if (amount > 0) {
                storage().addHeat((amount * HeatSyncConfig.pneumaticHeatPerKelvin()).toFloat(), false)
            } else if (amount < 0) {
                storage().extractHeat((-amount * HeatSyncConfig.pneumaticHeatPerKelvin()).toFloat(), false)
            }
        }

        override fun isSideConnected(side: Direction): Boolean = storage().canConnect(side)

        private fun toHeatSync(pneumaticKelvin: Double): Double =
            HeatSyncConfig.pneumaticAmbientHeat() +
                (pneumaticKelvin - HeatSyncConfig.pneumaticAmbientTemperature()) * HeatSyncConfig.pneumaticHeatPerKelvin()

        private fun toPneumatic(heat: Double): Double =
            HeatSyncConfig.pneumaticAmbientTemperature() +
                (heat - HeatSyncConfig.pneumaticAmbientHeat()) / HeatSyncConfig.pneumaticHeatPerKelvin()
    }
}
