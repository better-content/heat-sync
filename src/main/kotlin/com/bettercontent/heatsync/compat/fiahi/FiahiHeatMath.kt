package com.bettercontent.heatsync.compat.fiahi

import kotlin.math.max

internal object FiahiHeatMath {
    const val MIN_FOOD_TEMPERATURE = -124.99
    const val MAX_FOOD_TEMPERATURE = 124.99
    private const val CELSIUS_PER_MINECRAFT_UNIT = 45.0

    fun heatToFoodTemperature(
        heat: Double,
        absoluteZeroOffset: Double,
        heatPerMinecraftUnit: Double,
    ): Double {
        if (!heat.isFinite() || !absoluteZeroOffset.isFinite() ||
            !heatPerMinecraftUnit.isFinite() || heatPerMinecraftUnit <= 0.0
        ) {
            return 0.0
        }
        return (((heat - absoluteZeroOffset) / heatPerMinecraftUnit) * CELSIUS_PER_MINECRAFT_UNIT)
            .coerceIn(MIN_FOOD_TEMPERATURE, MAX_FOOD_TEMPERATURE)
    }

    fun weightedTarget(samples: Iterable<ThermalSample>): Double? {
        var weightedTemperature = 0.0
        var totalWeight = 0.0
        for (sample in samples) {
            if (!sample.temperature.isFinite()) continue
            val capacity = sample.capacity.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
            val resistance = sample.resistance.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
            val weight = max(capacity / resistance, java.lang.Double.MIN_NORMAL)
            weightedTemperature += sample.temperature * weight
            totalWeight += weight
        }
        if (!weightedTemperature.isFinite() || !totalWeight.isFinite() || totalWeight <= 0.0) return null
        return (weightedTemperature / totalWeight).coerceIn(MIN_FOOD_TEMPERATURE, MAX_FOOD_TEMPERATURE)
    }

    fun balancePouchTemperature(
        current: Double,
        target: Double,
        temperatureRate: Double,
        balanceRate: Double,
        frozenMultiplier: Double,
        rottenMultiplier: Double,
        itemCount: Int,
    ): Double {
        if (itemCount <= 0 || !current.isFinite() || !target.isFinite()) return current
        var delta = (target - current) * temperatureRate
        if (delta < 0.0 && current < 0.0) delta *= frozenMultiplier
        if (delta > 0.0 && current > 0.0) delta *= rottenMultiplier
        return (current + (delta * balanceRate / itemCount))
            .coerceIn(MIN_FOOD_TEMPERATURE, MAX_FOOD_TEMPERATURE)
    }
}

internal data class ThermalSample(
    val temperature: Double,
    val capacity: Double,
    val resistance: Double,
)
