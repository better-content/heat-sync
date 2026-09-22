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
        val beforeBoundary = FoodAgePolicy.advanceDecay(0.0, 23_999, 1.0, ordinary.days)
        val atBoundary = FoodAgePolicy.advanceDecay(0.0, 24_000, 1.0, ordinary.days)

        assertEquals(FoodThermalService.Stage.FRESH, FoodAgePolicy.stage(beforeBoundary))
        assertEquals(FoodThermalService.Stage.STALE, FoodAgePolicy.stage(atBoundary))
    }

    @Test
    fun preservationRateUsesActiveTimeAndIsIndependentOfUpdatePartitioning() {
        val oneUpdate = FoodAgePolicy.advanceDecay(0.0, 240_000, 0.1, ordinary.days)
        var manyUpdates = 0.0
        repeat(10) { manyUpdates = FoodAgePolicy.advanceDecay(manyUpdates, 24_000, 0.1, ordinary.days) }

        assertEquals(1.0, oneUpdate, 1.0e-12)
        assertEquals(oneUpdate, manyUpdates, 1.0e-12)
        assertEquals(FoodThermalService.Stage.STALE, FoodAgePolicy.stage(oneUpdate))
    }

    @Test
    fun frozenStableAndStoppedIntervalsDoNotAdvanceAge() {
        assertEquals(0.25, FoodAgePolicy.advanceDecay(0.25, 24_000, 0.0, ordinary.days))
        assertEquals(0.25, FoodAgePolicy.advanceDecay(0.25, 24_000, 1.0, stable.days))
        assertEquals(0.25, FoodAgePolicy.advanceDecay(0.25, 0, 1.0, ordinary.days))
        assertEquals(0.25, FoodAgePolicy.advanceDecay(0.25, -20, 1.0, ordinary.days))
    }

    @Test
    fun decayIsBoundedAtConversionThreshold() {
        assertEquals(2.5, FoodAgePolicy.advanceDecay(2.49, 24_000, 1.0, ordinary.days))
        assertEquals(FoodThermalService.Stage.CONVERTED, FoodAgePolicy.stage(2.5))
    }
}
