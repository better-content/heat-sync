package com.bettercontent.heatsync.mixin.fiahi;

import com.bettercontent.heatsync.compat.fiahi.FiahiAmbientContext;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Inventory.class, priority = 900)
public abstract class InventoryMixin {
    @Shadow @Final public Player player;

    @Inject(method = "tick", at = @At("HEAD"))
    private void heatSync$beginAmbientFoodTick(final CallbackInfo callback) {
        final double ambient = WorldHelper.getTemperatureAt(this.player.level(), this.player.blockPosition());
        FiahiAmbientContext.pushMinecraftTemperature(ambient);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void heatSync$endAmbientFoodTick(final CallbackInfo callback) {
        FiahiAmbientContext.pop();
    }
}
