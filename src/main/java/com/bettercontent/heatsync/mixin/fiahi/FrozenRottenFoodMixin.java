package com.bettercontent.heatsync.mixin.fiahi;

import com.bettercontent.heatsync.compat.fiahi.FiahiAmbientContext;
import com.bettercontent.heatsync.compat.fiahi.FiahiTemperatureHooks;
import com.hexagram2021.fiahi.common.item.capability.IFrozenRottenFood;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.hexagram2021.fiahi.common.item.capability.impl.FrozenRottenFood", remap = false)
public abstract class FrozenRottenFoodMixin {
    @Inject(method = "foodTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void heatSync$applyReversibleTemperature(
            final double targetTemperature,
            final Item item,
            final CallbackInfo callback
    ) {
        FiahiTemperatureHooks.applyAmbient(
                (IFrozenRottenFood) (Object) this,
                FiahiAmbientContext.targetOr(targetTemperature),
                item
        );
        callback.cancel();
    }
}
