package com.bettercontent.heatsync.compat.fiahi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `pouch thermal mass divides transfer by contained item count`() {
        assertEquals(
            1.0,
            FiahiHeatMath.balancePouchTemperature(
                current = 0.0,
                target = 100.0,
                temperatureRate = 1.0,
                balanceRate = 0.1,
                frozenMultiplier = 1.0,
                rottenMultiplier = 1.0,
                itemCount = 10,
            ),
        )
    }
}
