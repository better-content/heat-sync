package com.bettercontent.heatsync.mixin.minecraft;

import com.bettercontent.heatsync.food.FoodThermalService;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
abstract class InventoryMixin {
    @Accessor("player")
    abstract Player heatSync$getPlayer();

    @Inject(method = "setChanged", at = @At("TAIL"))
    private void heatSync$reconcileFoodAfterChange(CallbackInfo ci) {
        FoodThermalService.onPlayerInventoryChanged(heatSync$getPlayer());
    }
}
