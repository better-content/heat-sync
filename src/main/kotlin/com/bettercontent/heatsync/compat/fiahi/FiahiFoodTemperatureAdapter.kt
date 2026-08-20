package com.bettercontent.heatsync.compat.fiahi

import com.hexagram2021.fiahi.common.config.FIAHICommonConfig
import com.hexagram2021.fiahi.register.FIAHICapabilities
import com.momosoftworks.coldsweat.config.ConfigSettings
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries
import kotlin.math.abs

internal object FiahiFoodTemperatureAdapter {
    private val foodPouchId = ResourceLocation.fromNamespaceAndPath("fiahi", "food_pouch")
    private val leftoverMeatId = ResourceLocation.fromNamespaceAndPath("fiahi", "leftover_meat")
    private val leftoverVegetableId = ResourceLocation.fromNamespaceAndPath("fiahi", "leftover_vegetable")

    fun heat(stack: ItemStack, targetTemperature: Double): HeatingResult {
        if (stack.isEmpty) return HeatingResult(stack, false)
        val boundedTarget = targetTemperature.coerceIn(
            FiahiHeatMath.MIN_FOOD_TEMPERATURE,
            FiahiHeatMath.MAX_FOOD_TEMPERATURE,
        )
        if (ForgeRegistries.ITEMS.getKey(stack.item) == foodPouchId) {
            return heatPouch(stack, boundedTarget)
        }

        var changed = false
        var result = stack
        stack.getCapability(FIAHICapabilities.FOOD_CAPABILITY).ifPresent { food ->
            val previousTemperature = food.temperature
            food.foodTick(boundedTarget, stack.item)
            changed = abs(food.temperature - previousTemperature) > EPSILON
            if (food.temperature > SPOILED_TEMPERATURE) {
                val foodProperties = stack.item.foodProperties
                if (foodProperties != null) {
                    val leftoverId = if (foodProperties.isMeat) leftoverMeatId else leftoverVegetableId
                    ForgeRegistries.ITEMS.getValue(leftoverId)?.let { leftover ->
                        result = ItemStack(leftover, stack.count)
                        changed = true
                    }
                }
            }
        }
        return HeatingResult(result, changed)
    }

    private fun heatPouch(stack: ItemStack, targetTemperature: Double): HeatingResult {
        val tag = stack.tag ?: return HeatingResult(stack, false)
        val itemCount = pouchItemCount(tag)
        if (itemCount <= 0) return HeatingResult(stack, false)
        val current = tag.getDouble(POUCH_TEMPERATURE_KEY)
        val balanced = FiahiHeatMath.balancePouchTemperature(
            current = current,
            target = targetTemperature,
            temperatureRate = ConfigSettings.TEMP_RATE.get(),
            balanceRate = FIAHICommonConfig.TEMPERATURE_BALANCE_RATE.get() / 100.0,
            frozenMultiplier = FIAHICommonConfig.FROZEN_SPEED_MULTIPLIER.get(),
            rottenMultiplier = FIAHICommonConfig.ROTTEN_SPEED_MULTIPLIER.get(),
            itemCount = itemCount,
        )
        if (abs(balanced - current) <= EPSILON) return HeatingResult(stack, false)
        tag.putDouble(POUCH_TEMPERATURE_KEY, balanced)
        stack.tag = tag
        return HeatingResult(stack, true)
    }

    private fun pouchItemCount(tag: net.minecraft.nbt.CompoundTag): Int {
        if (!tag.contains(POUCH_ITEMS_KEY, Tag.TAG_LIST.toInt())) return 0
        val items = tag.getList(POUCH_ITEMS_KEY, Tag.TAG_COMPOUND.toInt())
        var count = 0
        for (index in 0 until items.size) {
            count += items.getCompound(index).getByte("Count").toInt().coerceAtLeast(0)
        }
        return count
    }

    private const val POUCH_TEMPERATURE_KEY = "temperature"
    private const val POUCH_ITEMS_KEY = "Items"
    private const val SPOILED_TEMPERATURE = 120.0
    private const val EPSILON = 0.000_001
}

internal data class HeatingResult(val stack: ItemStack, val changed: Boolean)
