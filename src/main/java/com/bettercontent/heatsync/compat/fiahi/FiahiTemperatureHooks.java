package com.bettercontent.heatsync.compat.fiahi;

import com.hexagram2021.fiahi.common.config.FIAHICommonConfig;
import com.hexagram2021.fiahi.common.item.capability.IFrozenRottenFood;
import com.hexagram2021.fiahi.common.util.RegistryHelper;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import net.minecraft.world.item.Item;

public final class FiahiTemperatureHooks {
    private static final double NEVER_STATE_LIMIT = 49.99;

    private FiahiTemperatureHooks() {
    }

    public static void applyAmbient(
            final IFrozenRottenFood food,
            final double targetTemperature,
            final Item item
    ) {
        final double current = food.getTemperature();
        final double next = FiahiHeatMath.balanceAmbientTemperature(
                current,
                targetTemperature,
                ConfigSettings.TEMP_RATE.get(),
                FIAHICommonConfig.TEMPERATURE_BALANCE_RATE.get() / 100.0,
                FIAHICommonConfig.FROZEN_SPEED_MULTIPLIER.get(),
                FIAHICommonConfig.ROTTEN_SPEED_MULTIPLIER.get()
        );
        apply(food, current, next, item);
    }

    public static void applyDirect(
            final IFrozenRottenFood food,
            final double targetTemperature,
            final Item item,
            final int thermalMass
    ) {
        final double current = food.getTemperature();
        final double next = FiahiHeatMath.balanceDirectTemperature(
                current,
                targetTemperature,
                FIAHICommonConfig.FROZEN_SPEED_MULTIPLIER.get(),
                FIAHICommonConfig.ROTTEN_SPEED_MULTIPLIER.get(),
                thermalMass
        );
        apply(food, current, next, item);
    }

    public static void applyDirectSupplement(
            final IFrozenRottenFood food,
            final double targetTemperature,
            final Item item,
            final double degreesPerUpdate
    ) {
        final double current = food.getTemperature();
        final double boundedTarget = current + Math.copySign(
                Math.min(Math.abs(targetTemperature - current), degreesPerUpdate),
                targetTemperature - current
        );
        apply(food, current, boundedTarget, item);
    }

    private static void apply(
            final IFrozenRottenFood food,
            final double current,
            final double requested,
            final Item item
    ) {
        double next = requested;
        if (item != null) {
            final String itemId = RegistryHelper.getRegistryName(item).toString();
            if (next > current && next > 0.0 && FIAHICommonConfig.NEVER_ROTTEN_FOODS.get().contains(itemId)) {
                next = Math.min(next, NEVER_STATE_LIMIT);
            }
            if (next < current && next < 0.0 && FIAHICommonConfig.NEVER_FROZEN_FOODS.get().contains(itemId)) {
                next = Math.max(next, -NEVER_STATE_LIMIT);
            }
        }
        food.setTemperature(next);
        food.updateFoodTag();
    }
}
