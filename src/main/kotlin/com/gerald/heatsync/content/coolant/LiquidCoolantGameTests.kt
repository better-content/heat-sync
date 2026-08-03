package com.gerald.heatsync.content.coolant

import com.gerald.heatsync.HeatSyncMod
import com.gerald.heatsync.HeatSyncRegistries
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.FluidType
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder(HeatSyncMod.MOD_ID)
@PrefixGameTestTemplate(false)
class LiquidCoolantGameTests {
    private val hotWaterId = ResourceLocation.fromNamespaceAndPath(HeatSyncMod.MOD_ID, "hot_water")

    @GameTest(template = "coolant_exchanger", timeoutTicks = 20)
    fun heatsWaterIntoHotWater(helper: GameTestHelper) {
        val exchangerPos = BlockPos(1, 1, 1)
        val blockEntity = placeExchanger(helper, exchangerPos)

        blockEntity.setHeat(500.0f)
        fillTank(blockEntity, FluidStack(net.minecraft.world.level.material.Fluids.WATER, FluidType.BUCKET_VOLUME))
        tick(helper, exchangerPos, blockEntity)

        helper.succeedIf {
            val tankFluid = blockEntity.fluidHandler().getFluidInTank(0)
            helper.assertTrue(
                tankFluid.fluid === HeatSyncRegistries.hotFluid(hotWaterId),
                "Expected hot water after heating, found ${tankFluid.fluid.fluidType.descriptionId}",
            )
            helper.assertTrue(blockEntity.getHeat() == 100.0f, "Expected remaining heat to be 100.0, was ${blockEntity.getHeat()}")
        }
    }

    @GameTest(template = "coolant_exchanger", timeoutTicks = 20)
    fun coolsHotWaterIntoWater(helper: GameTestHelper) {
        val exchangerPos = BlockPos(1, 1, 1)
        val blockEntity = placeExchanger(helper, exchangerPos)

        blockEntity.setHeat(100.0f)
        fillTank(blockEntity, FluidStack(HeatSyncRegistries.hotFluid(hotWaterId), FluidType.BUCKET_VOLUME))
        tick(helper, exchangerPos, blockEntity)

        helper.succeedIf {
            val tankFluid = blockEntity.fluidHandler().getFluidInTank(0)
            helper.assertTrue(
                tankFluid.fluid === net.minecraft.world.level.material.Fluids.WATER,
                "Expected water after cooling, found ${tankFluid.fluid.fluidType.descriptionId}",
            )
            helper.assertTrue(blockEntity.getHeat() == 420.0f, "Expected remaining heat to be 420.0, was ${blockEntity.getHeat()}")
        }
    }

    private fun placeExchanger(helper: GameTestHelper, pos: BlockPos): CoolantExchangerBlockEntity {
        helper.setBlock(pos, HeatSyncRegistries.COOLANT_EXCHANGER.get())
        val blockEntity = helper.getBlockEntity(pos) as? CoolantExchangerBlockEntity
        return requireNotNull(blockEntity) { "Coolant exchanger block entity was not created at $pos" }
    }

    private fun fillTank(blockEntity: CoolantExchangerBlockEntity, stack: FluidStack) {
        val filled = blockEntity.fluidHandler().fill(stack, IFluidHandler.FluidAction.EXECUTE)
        require(filled == stack.amount) { "Expected to insert ${stack.amount} mB but inserted $filled mB" }
    }

    private fun tick(helper: GameTestHelper, pos: BlockPos, blockEntity: CoolantExchangerBlockEntity) {
        CoolantExchangerBlockEntity.tick(helper.level, pos, helper.getBlockState(pos), blockEntity)
    }

    private fun CoolantExchangerBlockEntity.fluidHandler(): IFluidHandler {
        return getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER)
            .resolve()
            .orElseThrow { IllegalStateException("Coolant exchanger fluid capability was unavailable") }
    }
}
