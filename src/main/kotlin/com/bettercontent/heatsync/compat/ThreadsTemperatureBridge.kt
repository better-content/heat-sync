package com.bettercontent.heatsync.compat

import com.momosoftworks.coldsweat.api.util.Temperature
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

/** Uses Cold Sweat's authoritative body-temperature trait, never elapsed-time inference. */
object ThreadsTemperatureBridge {
    private const val ROOT = "HeatSyncThreadTemperatureEpisode"
    private const val STRESS = 75.0
    private const val COMFORT = 25.0

    @SubscribeEvent
    fun onTick(event: TickEvent.PlayerTickEvent) {
        val player = event.player as? ServerPlayer ?: return
        if (event.phase != TickEvent.Phase.END || player.tickCount % 20 != 0) return
        val body = Temperature.get(player, Temperature.Trait.BODY)
        val persisted = player.persistentData.getCompound(Player.PERSISTED_NBT_TAG)
        var token = persisted.getString(ROOT)
        if (kotlin.math.abs(body) >= STRESS) {
            if (token.isBlank()) token = "${player.uuid}:temperature:${player.server.tickCount}"
            persisted.putString(ROOT, token);player.persistentData.put(Player.PERSISTED_NBT_TAG, persisted)
            emit(player, "temperature_stress", if (body < 0) "cold" else "hot", token)
        } else if (kotlin.math.abs(body) <= COMFORT && token.isNotBlank()) {
            emit(player, "temperature_comfort", "restored", token)
            persisted.remove(ROOT);player.persistentData.put(Player.PERSISTED_NBT_TAG, persisted)
        }
    }

    @SubscribeEvent fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) { /* persisted episode intentionally survives */ }
    private fun emit(player: ServerPlayer,type: String,value: String,token: String) { try { Class.forName("com.bettercontent.threads.api.ThreadSignals").getMethod("emit",ServerPlayer::class.java,String::class.java,String::class.java,String::class.java).invoke(null,player,type,value,token) } catch (_: ReflectiveOperationException) {} }
}
