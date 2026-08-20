package com.bettercontent.heatsync.compat.fiahi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FiahiHeatMathTest {
    @Test
    fun `heat conversion preserves physical hot and cold extremes`() {
        assertEquals(0.0, FiahiHeatMath.heatToFoodTemperature(100.0, 100.0, 40.0))
        assertEquals(-112.5, FiahiHeatMath.heatToFoodTemperature(0.0, 100.0, 40.0))
        assertEquals(124.99, FiahiHeatMath.heatToFoodTemperature(400.0, 100.0, 40.0))
    }

    @Test
    fun `multiple sources use capacity and resistance weighted equilibrium`() {
        val target = FiahiHeatMath.weightedTarget(
            listOf(
                ThermalSample(100.0, 20.0, 2.0),
                ThermalSample(-100.0, 10.0, 2.0),
            ),
        )
        assertEquals(100.0 / 3.0, target!!, 0.000_001)
        assertNull(FiahiHeatMath.weightedTarget(emptyList()))
    }

    @Test
    fun `absolute ambient conversion uses Celsius while heat mapping remains relative`() {
        assertEquals(25.0, FiahiHeatMath.ambientMinecraftToCelsius(1.0))
        assertEquals(-25.0, FiahiHeatMath.ambientMinecraftToCelsius(-1.0))
    }

    @Test
    fun `ambient warming crosses frozen tiers in about one minute`() {
        var temperature = FiahiHeatMath.MIN_FOOD_TEMPERATURE
        repeat(10) {
            temperature = FiahiHeatMath.balanceAmbientTemperature(
                current = temperature,
                target = 25.0,
                temperatureRate = 1.0,
                balanceRate = 0.1,
                frozenMultiplier = 1.0,
                rottenMultiplier = 0.75,
            )
        }
        assertTrue(temperature < -25.0)
        temperature = FiahiHeatMath.balanceAmbientTemperature(
            current = temperature,
            target = 25.0,
            temperatureRate = 1.0,
            balanceRate = 0.1,
            frozenMultiplier = 1.0,
            rottenMultiplier = 0.75,
        )
        assertTrue(temperature > -25.0)
    }

    @Test
    fun `direct transfer recovers one hundred degrees in thirty seconds`() {
        var temperature = -124.99
        repeat(29) {
            temperature = FiahiHeatMath.balanceDirectTemperature(temperature, 25.0, 1.0, 0.75)
        }
        assertTrue(temperature < -25.0)
        temperature = FiahiHeatMath.balanceDirectTemperature(temperature, 25.0, 1.0, 0.75)
        assertTrue(temperature >= -25.0)
    }

    @Test
    fun `direct cooling reverses rotten tiers`() {
        var temperature = 75.0
        repeat(15) {
            temperature = FiahiHeatMath.balanceDirectTemperature(temperature, -25.0, 1.0, 0.75)
        }
        assertEquals(25.0, temperature, 0.000_001)
    }

    @Test
    fun `pouch thermal mass is capped at four items`() {
        assertEquals(1, FiahiHeatMath.effectivePouchMass(1))
        assertEquals(4, FiahiHeatMath.effectivePouchMass(64))
        assertEquals(
            FiahiHeatMath.DIRECT_DEGREES_PER_SECOND / 4.0,
            FiahiHeatMath.balanceDirectTemperature(
                current = 0.0,
                target = 100.0,
                frozenMultiplier = 1.0,
                rottenMultiplier = 1.0,
                thermalMass = FiahiHeatMath.effectivePouchMass(64),
            ),
        )
    }
}
