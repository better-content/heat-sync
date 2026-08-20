package com.bettercontent.heatsync.compat.latent

import com.bettercontent.heatsync.api.IHeatStorage
import net.minecraft.core.Direction
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class RadiogenicHeatDistributionTest {
    @Test
    fun `distributes a finite emission fairly without duplication`() {
        val first = Storage(100f)
        val second = Storage(100f)

        val remainder = RadiogenicHeatDistribution.distribute(80f, listOf(first, second))

        assertEquals(0f, remainder)
        assertEquals(40f, first.storedHeat)
        assertEquals(40f, second.storedHeat)
    }

    @Test
    fun `redistributes rejected heat and returns unaccepted remainder`() {
        val nearlyFull = Storage(10f)
        val room = Storage(30f)

        val remainder = RadiogenicHeatDistribution.distribute(80f, listOf(nearlyFull, room))

        assertEquals(40f, remainder)
        assertEquals(10f, nearlyFull.storedHeat)
        assertEquals(30f, room.storedHeat)
    }

    private class Storage(private val capacity: Float) : IHeatStorage {
        var storedHeat = 0f
        override fun getHeat(): Float = storedHeat
        override fun getMaxHeat(): Float = capacity
        override fun addHeat(amount: Float, simulate: Boolean): Float {
            val accepted = min(amount, capacity - storedHeat)
            if (!simulate) storedHeat += accepted
            return accepted
        }
        override fun extractHeat(amount: Float, simulate: Boolean): Float = 0f
        override fun setHeat(heat: Float) {
            storedHeat = heat.coerceIn(0f, capacity)
        }
        override fun canConnect(side: Direction?): Boolean = true
    }
}
