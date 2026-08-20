package com.bettercontent.heatsync.content.heat

import com.bettercontent.heatsync.HeatSyncConfig
import com.bettercontent.heatsync.HeatSyncRegistries
import com.bettercontent.heatsync.api.HeatBlockEntity
import com.bettercontent.heatsync.api.HeatCapabilities
import com.bettercontent.heatsync.api.IHeatStorage
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.LazyOptional

class HeatPipeBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(HeatSyncRegistries.HEAT_PIPE_BLOCK_ENTITY.get(), pos, state), HeatBlockEntity, IHaveGoggleInformation {
    private var heat: Float = neutralHeat()
    private var heatCapability: LazyOptional<IHeatStorage> = LazyOptional.of { this }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        heat = if (tag.contains(HEAT_KEY)) boundedHeat(tag.getFloat(HEAT_KEY)) else neutralHeat()
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putFloat(HEAT_KEY, heat)
    }

    override fun getUpdateTag(): CompoundTag = saveWithoutMetadata()
    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun <T : Any> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (!remove && cap === HeatCapabilities.HEAT) {
            return heatCapability.cast()
        }
        return super.getCapability(cap, side)
    }

    override fun invalidateCaps() {
        super.invalidateCaps()
        heatCapability.invalidate()
    }

    override fun reviveCaps() {
        super.reviveCaps()
        heatCapability = LazyOptional.of { this }
    }

    override fun getHeat(): Float = heat

    override fun addHeat(heat: Float) {
        this.heat = boundedHeat(this.heat + heat)
        setChanged()
    }

    override fun setHeat(heat: Float) {
        this.heat = boundedHeat(heat)
        setChanged()
    }

    override fun extractHeat(amount: Float, simulate: Boolean): Float {
        if (amount <= 0f) return 0f
        val extracted = minOf(amount, (heat - minHeat()).coerceAtLeast(0f))
        if (!simulate && extracted > 0f) {
            setHeat(heat - extracted)
        }
        return extracted
    }

    override fun maxHeat(): Float = HeatSyncConfig.pipeMaxHeat().toFloat()

    override fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        HeatBlockEntity.addToolTips(this, tooltip)
        return true
    }

    private fun serverTick() {
        if ((level?.gameTime ?: 0L) % TRANSFER_INTERVAL == 0L) {
            HeatBlockEntity.transferAround(this)
        }
    }

    private fun minHeat(): Float = HeatSyncConfig.pipeMinHeat().toFloat()

    private fun boundedHeat(value: Float): Float {
        val finiteValue = value.takeIf(Float::isFinite) ?: HeatSyncConfig.absoluteZeroOffset().toFloat()
        return finiteValue.coerceIn(minHeat(), maxHeat())
    }

    private fun neutralHeat(): Float = boundedHeat(HeatSyncConfig.absoluteZeroOffset().toFloat())

    companion object {
        private const val TRANSFER_INTERVAL = 4L
        private const val HEAT_KEY = "Heat"

        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun tick(level: Level, unusedPos: BlockPos, unusedState: BlockState, blockEntity: HeatPipeBlockEntity) {
            if (!level.isClientSide) {
                blockEntity.serverTick()
            }
        }
    }
}
