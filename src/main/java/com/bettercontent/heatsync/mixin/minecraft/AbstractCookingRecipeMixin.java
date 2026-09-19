package com.bettercontent.heatsync.mixin.minecraft;

import com.bettercontent.heatsync.food.FoodThermalService;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps furnace, smoker, campfire, and blasting recipe results from rejuvenating food. */
@Mixin(AbstractCookingRecipe.class)
abstract class AbstractCookingRecipeMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/Container;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void heatSync$carryFoodState(
            Container container,
            RegistryAccess registries,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        FoodThermalService.carryCookingState(container.getItem(0), cir.getReturnValue());
    }
}
