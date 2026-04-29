package com.gerald.heatsync

import kotlin.test.Test
import kotlin.test.assertEquals

class HeatMappingMathTest {
    @Test
    fun `cold sweat to pipe heat applies offset scale and min clamp`() {
        assertEquals(100.0, HeatMappingMath.coldSweatToPipeHeat(0.0, 100.0, 40.0))
        assertEquals(60.0, HeatMappingMath.coldSweatToPipeHeat(-1.0, 100.0, 40.0))
        assertEquals(140.0, HeatMappingMath.coldSweatToPipeHeat(1.0, 100.0, 40.0))
        assertEquals(0.0, HeatMappingMath.coldSweatToPipeHeat(-2.5, 100.0, 40.0))
        assertEquals(10.0, HeatMappingMath.coldSweatToPipeHeat(-2.5, 100.0, 40.0, minPipeHeat = 10.0))
    }

    @Test
    fun `pipe heat to cold sweat inverts using same offset and scale`() {
        assertEquals(0.0, HeatMappingMath.pipeHeatToColdSweat(100.0, 100.0, 40.0))
        assertEquals(-2.0, HeatMappingMath.pipeHeatToColdSweat(20.0, 100.0, 40.0))
        assertEquals(1.5, HeatMappingMath.pipeHeatToColdSweat(160.0, 100.0, 40.0))
    }
}
