package com.gerald.heatsync

import net.minecraftforge.common.ForgeConfigSpec

object HeatSyncConfig {
    private val builder = ForgeConfigSpec.Builder()

    private val absoluteZeroOffsetValue: ForgeConfigSpec.DoubleValue
    private val csToCnaScaleValue: ForgeConfigSpec.DoubleValue
    private val pipeMinHeatValue: ForgeConfigSpec.DoubleValue
    private val pipeMaxHeatValue: ForgeConfigSpec.DoubleValue

    private val ambientBlendRateValue: ForgeConfigSpec.DoubleValue
    private val pipeLossPerTickValue: ForgeConfigSpec.DoubleValue
    private val networkEqualizationStrengthValue: ForgeConfigSpec.DoubleValue
    private val coldSourcePullRateValue: ForgeConfigSpec.DoubleValue

    private val iceSourceHeatValue: ForgeConfigSpec.DoubleValue
    private val packedIceSourceHeatValue: ForgeConfigSpec.DoubleValue
    private val blueIceSourceHeatValue: ForgeConfigSpec.DoubleValue
    private val taggedColdSourceDefaultHeatValue: ForgeConfigSpec.DoubleValue

    private val pipeBlockTempRangeValue: ForgeConfigSpec.DoubleValue
    private val pipeBlockTempMaxEffectValue: ForgeConfigSpec.DoubleValue

    private val powerGridAmbientHeatValue: ForgeConfigSpec.DoubleValue
    private val powerGridAmbientTemperatureValue: ForgeConfigSpec.DoubleValue
    private val powerGridHeatPerDegreeValue: ForgeConfigSpec.DoubleValue
    private val powerGridMaxHeatValue: ForgeConfigSpec.DoubleValue

    val SPEC: ForgeConfigSpec

    init {
        builder.push("mapping")
        absoluteZeroOffsetValue = builder
            .comment("CNA heat value that corresponds to Cold Sweat 0 MC / 0 C.")
            .defineInRange("absolute_zero_offset", 100.0, 0.0, Double.MAX_VALUE)
        csToCnaScaleValue = builder
            .comment("How many CNA heat units correspond to one Cold Sweat MC unit.")
            .defineInRange("cs_to_cna_scale", 40.0, 0.0001, Double.MAX_VALUE)
        pipeMinHeatValue = builder
            .comment("Minimum pipe heat. Zero is absolute zero in the bridge model.")
            .defineInRange("pipe_min_heat", 0.0, 0.0, Double.MAX_VALUE)
        pipeMaxHeatValue = builder
            .comment("Maximum pipe heat.")
            .defineInRange("pipe_max_heat", 400.0, 0.0, Double.MAX_VALUE)
        builder.pop()

        builder.push("pipe_behavior")
        ambientBlendRateValue = builder
            .comment("How strongly local Cold Sweat ambient pulls pipe heat toward its baseline each update.")
            .defineInRange("ambient_blend_rate", 0.08, 0.0, 1.0)
        pipeLossPerTickValue = builder
            .comment("Passive loss toward CNA zero per update.")
            .defineInRange("pipe_loss_per_tick", 0.5, 0.0, Double.MAX_VALUE)
        networkEqualizationStrengthValue = builder
            .comment("How strongly connected neighbors pull a pipe toward their average heat.")
            .defineInRange("network_equalization_strength", 0.30, 0.0, 1.0)
        coldSourcePullRateValue = builder
            .comment("How strongly adjacent cold sources pull a pipe toward their source heat.")
            .defineInRange("cold_source_pull_rate", 0.18, 0.0, 1.0)
        builder.pop()

        builder.push("cold_sources")
        iceSourceHeatValue = builder
            .comment("Target CNA heat for adjacent ice.")
            .defineInRange("ice_source_heat", 80.0, 0.0, Double.MAX_VALUE)
        packedIceSourceHeatValue = builder
            .comment("Target CNA heat for adjacent packed ice.")
            .defineInRange("packed_ice_source_heat", 50.0, 0.0, Double.MAX_VALUE)
        blueIceSourceHeatValue = builder
            .comment("Target CNA heat for adjacent blue ice.")
            .defineInRange("blue_ice_source_heat", 20.0, 0.0, Double.MAX_VALUE)
        taggedColdSourceDefaultHeatValue = builder
            .comment("Target heat for any other block in heatsync:pipe_cold_sources.")
            .defineInRange("tagged_cold_source_default_heat", 80.0, 0.0, Double.MAX_VALUE)
        builder.pop()

        builder.push("radiation")
        pipeBlockTempRangeValue = builder
            .comment("How far pipe-emitted Cold Sweat block temperature reaches.")
            .defineInRange("pipe_blocktemp_range", 5.0, 0.0, Double.MAX_VALUE)
        pipeBlockTempMaxEffectValue = builder
            .comment("Absolute Cold Sweat MC temperature cap emitted by a pipe.")
            .defineInRange("pipe_blocktemp_max_effect", 0.75, 0.0, Double.MAX_VALUE)
        builder.pop()

        builder.push("power_grid")
        powerGridAmbientHeatValue = builder
            .comment("HeatSync heat value corresponding to Power Grid ambient device temperature.")
            .defineInRange("ambient_heat", 100.0, 0.0, Double.MAX_VALUE)
        powerGridAmbientTemperatureValue = builder
            .comment("Power Grid ambient device temperature in Celsius.")
            .defineInRange("ambient_temperature_c", 22.0, -273.15, Double.MAX_VALUE)
        powerGridHeatPerDegreeValue = builder
            .comment("HeatSync heat units per one Power Grid Celsius degree. Default maps 1200 C near HeatSync 400.")
            .defineInRange("heat_per_degree_c", 0.25466893039049235, 0.0001, Double.MAX_VALUE)
        powerGridMaxHeatValue = builder
            .comment("Maximum HeatSync heat exposed by Power Grid thermal devices.")
            .defineInRange("max_heat", 400.0, 1.0, Double.MAX_VALUE)
        builder.pop()

        SPEC = builder.build()
    }

    fun absoluteZeroOffset(): Double = absoluteZeroOffsetValue.get()

    fun csToCnaScale(): Double = csToCnaScaleValue.get()

    fun pipeMinHeat(): Double = pipeMinHeatValue.get()

    fun pipeMaxHeat(): Double = pipeMaxHeatValue.get()

    fun ambientBlendRate(): Double = ambientBlendRateValue.get()

    fun pipeLossPerTick(): Double = pipeLossPerTickValue.get()

    fun networkEqualizationStrength(): Double = networkEqualizationStrengthValue.get()

    fun coldSourcePullRate(): Double = coldSourcePullRateValue.get()

    fun iceSourceHeat(): Double = iceSourceHeatValue.get()

    fun packedIceSourceHeat(): Double = packedIceSourceHeatValue.get()

    fun blueIceSourceHeat(): Double = blueIceSourceHeatValue.get()

    fun taggedColdSourceDefaultHeat(): Double = taggedColdSourceDefaultHeatValue.get()

    fun pipeBlockTempRange(): Double = pipeBlockTempRangeValue.get()

    fun pipeBlockTempMaxEffect(): Double = pipeBlockTempMaxEffectValue.get()

    fun powerGridAmbientHeat(): Double = powerGridAmbientHeatValue.get()

    fun powerGridAmbientTemperature(): Double = powerGridAmbientTemperatureValue.get()

    fun powerGridHeatPerDegree(): Double = powerGridHeatPerDegreeValue.get()

    fun powerGridMaxHeat(): Double = powerGridMaxHeatValue.get()
}
