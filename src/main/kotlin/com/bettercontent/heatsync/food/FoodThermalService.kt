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
import kotlin.math.exp
import kotlin.math.pow

/** Replaces FIAHI's coupled temperature/rot scalar with independent physical temperature and decay. */
object FoodThermalService {
    private const val KEY = "heat_sync_food"
    private const val TEMPERATURE = "temperature_k"
    private const val DECAY = "decay"
    private const val LAST_TIME = "last_time"
    private const val LAST_TARGET = "last_target_k"
    private const val CHANGED = "thermally_changed"
    private const val VERSION = "version"
    private const val TICK_INTERVAL = 120L
    private const val WORLD_TAU_TICKS = 1000.0 // 95% in five minutes
    private val trackedInventoryPositionsByLevel = mutableMapOf<ResourceKey<Level>, LongOpenHashSet>()

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
        if (existing.contains(VERSION)) return existing
        existing.putInt(VERSION, 1)
        existing.putDouble(TEMPERATURE, targetK)
        existing.putDouble(DECAY, 0.0)
        existing.putLong(LAST_TIME, gameTime)
        existing.putDouble(LAST_TARGET, targetK)
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

    fun temperatureK(stack: ItemStack): Double = stack.tag?.getCompound(KEY)?.getDouble(TEMPERATURE) ?: 295.15

    fun isFrozen(stack: ItemStack): Boolean =
        profile(stack).freezingC?.let { temperatureK(stack) - 273.15 <= it } == true

    /** Item-model tint: cold is ice-white; spoilage deepens from faded brown to near-black. */
    fun itemTint(stack: ItemStack): Int {
        if (!stack.isEdible) return 0xFFFFFF
        val frozen = isFrozen(stack)
        if (frozen) return 0xEAF8FF
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
        val old = tag.getDouble(TEMPERATURE)
        val oldTarget = tag.getDouble(LAST_TARGET)
        val tau = if (appliance) 200.0 else WORLD_TAU_TICKS
        val next = oldTarget + (old - oldTarget) * exp(-elapsed / tau)
        tag.putDouble(TEMPERATURE, next)
        if (kotlin.math.abs(next - old) >= 5.0) tag.putBoolean(CHANGED, true)
        profile.days?.let { days ->
            val celsius = next - 273.15
            val freezing = profile.freezingC ?: Double.NEGATIVE_INFINITY
            if (celsius > freezing) {
                val rate = 2.0.pow((celsius - 22.0) / 10.0)
                val added = elapsed / (days * 24000.0) * rate
                tag.putDouble(DECAY, (tag.getDouble(DECAY) + added).coerceAtMost(1.0))
            }
        }
        tag.putLong(LAST_TIME, gameTime)
        tag.putDouble(LAST_TARGET, targetK)
        if (stage(stack) == Stage.CONVERTED) return ItemStack(if (profile.meat) FoodItems.SPOILED_MEAT.get() else FoodItems.SPOILED_PRODUCE.get(), stack.count)
        return stack
    }

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
        val temperatures = Direction.values().mapNotNull { direction ->
            val source = level.getBlockEntity(pos.relative(direction)) ?: return@mapNotNull null
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
        Direction.values().mapNotNull { direction ->
            when (level.getBlockState(pos.relative(direction)).block) {
                Blocks.SNOW_BLOCK -> 268.15
                Blocks.ICE -> 273.15
                Blocks.PACKED_ICE -> 263.15
                Blocks.BLUE_ICE -> 253.15
                else -> null
            }
        }.takeIf { it.isNotEmpty() }?.average()

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
        val iterator = tracked.iterator()
        while (iterator.hasNext()) {
            val packedPos = iterator.nextLong()
            if (SectionPos.blockToSectionCoord(BlockPos.getX(packedPos)) == chunkPos.x &&
                SectionPos.blockToSectionCoord(BlockPos.getZ(packedPos)) == chunkPos.z) {
                iterator.remove()
            }
        }
    }

    @SubscribeEvent
    fun onBlockPlaced(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        level.getBlockEntity(event.pos)?.let { trackInventory(level, it) }
    }

    @SubscribeEvent
    fun onBlockBroken(event: BlockEvent.BreakEvent) {
        val level = event.level as? ServerLevel ?: return
        trackedInventoryPositionsByLevel[level.dimension()]?.remove(event.pos.asLong())
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
        val iterator = tracked.iterator()
        while (iterator.hasNext()) {
            val pos = BlockPos.of(iterator.nextLong())
            if (!level.isLoaded(pos)) {
                iterator.remove()
                continue
            }
            val blockEntity = level.getBlockEntity(pos)
            if (blockEntity == null || blockEntity.isRemoved || !isInventory(blockEntity)) {
                iterator.remove()
                continue
            }
            tickBlockInventory(level, blockEntity, gameTime)
        }
    }

    /** Public for GameTests and for inventories placed after their chunk has loaded. */
    fun trackInventory(level: ServerLevel, blockEntity: BlockEntity) {
        if (isInventory(blockEntity)) trackedInventories(level).add(blockEntity.blockPos.asLong())
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
        trackedInventoryPositionsByLevel.getOrPut(level.dimension(), ::LongOpenHashSet)

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
        event.toolTip.add(Component.literal("${"%.1f".format(c)} °C — ${stage(stack).name.lowercase()}"))
        if (frozen) event.toolTip.add(Component.translatable("tooltip.heat_sync.food_frozen"))
        if (p.days == null) event.toolTip.add(Component.translatable("tooltip.heat_sync.food_shelf_stable"))
    }

    private fun Boolean?.orFalse() = this == true
}
