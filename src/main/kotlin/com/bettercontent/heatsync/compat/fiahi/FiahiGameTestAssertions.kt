package com.bettercontent.heatsync.compat.fiahi

import com.hexagram2021.fiahi.common.item.capability.IFrozenRottenFood
import com.hexagram2021.fiahi.register.FIAHICapabilities
import com.hexagram2021.fiahi.register.FIAHIItems
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Loaded only by the FIAHI-present GameTest profile. */
object FiahiGameTestAssertions {
    fun assertReversibleTemperatureTransfer(helper: GameTestHelper) {
        val stack = ItemStack(Items.COOKED_BEEF)
        val food = stack.getCapability(FIAHICapabilities.FOOD_CAPABILITY).resolve()
            .orElseThrow { IllegalStateException("FIAHI food capability was unavailable") }

        food.temperature = FiahiHeatMath.MIN_FOOD_TEMPERATURE
        FiahiAmbientContext.pushMinecraftTemperature(1.0)
        try {
            repeat(11) { food.foodTick(food.temperature, stack.item) }
        } finally {
            FiahiAmbientContext.pop()
        }
        helper.assertTrue(food.temperature > -25.0, "Deeply frozen food did not thaw through its tier boundary")

        food.temperature = 75.0
        repeat(15) { FiahiTemperatureHooks.applyDirect(food, -25.0, stack.item, 1) }
        helper.assertTrue(food.temperature <= 25.0, "Rotten food did not cool back to the fresh boundary")

        val leftover = ItemStack(FIAHIItems.LEFTOVER_MEAT.get())
        helper.assertTrue(
            !IFrozenRottenFood.canBeFrozenRotten(leftover) &&
                !leftover.getCapability(FIAHICapabilities.FOOD_CAPABILITY).isPresent,
            "Converted leftovers unexpectedly became reversible food",
        )
        helper.succeed()
    }
}
