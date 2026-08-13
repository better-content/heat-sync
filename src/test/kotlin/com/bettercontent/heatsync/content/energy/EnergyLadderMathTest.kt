package com.bettercontent.heatsync.content.energy

import kotlin.test.Test
import kotlin.test.assertEquals

class EnergyLadderMathTest {
    @Test
    fun `FE limits containment but never contributes AE`() {
        val plan = EnergyLadderMath.planUnbinding(
            availableUnits = 8,
            previewAe = 800.0,
            previewHeat = 40f,
            storedFe = 450,
            fePerUnit = 200,
            gridDemandAe = 1000.0,
            unitsPerTick = 4,
        )

        assertEquals(2, plan.units)
        assertEquals(400, plan.containmentFe)
        assertEquals(200.0, plan.ae)
        assertEquals(10f, plan.heat)
    }

    @Test
    fun `grid demand prevents unbinding and daughter production`() {
        val plan = EnergyLadderMath.planUnbinding(4, 400.0, 20f, 10_000, 200, 99.0, 4)
        assertEquals(UnbindingPlan(0, 0, 0.0, 0.0f), plan)
    }

    @Test
    fun `parallel scale remains fixed per transducer`() {
        val plan = EnergyLadderMath.planUnbinding(20, 2_000.0, 100f, 20_000, 200, 20_000.0, 3)
        assertEquals(3, plan.units)
        assertEquals(300.0, plan.ae)
    }
}
