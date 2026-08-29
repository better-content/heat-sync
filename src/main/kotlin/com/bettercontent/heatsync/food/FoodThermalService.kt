package com.bettercontent.heatsync.food

import com.bettercontent.heatsync.HeatSyncMod
import com.bettercontent.heatsync.ColdSweatAmbientSampler
import com.bettercontent.heatsync.api.ThermalCapabilities
import com.bettercontent.heatsync.api.HeatBlockEntity
import com.bettercontent.heatsync.api.HeatStorageThermalBody
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import com.momosoftworks.coldsweat.api.util.Temperature
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.event.level.ChunkEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.items.IItemHandlerModifiable
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.pow

/** Replaces FIAHI's coupled temperature/rot scalar with independent physical temperature and decay. */
object FoodThermalService {
    private const val KEY = "heat_sync_food"
    private const val TEMPERATURE_BUCKET = "temperature_bucket_c"
    private const val DECAY = "decay"
    private const val LAST_TIME = "last_time"
    private const val VERSION = "version"
    private const val TICK_INTERVAL = 120L
    private const val WORLD_TAU_TICKS = 4000.0 // 95% in twenty minutes
    private const val TEMPERATURE_BUCKET_C = 5.0
    private const val DECAY_STEPS = 140.0
    private const val REFRIGERATION_C = 5.0
    private val trackedInventoryPositionsByLevel = ConcurrentHashMap<ResourceKey<Level>, LongOpenHashSet>()

    private data class ContainerTarget(val temperatureK: Double, val appliance: Boolean)

    enum class Stage { FRESH, STALE, SPOILED, ROTTEN, CONVERTED }
    data class Profile(val id: String, val days: Double?, val freezingC: Double?, val meat: Boolean)

    fun profile(stack: ItemStack): Profile {
        val id = stack.item.descriptionId.lowercase()
        val meat = stack.item.foodProperties?.isMeat == true
        return when {
            id.contains("vodka") || id.contains("rum") -> Profile("distilled_alcohol", null, -25.0, false)
            id.contains("beer") || id.contains("wine") || id.contains("mead") -> Profile("fermented_alcohol", null, -5.0, false)
            id.contains("grog") || id.contains("nog") || id.contains("cocktail") -> Profile("alcoholic_cocktail", 28.0, -5.0, false)
            id.contains("dried") -> Profile("dried", null, null, meat)
            id.contains("canned") || id.contains("golden_") -> Profile("shelf_stable", null, 0.0, meat)
            id.contains("jerky") || id.contains("pickle") || id.contains("kimchi") || id.contains("jam") || id.contains("marmalade") || id.contains("smoked") || id.contains("cheese") -> Profile("preserved", 28.0, 0.0, meat)
            meat || id.contains("raw_") -> Profile("raw_animal", 3.0, 0.0, meat)
            id.contains("apple") || id.contains("berry") || id.contains("carrot") || id.contains("potato") || id.contains("melon") || id.contains("vegetable") -> Profile("fresh_produce", 5.0, 0.0, false)
            else -> Profile("prepared", 7.0, 0.0, meat)
        }
    }

    fun state(stack: ItemStack, targetK: Double, gameTime: Long): CompoundTag {
        val root = stack.orCreateTag
        val existing = root.getCompound(KEY)
        if (existing.getInt(VERSION) >= 2) return existing
        val migratedTemperature = if (existing.contains("temperature_k")) existing.getDouble("temperature_k") else targetK
        val migratedDecay = existing.getDouble(DECAY)
        existing.putInt(VERSION, 2)
        existing.putInt(TEMPERATURE_BUCKET, bucketForKelvin(migratedTemperature))
        existing.putDouble(DECAY, quantizeDecay(migratedDecay))
        existing.putLong(LAST_TIME, gameTime)
        existing.remove("temperature_k")
        existing.remove("last_target_k")
        existing.remove("thermally_changed")
        root.put(KEY, existing)
        return existing
    }

    fun stage(stack: ItemStack): Stage {
        val value = stack.tag?.getCompound(KEY)?.getDouble(DECAY) ?: 0.0
        return when {
            value >= 1.0 -> Stage.CONVERTED
            value >= 5.0 / 7.0 -> Stage.ROTTEN
            value >= 3.0 / 7.0 -> Stage.SPOILED
            value >= 1.0 / 7.0 -> Stage.STALE
            else -> Stage.FRESH
        }
    }

