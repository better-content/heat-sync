package com.bettercontent.heatsync.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FoodStackMergeServiceTest {
    @Test
    fun `weighted values use destination count and actual moved count`() {
        val destination = values(temperatureK = 273.15, decay = 0.2, lastTime = 80)
        val source = values(temperatureK = 373.15, decay = 0.8, lastTime = 100)

        val merged = FoodStackMergeService.weighted(destination, 3, source, 1)

        assertEquals(298.15, merged.temperatureK, 1.0e-9)
        assertEquals(0.35, merged.decay, 1.0e-9)
        assertEquals(100, merged.lastTime)
    }

    @Test
    fun `temperature bucket is deterministic while decay retains fractions`() {
        assertEquals(4, FoodStackMergeService.bucketForKelvin(295.15))
        assertEquals(5, FoodStackMergeService.bucketForKelvin(295.65))
        val destination = values(295.15, 0.501, 100)
        val source = values(295.15, 0.506, 100)
        assertEquals(0.5035, FoodStackMergeService.weighted(destination, 1, source, 1).decay, 1.0e-9)
    }

    private fun values(temperatureK: Double, decay: Double, lastTime: Long) =
        FoodStackMergeService.ThermalValues(
            temperatureK,
            decay,
            lastTime,
            targetBucket = 4,
            targetAppliance = false,
            preservationRate = 1.0,
            present = true,
        )
}
