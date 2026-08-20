package com.bettercontent.heatsync.mixin.fiahi;

import com.bettercontent.heatsync.compat.fiahi.FiahiApplianceHooks;
import com.momosoftworks.coldsweat.common.blockentity.IceboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IceboxBlockEntity.class, priority = 900, remap = false)
public abstract class IceboxBlockEntityMixin {
    @Inject(
            method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("RETURN")
    )
    private static <T extends BlockEntity> void heatSync$applyDirectFoodStep(
            final Level level,
            final BlockPos pos,
            final BlockState state,
            final T rawBlockEntity,
            final CallbackInfo callback
    ) {
        final IceboxBlockEntity icebox = (IceboxBlockEntity) rawBlockEntity;
        if (level.isClientSide || icebox.getFuel() <= 0 || level.getGameTime() % 20 != 0) return;
        final int result = FiahiApplianceHooks.applyDirectStep(icebox, IceboxBlockEntity.WATERSKIN_SLOTS, -25.0);
        if ((result & FiahiApplianceHooks.POUCH_CHANGED) != 0
                && (result & FiahiApplianceHooks.LOOSE_FOOD_PRESENT) == 0) {
            icebox.setFuel(Math.max(0, icebox.getFuel() - 1));
        }
    }
}
