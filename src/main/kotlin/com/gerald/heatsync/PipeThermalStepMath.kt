package com.gerald.heatsync

object PipeThermalStepMath {
    fun step(
        pipeHeat: Double,
        ambientCna: Double? = null,
        neighborAverage: Double? = null,
        sourceHeat: Double? = null,
        ambientBlendRate: Double,
        networkEqualizationStrength: Double,
        coldSourcePullRate: Double,
        pipeLossPerTick: Double,
        minPipeHeat: Double,
        maxPipeHeat: Double
    ): Double {
        var nextHeat = pipeHeat
        if (neighborAverage != null) {
            nextHeat += (neighborAverage - nextHeat) * networkEqualizationStrength
        }
        if (ambientCna != null) {
            nextHeat += (ambientCna - nextHeat) * ambientBlendRate
        }
        if (sourceHeat != null) {
            nextHeat += (sourceHeat - nextHeat) * coldSourcePullRate
        }
        nextHeat = (nextHeat - pipeLossPerTick).coerceAtLeast(minPipeHeat)
        return nextHeat.coerceIn(minPipeHeat, maxPipeHeat)
    }
}
