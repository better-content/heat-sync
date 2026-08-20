package com.bettercontent.heatsync.compat.fiahi;

import com.hexagram2021.fiahi.common.config.FIAHICommonConfig;
import com.hexagram2021.fiahi.common.item.FoodPouchItem;
import com.hexagram2021.fiahi.common.item.capability.IFrozenRottenFood;
import com.hexagram2021.fiahi.register.FIAHICapabilities;
import com.hexagram2021.fiahi.register.FIAHIItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class FiahiApplianceHooks {
    public static final int POUCH_CHANGED = 1;
    public static final int LOOSE_FOOD_PRESENT = 2;
    private static final String POUCH_TEMPERATURE_KEY = "temperature";
    private static final double LOOSE_SUPPLEMENT_PER_SECOND =
            FiahiHeatMath.DIRECT_DEGREES_PER_SECOND - 1.0;

    private FiahiApplianceHooks() {
    }

    public static int applyDirectStep(
            final Container inventory,
            final int[] slots,
            final double targetTemperature
    ) {
        int result = 0;
        for (final int slot : slots) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(FIAHIItems.FOOD_POUCH.get())) {
                if (applyPouch(stack, targetTemperature)) {
                    result |= POUCH_CHANGED;
                }
                continue;
            }
            if (!IFrozenRottenFood.canBeFrozenRotten(stack)) continue;
            result |= LOOSE_FOOD_PRESENT;
            stack.getCapability(FIAHICapabilities.FOOD_CAPABILITY).ifPresent(food ->
                    FiahiTemperatureHooks.applyDirectSupplement(
                            food,
                            targetTemperature,
                            stack.getItem(),
                            LOOSE_SUPPLEMENT_PER_SECOND
                    )
            );
        }
        if (result != 0) {
            inventory.setChanged();
        }
        return result;
    }

    private static boolean applyPouch(final ItemStack stack, final double targetTemperature) {
        final CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        final int itemCount = FoodPouchItem.getItemCount(tag);
        if (itemCount <= 0) return false;
        final double current = tag.getDouble(POUCH_TEMPERATURE_KEY);
        final double next = FiahiHeatMath.balanceDirectTemperature(
                current,
                targetTemperature,
                FIAHICommonConfig.FROZEN_SPEED_MULTIPLIER.get(),
                FIAHICommonConfig.ROTTEN_SPEED_MULTIPLIER.get(),
                FiahiHeatMath.effectivePouchMass(itemCount)
        );
        if (Double.compare(current, next) == 0) return false;
        tag.putDouble(POUCH_TEMPERATURE_KEY, next);
        stack.setTag(tag);
        return true;
    }
}