    fun temperatureK(stack: ItemStack): Double = stack.tag?.getCompound(KEY)
        ?.takeIf { it.getInt(VERSION) >= 2 }
        ?.getInt(TEMPERATURE_BUCKET)
        ?.let(::kelvinForBucket)
        ?: 295.15

    fun isFrozen(stack: ItemStack): Boolean =
        profile(stack).freezingC?.let { temperatureK(stack) - 273.15 <= it } == true

    /** Item-model tint: frozen food is visibly ice-blue; spoilage deepens from faded brown to near-black. */
    fun itemTint(stack: ItemStack): Int {
        if (!stack.isEdible) return 0xFFFFFF
        val frozen = isFrozen(stack)
        if (frozen) return 0x9DDCFF
        return when (stage(stack)) {
            Stage.FRESH -> 0xFFFFFF
            Stage.STALE -> 0xD9C9AA
            Stage.SPOILED -> 0x916D43
            Stage.ROTTEN, Stage.CONVERTED -> 0x392416
        }
    }

    fun tick(stack: ItemStack, targetK: Double, gameTime: Long, appliance: Boolean = false): ItemStack {
        if (!stack.isEdible || stack.item == FoodItems.SPOILED_MEAT.get() || stack.item == FoodItems.SPOILED_PRODUCE.get()) return stack
        val profile = profile(stack)
        val tag = state(stack, targetK, gameTime)
        val elapsed = (gameTime - tag.getLong(LAST_TIME)).coerceAtLeast(0L)
        if (elapsed == 0L) return stack
        val old = kelvinForBucket(tag.getInt(TEMPERATURE_BUCKET))
        val tau = if (appliance) 200.0 else WORLD_TAU_TICKS
        val next = targetK + (old - targetK) * exp(-elapsed / tau)
        tag.putInt(TEMPERATURE_BUCKET, bucketForKelvin(next))
        profile.days?.let { days ->
            if (next - 273.15 > REFRIGERATION_C) {
                val added = elapsed / (days * 24000.0)
                tag.putDouble(DECAY, quantizeDecay(tag.getDouble(DECAY) + added))
            }
        }
        tag.putLong(LAST_TIME, gameTime)
        if (stage(stack) >= Stage.ROTTEN) return ItemStack(if (profile.meat) FoodItems.SPOILED_MEAT.get() else FoodItems.SPOILED_PRODUCE.get(), stack.count)
        return stack
    }

    private fun bucketForKelvin(kelvin: Double): Int {
        val bucket = (kelvin - 273.15) / TEMPERATURE_BUCKET_C
        val lower = kotlin.math.floor(bucket).toInt()
        return if (ThreadLocalRandom.current().nextDouble() < bucket - lower) lower + 1 else lower
    }

    private fun kelvinForBucket(bucket: Int): Double = bucket * TEMPERATURE_BUCKET_C + 273.15

    private fun quantizeDecay(value: Double): Double =
        (kotlin.math.round(value.coerceIn(0.0, 1.0) * DECAY_STEPS) / DECAY_STEPS).coerceAtMost(1.0)

    /** Updates a real block inventory from local Cold Sweat temperature and adjacent thermal blocks. */
    fun tickContainer(level: Level, pos: BlockPos, container: Container, gameTime: Long) {
        val target = containerTarget(level, pos)
        for (slot in 0 until container.containerSize) {
            val stack = container.getItem(slot)
            if (stack.isEdible) container.setItem(slot, tick(stack, target.temperatureK, gameTime, target.appliance))
        }
    }

    /**
     * A powered Heat Sync body is an appliance setpoint.  Ice is a passive local cold source;
     * everything else follows Cold Sweat's actual world temperature at this block position.
     */
    private fun containerTarget(level: Level, pos: BlockPos): ContainerTarget =
        adjacentThermalTarget(level, pos)?.let { ContainerTarget(it, appliance = true) }
            ?: adjacentPassiveColdTarget(level, pos)?.let { ContainerTarget(it, appliance = false) }
            ?: ContainerTarget(ambient(level, pos), appliance = false)

