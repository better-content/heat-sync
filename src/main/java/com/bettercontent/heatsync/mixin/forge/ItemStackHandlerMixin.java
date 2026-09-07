package com.bettercontent.heatsync.mixin.forge;

import com.bettercontent.heatsync.food.FoodStackMergeService;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemStackHandler.class, remap = false)
abstract class ItemStackHandlerMixin {
    @Redirect(method = "insertItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;grow(I)V", remap = true))
    private void heatSync$averageCommittedInsert(ItemStack destination, int movedCount, int slot, ItemStack source, boolean simulate) {
        FoodStackMergeService.mergeInto(destination, destination.copy(), source.copy(), movedCount);
        destination.grow(movedCount);
    }
}
