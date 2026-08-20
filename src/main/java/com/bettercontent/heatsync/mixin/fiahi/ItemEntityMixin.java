package com.bettercontent.heatsync.mixin.fiahi;

import com.bettercontent.heatsync.compat.fiahi.FiahiAmbientContext;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemEntity.class, priority = 900)
public abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void heatSync$beginAmbientFoodTick(final CallbackInfo callback) {
        final ItemEntity entity = (ItemEntity) (Object) this;
        final double ambient = WorldHelper.getTemperatureAt(entity.level(), entity.getOnPos());
        FiahiAmbientContext.pushMinecraftTemperature(ambient);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void heatSync$endAmbientFoodTick(final CallbackInfo callback) {
        FiahiAmbientContext.pop();
    }
}
