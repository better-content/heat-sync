package com.bettercontent.heatsync.food

import com.bettercontent.heatsync.HeatSyncMod
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.food.FoodProperties
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

object FoodEffects {
    val EFFECTS: DeferredRegister<MobEffect> = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, HeatSyncMod.MOD_ID)
    val THIRST: RegistryObject<MobEffect> = EFFECTS.register("thirst") { SystemDrainEffect(0x5AA9E6, false) }
    val MALNOURISHMENT: RegistryObject<MobEffect> = EFFECTS.register("malnourishment") { SystemDrainEffect(0x9B875D, true) }
}

object FoodItems {
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(ForgeRegistries.ITEMS, HeatSyncMod.MOD_ID)
    val SPOILED_MEAT: RegistryObject<Item> = ITEMS.register("spoiled_meat") { Item(Item.Properties().food(FoodProperties.Builder().nutrition(1).saturationMod(0.1f).meat().build())) }
    val SPOILED_PRODUCE: RegistryObject<Item> = ITEMS.register("spoiled_produce") { Item(Item.Properties().food(FoodProperties.Builder().nutrition(1).saturationMod(0.1f).build())) }
}

private class SystemDrainEffect(color: Int, private val diet: Boolean) : MobEffect(MobEffectCategory.HARMFUL, color) {
    override fun isDurationEffectTick(duration: Int, amplifier: Int): Boolean = duration % 20 == 0
    override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
        val player = entity as? Player ?: return
        if (diet) DietBridge.drain(player, amplifier) else ThirstBridge.drain(player, amplifier)
    }
}

private object ThirstBridge {
    fun drain(player: Player, amplifier: Int) {
        runCatching {
            val caps = Class.forName("dev.ghen.thirst.foundation.common.capability.ModCapabilities")
            val capability = caps.getField("PLAYER_THIRST").get(null)
            val lazy = player.javaClass.getMethod("getCapability", Class.forName("net.minecraftforge.common.capabilities.Capability")).invoke(player, capability)
            lazy.javaClass.getMethod("ifPresent", java.util.function.Consumer::class.java).invoke(lazy, java.util.function.Consumer<Any> { thirst ->
                val now = thirst.javaClass.getMethod("getThirst").invoke(thirst) as Int
                val quenched = thirst.javaClass.getMethod("getQuenched").invoke(thirst) as Int
                val loss = (amplifier + 1) / 60.0
                val next = (now - loss).toInt().coerceAtLeast(0)
                thirst.javaClass.getMethod("setThirst", Int::class.javaPrimitiveType).invoke(thirst, next)
                thirst.javaClass.getMethod("setQuenched", Int::class.javaPrimitiveType).invoke(thirst, quenched.coerceAtMost(next))
                thirst.javaClass.getMethod("updateThirstData", Player::class.java).invoke(thirst, player)
            })
        }
    }
}

private object DietBridge {
    fun drain(player: Player, amplifier: Int) {
        runCatching {
            val services = Class.forName("com.illusivesoulworks.diet.platform.Services")
            val capabilityService = services.getField("CAPABILITY").get(null)
            val optional = capabilityService.javaClass.getMethod("get", Player::class.java).invoke(capabilityService, player) as java.util.Optional<*>
            optional.ifPresent { tracker ->
                val total = doubleArrayOf(0.02, 0.06, 0.10)[amplifier.coerceIn(0, 2)] / 60.0
                val values = tracker.javaClass.getMethod("getValues").invoke(tracker) as Map<*, *>
                values.forEach { (key, value) -> tracker.javaClass.getMethod("setValue", String::class.java, Float::class.javaPrimitiveType).invoke(tracker, key as String, ((value as Float) - total.toFloat()).coerceAtLeast(0f)) }
                tracker.javaClass.getMethod("sync").invoke(tracker)
            }
        }
    }
}