    fun adjacentThermalTarget(level: Level, pos: BlockPos): Double? {
        val temperatures = loadedAdjacentPositions(pos, level::isLoaded).mapNotNull { (direction, sourcePos) ->
            val source = level.getBlockEntity(sourcePos) ?: return@mapNotNull null
            source.getCapability(ThermalCapabilities.BODY, direction.opposite)
                .resolve()
                .orElseGet {
                    (source as? HeatBlockEntity)?.let(::HeatStorageThermalBody)
                }
                ?.temperatureKelvin()
        }
        return temperatures.takeIf { it.isNotEmpty() }?.average()
    }

    private fun adjacentPassiveColdTarget(level: Level, pos: BlockPos): Double? =
        loadedAdjacentPositions(pos, level::isLoaded).mapNotNull { (_, sourcePos) ->
            when (level.getBlockState(sourcePos).block) {
                Blocks.SNOW_BLOCK -> 268.15
                Blocks.ICE -> 273.15
                Blocks.PACKED_ICE -> 263.15
                Blocks.BLUE_ICE -> 253.15
                else -> null
            }
        }.takeIf { it.isNotEmpty() }?.average()

    /** Neighbor enumeration is loaded-only so thermal inventory ticks can never request chunk generation. */
    internal fun loadedAdjacentPositions(
        pos: BlockPos,
        isLoaded: (BlockPos) -> Boolean,
    ): List<Pair<Direction, BlockPos>> = Direction.values().mapNotNull { direction ->
        val neighbor = pos.relative(direction)
        if (isLoaded(neighbor)) direction to neighbor else null
    }

    private fun ambient(player: Player): Double {
        val mc = runCatching { Temperature.get(player, Temperature.Trait.WORLD) }.getOrDefault(0.88)
        return mc * 25.0 + 273.15
    }

    private fun ambient(level: Level, pos: BlockPos): Double {
        if (!ModList.get().isLoaded(HeatSyncMod.COLD_SWEAT_MOD_ID)) return 295.15
        val mc = runCatching { ColdSweatAmbientSampler.sampleWorldTemp(level, pos) }.getOrDefault(0.88)
        return mc * 25.0 + 273.15
    }

