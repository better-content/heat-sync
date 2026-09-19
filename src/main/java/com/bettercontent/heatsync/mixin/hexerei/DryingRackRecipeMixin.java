package com.bettercontent.heatsync.mixin.hexerei;

import com.bettercontent.heatsync.food.FoodThermalService;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carries age and temperature from a rack input into its explicitly catalogued output. */
@Mixin(targets = "net.joefoxe.hexerei.data.recipes.DryingRackRecipe", remap = false)
abstract class DryingRackRecipeMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/SimpleContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void heatSync$carryFoodState(SimpleContainer container, RegistryAccess registries,
                                          CallbackInfoReturnable<ItemStack> cir) {
        FoodThermalService.carryDryingState(container.getItem(0), cir.getReturnValue());
    }
}
