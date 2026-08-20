package com.bettercontent.heatsync.mixin.fiahi;

import com.bettercontent.heatsync.compat.fiahi.FiahiHeatMath;
import com.hexagram2021.fiahi.common.item.capability.IFrozenRottenFood;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.hexagram2021.fiahi.common.ForgeEventHandler", priority = 900, remap = false)
public abstract class ForgeEventHandlerMixin {
    @Redirect(
            method = "lambda$tickContainer$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hexagram2021/fiahi/common/item/capability/IFrozenRottenFood;foodTick(DLnet/minecraft/world/item/Item;)V"
            ),
            require = 1
    )
    private static void heatSync$useAbsoluteContainerAmbient(
            final IFrozenRottenFood food,
            final double encodedTarget,
            final Item item
    ) {
        final double ambientMinecraftUnits = (encodedTarget - food.getTemperature()) / 2.0;
        food.foodTick(FiahiHeatMath.ambientMinecraftToCelsius(ambientMinecraftUnits), item);
    }
}