    @SubscribeEvent
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        val player = event.player
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide || player.tickCount.toLong() % TICK_INTERVAL != 0L) return
        val target = ambient(player)
        listOf(player.inventory.items, player.inventory.armor, player.inventory.offhand).forEach { inventory ->
            inventory.indices.forEach { slot ->
                val stack = inventory[slot]
                if (stack.isEdible) inventory[slot] = tick(stack, target, player.level().gameTime)
            }
        }
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val now = player.level().gameTime
        listOf(player.inventory.items, player.inventory.armor, player.inventory.offhand).forEach { inventory ->
            inventory.forEach { stack ->
                stack.tag?.getCompound(KEY)?.takeIf { it.getInt(VERSION) >= 2 }?.putLong(LAST_TIME, now)
            }
        }
    }

    @SubscribeEvent
    fun onLevelLoad(event: LevelEvent.Load) {
        (event.level as? ServerLevel)?.let(::trackedInventories)
    }

    @SubscribeEvent
    fun onLevelUnload(event: LevelEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        trackedInventoryPositionsByLevel.remove(level.dimension())
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkEvent.Load) {
        val level = event.level as? ServerLevel ?: return
        val chunk = event.chunk as? LevelChunk ?: return
        chunk.blockEntities.values.forEach { trackInventory(level, it) }
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkEvent.Unload) {
        val level = event.level as? ServerLevel ?: return
        val tracked = trackedInventoryPositionsByLevel[level.dimension()] ?: return
        val chunkPos = event.chunk.pos
        val removed = synchronized(tracked) { tracked.toLongArray() }.filter { packedPos ->
            if (SectionPos.blockToSectionCoord(BlockPos.getX(packedPos)) == chunkPos.x &&
                SectionPos.blockToSectionCoord(BlockPos.getZ(packedPos)) == chunkPos.z) {
                true
            } else {
                false
            }
        }
        synchronized(tracked) { removed.forEach(tracked::remove) }
    }

    @SubscribeEvent
    fun onBlockPlaced(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        level.getBlockEntity(event.pos)?.let { trackInventory(level, it) }
    }

    @SubscribeEvent
    fun onBlockBroken(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        trackedInventoryPositionsByLevel[level.dimension()]?.let { tracked ->
            synchronized(tracked) { tracked.remove(event.pos.asLong()) }
        }
    }

    @SubscribeEvent
    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val level = event.level as? ServerLevel ?: return
        if (level.gameTime % TICK_INTERVAL != 0L) return
        tickTrackedInventories(level, level.gameTime)
    }

    /** Public for GameTests; production calls this from the server level tick. */
    fun tickTrackedInventories(level: ServerLevel, gameTime: Long) {
        val tracked = trackedInventoryPositionsByLevel[level.dimension()] ?: return
        val snapshot = synchronized(tracked) { tracked.toLongArray() }
        val removed = mutableListOf<Long>()
        snapshot.forEach { packedPos ->
            val pos = BlockPos.of(packedPos)
            if (!level.isLoaded(pos)) {
                removed += packedPos
                return@forEach
            }
            val blockEntity = level.getBlockEntity(pos)
            if (blockEntity == null || blockEntity.isRemoved || !isInventory(blockEntity)) {
                removed += packedPos
                return@forEach
            }
            tickBlockInventory(level, blockEntity, gameTime)
        }
        synchronized(tracked) { removed.forEach(tracked::remove) }
    }

    /** Public for GameTests and for inventories placed after their chunk has loaded. */
    fun trackInventory(level: ServerLevel, blockEntity: BlockEntity) {
        if (isInventory(blockEntity)) trackedInventories(level).let { tracked ->
            synchronized(tracked) { tracked.add(blockEntity.blockPos.asLong()) }
        }
    }

    private fun tickBlockInventory(level: Level, blockEntity: BlockEntity, gameTime: Long) {
        if (blockEntity is Container) {
            tickContainer(level, blockEntity.blockPos, blockEntity, gameTime)
            return
        }
        val target = containerTarget(level, blockEntity.blockPos)
        blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent { handler ->
            val writable = handler as? IItemHandlerModifiable ?: return@ifPresent
            for (slot in 0 until writable.slots) {
                val stack = writable.getStackInSlot(slot)
                if (stack.isEdible) writable.setStackInSlot(slot, tick(stack, target.temperatureK, gameTime, target.appliance))
            }
        }
    }

    private fun isInventory(blockEntity: BlockEntity): Boolean =
        blockEntity is Container || blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent

    private fun trackedInventories(level: ServerLevel): LongOpenHashSet =
        trackedInventoryPositionsByLevel.computeIfAbsent(level.dimension()) { LongOpenHashSet() }

    @SubscribeEvent
    fun onUseStart(event: LivingEntityUseItemEvent.Start) {
        val stack = event.item
        if (!stack.isEdible) return
        if (isFrozen(stack)) event.isCanceled = true
    }

    @SubscribeEvent
    fun onUseFinish(event: LivingEntityUseItemEvent.Finish) {
        val player = event.entity as? ServerPlayer ?: return
        val current = event.item
        val stage = if (current.item == FoodItems.SPOILED_MEAT.get() || current.item == FoodItems.SPOILED_PRODUCE.get()) Stage.ROTTEN else stage(current)
        val amplifier = when (stage) { Stage.STALE -> 0; Stage.SPOILED -> 1; Stage.ROTTEN, Stage.CONVERTED -> 2; else -> return }
        val duration = debuffDurationTicks(stage)
        player.addEffect(MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER, duration, amplifier))
        player.addEffect(MobEffectInstance(FoodEffects.THIRST.get(), duration, amplifier))
        player.addEffect(MobEffectInstance(FoodEffects.MALNOURISHMENT.get(), duration, amplifier))
        if (amplifier == 2) {
            player.addEffect(MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200, 0))
            player.addEffect(MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 400, 0))
        }
    }

    fun debuffDurationTicks(stage: Stage): Int = if (stage == Stage.STALE) 200 else 1200

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val stack = event.itemStack
        if (!stack.isEdible || !stack.tag?.contains(KEY).orFalse()) return
        val p = profile(stack)
        val c = temperatureK(stack) - 273.15
        val frozen = isFrozen(stack)
        event.toolTip.add(Component.literal("${"%.0f".format(c)} °C — ${stage(stack).name.lowercase()}"))
        if (frozen) event.toolTip.add(Component.translatable("tooltip.heat_sync.food_frozen"))
        if (p.days == null) event.toolTip.add(Component.translatable("tooltip.heat_sync.food_shelf_stable"))
    }

    private fun Boolean?.orFalse() = this == true
}
