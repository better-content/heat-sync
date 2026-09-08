package com.bettercontent.heatsync.food

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.ForgeRegistries

/** Correlates an observed frozen-food rejection with later use of that ordinary item after correction. */
object FoodThreadEpisodes {
    private const val ROOT = "HeatSyncThreadFoodEpisode"
    private const val TOKEN = "token"
    private const val ITEM = "item"
    private const val THREAD_ID = "food_carries_weather"

    fun onFrozenUseRejected(player: ServerPlayer, stack: ItemStack) {
        if (!isNonNeutral(stack)) return
        val itemId = itemId(stack) ?: return
        val persisted = player.persistentData.getCompound(Player.PERSISTED_NBT_TAG)
        val prior = persisted.getCompound(ROOT)
        val active = ThreadSignalsReflection.activeCorrelation(player, THREAD_ID)
        val token = active
            ?: prior.getString(TOKEN).takeIf(::validToken)
            ?: "${player.uuid}:food:${player.server.tickCount}"
        val episodeItem = prior.getString(ITEM).takeIf { active != null && it.isNotBlank() } ?: itemId

        persisted.put(ROOT, CompoundTag().also {
            it.putString(TOKEN, token)
            it.putString(ITEM, episodeItem)
        })
        player.persistentData.put(Player.PERSISTED_NBT_TAG, persisted)
        if (active == null) {
            ThreadSignalsReflection.emit(player, "food_thermal_state", "non_neutral", token)
        }
    }

    fun onFoodUseFinished(player: ServerPlayer, stack: ItemStack) {
        if (!isAppropriate(stack)) return
        val persisted = player.persistentData.getCompound(Player.PERSISTED_NBT_TAG)
        val episode = persisted.getCompound(ROOT)
        if (!sameOrdinaryItem(episode.getString(ITEM), stack)) return
        val active = ThreadSignalsReflection.activeCorrelation(player, THREAD_ID) ?: return
        if (!validToken(active)) return
        if (ThreadSignalsReflection.emit(player, "food_thermal_use", "appropriate", active)) {
            persisted.remove(ROOT)
            player.persistentData.put(Player.PERSISTED_NBT_TAG, persisted)
        }
    }

    internal fun isNonNeutral(stack: ItemStack): Boolean = stack.isEdible && FoodThermalService.isFrozen(stack)

    internal fun isAppropriate(stack: ItemStack): Boolean =
        stack.isEdible && !FoodThermalService.isFrozen(stack) && FoodThermalService.stage(stack) == FoodThermalService.Stage.FRESH

    internal fun sameOrdinaryItem(expectedId: String, stack: ItemStack): Boolean = itemId(stack) == expectedId

    private fun itemId(stack: ItemStack): String? = ForgeRegistries.ITEMS.getKey(stack.item)?.toString()

    private fun validToken(value: String): Boolean =
        value.isNotBlank() && value.length <= 128 && value.all { it.code in 0x21..0x7e }
}

private object ThreadSignalsReflection {
    private const val API = "com.bettercontent.threads.api.ThreadSignals"

    fun emit(player: ServerPlayer, type: String, value: String, token: String): Boolean = try {
        Class.forName(API)
            .getMethod(
                "emit",
                ServerPlayer::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
            )
            .invoke(null, player, type, value, token)
        true
    } catch (_: ReflectiveOperationException) {
        false
    }

    fun activeCorrelation(player: ServerPlayer, threadId: String): String? = try {
        Class.forName(API)
            .getMethod("activeCorrelation", ServerPlayer::class.java, String::class.java)
            .invoke(null, player, threadId) as? String
    } catch (_: ReflectiveOperationException) {
        null
    }
}
