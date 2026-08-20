package com.bettercontent.heatsync.compat.fiahi

import net.minecraft.ChatFormatting
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.registries.ForgeRegistries

object FiahiTooltipBridge {
    private val foodPouchId = ResourceLocation.fromNamespaceAndPath("fiahi", "food_pouch")

    @JvmStatic
    fun onTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        val tag = stack.tag ?: return
        val temperature = when {
            ForgeRegistries.ITEMS.getKey(stack.item) == foodPouchId &&
                tag.contains(POUCH_TEMPERATURE_KEY, Tag.TAG_ANY_NUMERIC.toInt()) ->
                tag.getDouble(POUCH_TEMPERATURE_KEY)

            tag.contains(FOOD_TEMPERATURE_KEY, Tag.TAG_ANY_NUMERIC.toInt()) ->
                tag.getDouble(FOOD_TEMPERATURE_KEY)

            else -> return
        }
        if (temperature >= FROZEN_GUIDANCE_THRESHOLD) return
        event.toolTip.add(Component.translatable("tooltip.heat_sync.fiahi_thawing").withStyle(ChatFormatting.AQUA))
        event.toolTip.add(Component.translatable("tooltip.heat_sync.fiahi_spoiling").withStyle(ChatFormatting.DARK_GRAY))
    }

    private const val FOOD_TEMPERATURE_KEY = "fiahi:temperature"
    private const val POUCH_TEMPERATURE_KEY = "temperature"
    private const val FROZEN_GUIDANCE_THRESHOLD = -25.0
}
