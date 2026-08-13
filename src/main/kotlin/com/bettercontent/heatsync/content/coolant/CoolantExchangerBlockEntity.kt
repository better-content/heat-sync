package com.bettercontent.heatsync.content.coolant

import com.bettercontent.heatsync.HeatSyncRegistries
import com.bettercontent.heatsync.api.HeatBlockEntity
import com.bettercontent.heatsync.api.HeatCapabilities
import com.bettercontent.heatsync.api.IHeatStorage
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import com.simibubi.create.foundation.utility.CreateLang
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.templates.FluidTank
import net.minecraftforge.registries.ForgeRegistries

class CoolantExchangerBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(HeatSyncRegistries.COOLANT_EXCHANGER_BLOCK_ENTITY.get(), pos, state), HeatBlockEntity,
    IHaveGoggleInformation {
    private var heat: Float = 0f
    private var tickCounter: Long = 0L

    private val tank = object : FluidTank(TANK_CAPACITY) {
        override fun isFluidValid(stack: FluidStack): Boolean {
            val fluidId = ForgeRegistries.FLUIDS.getKey(stack.fluid)
            return stack.isEmpty || (fluidId != null && LiquidCoolantManager.supported(fluidId))
        }

        override fun onContentsChanged() {
            setChanged()
            sendData()
        }
    }

    private var fluidCapability: LazyOptional<IFluidHandler> = LazyOptional.of { tank }
    private var heatCapability: LazyOptional<IHeatStorage> = LazyOptional.of { this }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        heat = tag.getFloat(HEAT_KEY).coerceAtLeast(ABSOLUTE_ZERO)
        tickCounter = tag.getLong(TICK_KEY)
        tank.readFromNBT(tag.getCompound(TANK_KEY))
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putFloat(HEAT_KEY, heat)
        tag.putLong(TICK_KEY, tickCounter)
        tag.put(TANK_KEY, tank.writeToNBT(CompoundTag()))
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()

    override fun handleUpdateTag(tag: CompoundTag) {
        load(tag)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun <T : Any> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (!remove && cap === ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast()
        }
        if (!remove && cap === HeatCapabilities.HEAT) {
            return heatCapability.cast()
        }
        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        fluidCapability.invalidate()
        heatCapability.invalidate()
    }

    override fun reviveCaps() {
        super.reviveCaps()
        fluidCapability = LazyOptional.of { tank }
        heatCapability = LazyOptional.of { this }
    }

    override fun getHeat(): Float = heat

    override fun addHeat(heat: Float) {
        this.heat = (this.heat + heat).coerceAtLeast(ABSOLUTE_ZERO)
        setChanged()
    }

    override fun setHeat(heat: Float) {
        this.heat = heat.coerceAtLeast(ABSOLUTE_ZERO)
        setChanged()
    }

    override fun maxHeat(): Float = INTERNAL_HEAT_BUFFER

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        HeatBlockEntity.addToolTips(this, tooltip)
        super<IHaveGoggleInformation>.containedFluidTooltip(tooltip, isPlayerSneaking, fluidCapability)
        val stack = tank.fluid
        if (!stack.isEmpty) {
            val fluidId = ForgeRegistries.FLUIDS.getKey(stack.fluid) ?: return true
            val definition = LiquidCoolantManager.find(fluidId)
            if (definition != null) {
                val modeKey = if (definition.matchesCold(fluidId)) {
                    "tooltip.heat_sync.mode.heating"
                } else {
                    "tooltip.heat_sync.mode.cooling"
                }
                CreateLang.translate(modeKey)
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip)
            }
        }
        return true
    }

    private fun serverTick() {
        tickCounter++
        val convertedBeforeTransfer = processFluid()

        if (tickCounter % NETWORK_TRANSFER_INTERVAL == 0L) {
            HeatBlockEntity.transferAround(this)
        }

        val convertedAfterTransfer = if (convertedBeforeTransfer) false else processFluid()
        val buffered = clampWorkingHeat()
        val changed = convertedBeforeTransfer || convertedAfterTransfer || buffered

        if (tickCounter % NETWORK_TRANSFER_INTERVAL == 0L) {
            if (!canSpendHeatIntoFluidThisTick(convertedBeforeTransfer || convertedAfterTransfer)) {
                HeatBlockEntity.handleOverheat(this)
            }
            HeatBlockEntity.trySync(this)
        } else if (changed) {
            sendData()
        }
    }

    private fun processFluid(): Boolean {
        val stack = tank.fluid
        if (stack.isEmpty) {
            return false
        }

        val fluidId = ForgeRegistries.FLUIDS.getKey(stack.fluid) ?: return false
        val definition = LiquidCoolantManager.find(fluidId) ?: return false
        val amount = stack.amount

        val exchange = CoolantExchangeLogic.computeExchange(
            fluidId = fluidId,
            amount = amount,
            currentHeat = heat,
            maxHeat = INTERNAL_HEAT_BUFFER,
            definition = definition,
        ) ?: return false
        val converted = resolveFluid(exchange.targetFluid) ?: return false
        tank.setFluid(FluidStack(converted, amount))
        heat = exchange.resultingHeat
        setChanged()
        return true
    }

    private fun canSpendHeatIntoFluidThisTick(convertedThisTick: Boolean): Boolean {
        return convertedThisTick || hasMatchingFluidForCurrentHeat()
    }

    private fun hasMatchingFluidForCurrentHeat(): Boolean {
        val stack = tank.fluid
        if (stack.isEmpty) {
            return false
        }

        val fluidId = ForgeRegistries.FLUIDS.getKey(stack.fluid) ?: return false
        val definition = LiquidCoolantManager.find(fluidId) ?: return false
        return (heat > ABSOLUTE_ZERO && definition.matchesCold(fluidId)) ||
            (heat < INTERNAL_HEAT_BUFFER && definition.matchesHot(fluidId))
    }

    private fun clampWorkingHeat(): Boolean {
        val clamped = if (hasMatchingFluidForCurrentHeat()) {
            heat.coerceIn(ABSOLUTE_ZERO, INTERNAL_HEAT_BUFFER)
        } else {
            heat.coerceAtLeast(ABSOLUTE_ZERO)
        }
        if (clamped == heat) {
            return false
        }

        heat = clamped
        setChanged()
        return true
    }

    private fun sendData() {
        val currentLevel = level ?: return
        if (currentLevel.isClientSide) {
            return
        }

        val state = blockState
        currentLevel.sendBlockUpdated(blockPos, state, state, 3)
    }

    private fun resolveFluid(fluidId: ResourceLocation) = ForgeRegistries.FLUIDS.getValue(fluidId)

    companion object {
        private const val TANK_CAPACITY = 1000
        private const val ABSOLUTE_ZERO = 0f
        private const val INTERNAL_HEAT_BUFFER = 800f
        private const val NETWORK_TRANSFER_INTERVAL = 4L
        private const val HEAT_KEY = "Heat"
        private const val TANK_KEY = "Tank"
        private const val TICK_KEY = "TickCounter"

        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun tick(level: Level, unusedPos: BlockPos, unusedState: BlockState, blockEntity: CoolantExchangerBlockEntity) {
            if (!level.isClientSide) {
                blockEntity.serverTick()
            }
        }
    }
}
