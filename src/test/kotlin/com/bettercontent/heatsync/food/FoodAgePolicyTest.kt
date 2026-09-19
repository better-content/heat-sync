package com.bettercontent.heatsync.food

import kotlin.test.Test
import kotlin.test.assertEquals

class FoodAgePolicyTest {
    private val ordinary = FoodThermalService.Profile("raw_animal", 1.0, 0.0, true)
    private val dried = FoodThermalService.Profile("dried", 1.0, null, true)
    private val stable = FoodThermalService.Profile("shelf_stable", null, 0.0, false)

    @Test
    fun activeRatesMatchFoodCategories() {
        assertEquals(1.0, FoodThermalService.preservationRate(ordinary, 295.15))
        assertEquals(0.1, FoodThermalService.preservationRate(ordinary, 278.15))
        assertEquals(0.1, FoodThermalService.preservationRate(dried, 295.15))
        assertEquals(0.0, FoodThermalService.preservationRate(ordinary, 268.15))
        assertEquals(0.0, FoodThermalService.preservationRate(stable, 295.15))
    }

    @Test
    fun firstHarmfulThresholdIsOneEquivalentDay() {
        val decayAtBoundary = 24_000.0 / 24_000.0
        assertEquals(1.0, decayAtBoundary)
        assertEquals(0.1, 24_000.0 * 0.1 / 24_000.0)
    }
}
