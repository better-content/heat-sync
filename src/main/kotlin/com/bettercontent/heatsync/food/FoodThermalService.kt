package com.bettercontent.heatsync.food

import com.bettercontent.heatsync.HeatSyncMod
import com.bettercontent.heatsync.ColdSweatAmbientSampler
import com.bettercontent.heatsync.api.ThermalCapabilities
import com.bettercontent.heatsync.api.HeatBlockEntity
import com.bettercontent.heatsync.api.HeatStorageThermalBody
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import com.momosoftworks.coldsweat.api.util.Temperature
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.server.level.ServerLevel
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.event.level.BlockEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.items.IItemHandlerModifiable
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.exp
import kotlin.math.pow

/** Replaces FIAHI's coupled temperature/rot scalar with independent physical temperature and decay. */
object FoodThermalService {
    private const val KEY = "heat_sync_food"
    private const val TEMPERATURE_BUCKET = "temperature_bucket_c"
    private const val DECAY = "decay"
    private const val LAST_TIME = "last_time"
    private const val LAST_TARGET_BUCKET = "last_target_bucket_c"
    private const val LAST_TARGET_APPLIANCE = "last_target_appliance"
    private const val VERSION = "version"
    private const val ACTIVE = "heat_sync_food_active"
    private const val CURRENT_VERSION = 3
    private const val WORLD_TAU_TICKS = 4000.0 // 95% in twenty minutes
    private const val TEMPERATURE_BUCKET_C = 5.0
    private const val DECAY_STEPS = 140.0
    private const val REFRIGERATION_C = 5.0
    private val inventoryFingerprints = Collections.synchronizedMap(WeakHashMap<BlockEntity, Int>())
    private val playerInventoryFingerprints = Collections.synchronizedMap(WeakHashMap<Player, Int>())
    private val reconciling = ThreadLocal.withInitial { false }

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
        if (existing.getInt(VERSION) == CURRENT_VERSION) return existing
        existing.allKeys.toList().forEach(existing::remove)
        val targetBucket = bucketForKelvin(targetK)
        existing.putInt(VERSION, CURRENT_VERSION)
        existing.putInt(TEMPERATURE_BUCKET, targetBucket)
        existing.putDouble(DECAY, 0.0)
        existing.putLong(LAST_TIME, gameTime)
        existing.putInt(LAST_TARGET_BUCKET, targetBucket)
        existing.putBoolean(LAST_TARGET_APPLIANCE, false)
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
        ?.takeIf { it.getInt(VERSION) == CURRENT_VERSION }
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
        val old = kelvinForBucket(tag.getInt(TEMPERATURE_BUCKET))
        val priorTarget = kelvinForBucket(tag.getInt(LAST_TARGET_BUCKET))
        val tau = if (tag.getBoolean(LAST_TARGET_APPLIANCE)) 200.0 else WORLD_TAU_TICKS
        val next = priorTarget + (old - priorTarget) * exp(-elapsed / tau)
        tag.putInt(TEMPERATURE_BUCKET, bucketForKelvin(next))
        profile.days?.let { days ->
            if (next - 273.15 > REFRIGERATION_C) {
                val added = elapsed / (days * 24000.0)
                tag.putDouble(DECAY, quantizeDecay(tag.getDouble(DECAY) + added))
            }
        }
        tag.putLong(LAST_TIME, gameTime)
        tag.putInt(LAST_TARGET_BUCKET, bucketForKelvin(targetK))
        tag.putBoolean(LAST_TARGET_APPLIANCE, appliance)
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
        val foodSlots = (0 until container.containerSize).filter { isTrackedFood(container.getItem(it)) }
        if (foodSlots.isEmpty()) return
        val target = containerTarget(level, pos)
        for (slot in 0 until container.containerSize) {
            val stack = container.getItem(slot)
            if (isTrackedFood(stack)) {
                val effective = target ?: storedTarget(stack)
                container.setItem(slot, tick(stack, effective.temperatureK, gameTime, effective.appliance))
            }
        }
    }

    /**
     * A powered Heat Sync body is an appliance setpoint.  Ice is a passive local cold source;
     * everything else follows Cold Sweat's actual world temperature at this block position.
     */
    private fun containerTarget(level: Level, pos: BlockPos): ContainerTarget? =
        adjacentThermalTarget(level, pos)?.let { ContainerTarget(it, appliance = true) }
            ?: adjacentPassiveColdTarget(level, pos)?.let { ContainerTarget(it, appliance = false) }
            ?: ambient(level, pos)?.let { ContainerTarget(it, appliance = false) }

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

    private fun ambient(level: Level, pos: BlockPos): Double? {
        if (!ModList.get().isLoaded(HeatSyncMod.COLD_SWEAT_MOD_ID)) return 295.15
        if (ModList.get().isLoaded("weather2") && level is ServerLevel &&
            !weatherProbeChunksLoaded(pos, level::hasChunk)) return null
        val mc = runCatching { ColdSweatAmbientSampler.sampleWorldTemp(level, pos) }.getOrDefault(0.88)
        return mc * 25.0 + 273.15
    }

    internal fun weatherProbeChunksLoaded(pos: BlockPos, hasChunk: (Int, Int) -> Boolean): Boolean {
        val originX = pos.x shr 4
        val originZ = pos.z shr 4
        val offsets = intArrayOf(-6, -3, 0, 3, 6)
        return offsets.all { dx -> offsets.all { dz -> hasChunk(originX + dx, originZ + dz) } }
    }

    @SubscribeEvent
    fun onPlayerLoggedIn(event: net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        reconcilePlayerInventory(player, force = true)
    }

    @SubscribeEvent
    fun onBlockPlaced(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        if (event.entity == null) return
        level.server.execute {
            level.getBlockEntity(event.pos)?.let { activateInventory(it, reconcileNow = true) }
        }
    }

    @SubscribeEvent
    fun onRightClickBlock(event: PlayerInteractEvent.RightClickBlock) {
        val player = event.entity as? ServerPlayer ?: return
        if (player is FakePlayer) return
        val blockEntity = player.level().getBlockEntity(event.pos) ?: return
        if (!isInventory(blockEntity)) return
        activateInventory(blockEntity, reconcileNow = false)
        player.server.execute { reconcileBlockInventory(blockEntity, force = true) }
    }

    @JvmStatic
    fun onBlockEntityChanged(blockEntity: BlockEntity) {
        if (blockEntity.level?.isClientSide != false || !blockEntity.persistentData.getBoolean(ACTIVE)) return
        reconcileBlockInventory(blockEntity, force = false)
    }

    fun activateInventory(blockEntity: BlockEntity, reconcileNow: Boolean) {
        if (!isInventory(blockEntity)) return
        blockEntity.persistentData.putBoolean(ACTIVE, true)
        blockEntity.setChanged()
        if (reconcileNow) reconcileBlockInventory(blockEntity, force = true)
    }

    fun isActivated(blockEntity: BlockEntity): Boolean = blockEntity.persistentData.getBoolean(ACTIVE)

    private fun reconcileBlockInventory(blockEntity: BlockEntity, force: Boolean) {
        val level = blockEntity.level ?: return
        if (level.isClientSide || blockEntity.isRemoved || reconciling.get()) return
        val before = inventoryFingerprint(blockEntity)
        if (!force && inventoryFingerprints[blockEntity] == before) return
        if (before == 0) {
            inventoryFingerprints[blockEntity] = before
            return
        }
        reconciling.set(true)
        try {
            tickBlockInventory(level, blockEntity, level.gameTime)
            inventoryFingerprints[blockEntity] = inventoryFingerprint(blockEntity)
        } finally {
            reconciling.set(false)
        }
    }

    private fun tickBlockInventory(level: Level, blockEntity: BlockEntity, gameTime: Long) {
        if (blockEntity is Container) {
            tickContainer(level, blockEntity.blockPos, blockEntity, gameTime)
            return
        }
        blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent { handler ->
            val writable = handler as? IItemHandlerModifiable ?: return@ifPresent
            val foodSlots = (0 until writable.slots).filter { isTrackedFood(writable.getStackInSlot(it)) }
            if (foodSlots.isEmpty()) return@ifPresent
            val target = containerTarget(level, blockEntity.blockPos)
            foodSlots.forEach { slot ->
                val stack = writable.getStackInSlot(slot)
                val effective = target ?: storedTarget(stack)
                writable.setStackInSlot(slot, tick(stack, effective.temperatureK, gameTime, effective.appliance))
            }
        }
    }

    private fun isInventory(blockEntity: BlockEntity): Boolean =
        blockEntity is Container || blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent

    private fun inventoryFingerprint(blockEntity: BlockEntity): Int {
        var result = 1
        var found = false
        fun add(slot: Int, stack: ItemStack) {
            if (!isTrackedFood(stack)) return
            found = true
            result = 31 * result + slot
            result = 31 * result + stack.item.hashCode()
            result = 31 * result + stack.count
            result = 31 * result + (stack.tag?.hashCode() ?: 0)
        }
        if (blockEntity is Container) {
            (0 until blockEntity.containerSize).forEach { add(it, blockEntity.getItem(it)) }
        } else {
            blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent { handler ->
                (0 until handler.slots).forEach { add(it, handler.getStackInSlot(it)) }
            }
        }
        return if (found) result else 0
    }

    private fun storedTarget(stack: ItemStack): ContainerTarget {
        val tag = stack.tag?.getCompound(KEY)
        return if (tag != null && tag.getInt(VERSION) == CURRENT_VERSION) {
            ContainerTarget(kelvinForBucket(tag.getInt(LAST_TARGET_BUCKET)), tag.getBoolean(LAST_TARGET_APPLIANCE))
        } else ContainerTarget(295.15, false)
    }

    private fun isTrackedFood(stack: ItemStack): Boolean = stack.isEdible &&
        stack.item != FoodItems.SPOILED_MEAT.get() && stack.item != FoodItems.SPOILED_PRODUCE.get()

    @JvmStatic
    fun onPlayerInventoryChanged(player: Player) {
        if (player is ServerPlayer) reconcilePlayerInventory(player, force = false)
    }

    private fun reconcilePlayerInventory(player: ServerPlayer, force: Boolean) {
        if (reconciling.get()) return
        val inventories = listOf(player.inventory.items, player.inventory.armor, player.inventory.offhand)
        val before = inventories.flatten().fold(1) { hash, stack ->
            if (isTrackedFood(stack)) 31 * hash + stack.item.hashCode() + 31 * stack.count + (stack.tag?.hashCode() ?: 0) else hash
        }
        if (!force && playerInventoryFingerprints[player] == before) return
        reconciling.set(true)
        try {
            val target = ambient(player)
            inventories.forEach { inventory ->
                inventory.indices.forEach { slot ->
                    if (isTrackedFood(inventory[slot])) inventory[slot] = tick(inventory[slot], target, player.level().gameTime)
                }
            }
            playerInventoryFingerprints[player] = inventories.flatten().fold(1) { hash, stack ->
                if (isTrackedFood(stack)) 31 * hash + stack.item.hashCode() + 31 * stack.count + (stack.tag?.hashCode() ?: 0) else hash
            }
        } finally {
            reconciling.set(false)
        }
    }

    @SubscribeEvent
    fun onUseStart(event: LivingEntityUseItemEvent.Start) {
        val stack = event.item
        if (!stack.isEdible) return
        (event.entity as? ServerPlayer)?.let { reconcilePlayerInventory(it, force = true) }
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
