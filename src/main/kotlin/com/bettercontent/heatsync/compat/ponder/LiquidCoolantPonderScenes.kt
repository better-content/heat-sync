package com.bettercontent.heatsync.compat.ponder

import com.bettercontent.heatsync.HeatSyncMod
import com.bettercontent.heatsync.HeatSyncRegistries
import com.bettercontent.heatsync.content.coolant.CoolantExchangerBlockEntity
import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object HeatSyncPonderScenes {
    private val HOT_WATER_ID = ResourceLocation.fromNamespaceAndPath(HeatSyncMod.MOD_ID, "hot_water")

    fun coolantExchanger(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        val exchangerPos = util.grid().at(2, 1, 2)
        val heatPipe = util.select().position(2, 1, 1)
        val exchanger = util.select().position(exchangerPos)
        val coldPipe = util.select().position(3, 1, 1)
        val fullSetup = util.select().fromTo(2, 1, 1, 3, 2, 3)

        scene.title("coolant_exchanger", "Exchanging fluids with Heat Sync heat")
        scene.configureBasePlate(0, 0, 5)
        scene.showBasePlate()
        scene.idle(5)

        scene.world().showSection(util.select().layer(0), Direction.UP)
        scene.idle(10)
        scene.world().showSection(util.select().fromTo(1, 1, 1, 3, 2, 3), Direction.DOWN)
        scene.idle(10)

        scene.world().setBlock(exchangerPos, HeatSyncRegistries.COOLANT_EXCHANGER.get().defaultBlockState(), false)
        scene.overlay().showText(80)
            .text("Coolant Exchangers sit on Heat Sync heat lines and convert supported fluids directly in their tank.")
            .colored(PonderPalette.WHITE)
            .pointAt(util.vector().topOf(exchangerPos))
            .placeNearTarget()
        scene.idle(90)

        scene.overlay().showOutline(PonderPalette.RED, "heat_input", heatPipe, 60)
        scene.overlay().showText(60)
            .text("Positive heat from connected pipes heats cold coolant into its hot form.")
            .colored(PonderPalette.RED)
            .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.NORTH))
            .placeNearTarget()
        scene.world().modifyBlockEntity(exchangerPos, CoolantExchangerBlockEntity::class.java) {
            it.setHeat(700.0f)
        }
        scene.world().modifyBlockEntityNBT(exchanger, CoolantExchangerBlockEntity::class.java, { tag ->
            fillTank(tag, "minecraft:water")
        }, false)
        scene.idle(10)
        scene.world().modifyBlockEntityNBT(exchanger, CoolantExchangerBlockEntity::class.java, { tag ->
            fillTank(tag, "$HOT_WATER_ID")
        }, false)
        scene.idle(70)

        scene.addKeyframe()
        scene.overlay().showOutline(PonderPalette.BLUE, "cooling_loop", coldPipe, 60)
        scene.overlay().showText(70)
            .text("If the network has room to absorb more heat, the exchanger pulls heat back out and restores the cooled variant.")
            .colored(PonderPalette.BLUE)
            .pointAt(util.vector().blockSurface(util.grid().at(3, 1, 1), Direction.WEST))
            .placeNearTarget()
        scene.world().modifyBlockEntity(exchangerPos, CoolantExchangerBlockEntity::class.java) {
            it.setHeat(100.0f)
        }
        scene.idle(10)
        scene.world().modifyBlockEntityNBT(exchanger, CoolantExchangerBlockEntity::class.java, { tag ->
            fillTank(tag, HOT_WATER_ID.toString())
        }, false)
        scene.idle(80)

        scene.overlay().showControls(util.vector().topOf(exchangerPos), Pointing.DOWN, 50)
            .rightClick()
            .withItem(ItemStack(Items.WATER_BUCKET))
        scene.overlay().showText(70)
            .text("Any fluid pair defined in data can use the same block, so packs can add custom coolants without code changes.")
            .pointAt(util.vector().topOf(exchangerPos))
            .placeNearTarget()
        scene.idle(80)

        scene.overlay().showOutline(PonderPalette.GREEN, "full_setup", fullSetup, 80)
        scene.overlay().showText(80)
            .text("The exchanger only needs fluid access and a Heat Sync heat connection; the amount of heat per bucket comes from the coolant JSON.")
            .colored(PonderPalette.GREEN)
            .pointAt(util.vector().centerOf(exchangerPos))
            .placeNearTarget()
        scene.idle(90)
    }

    private fun fillTank(tag: CompoundTag, fluidId: String) {
        val tankTag = CompoundTag()
        tankTag.putString("FluidName", fluidId)
        tankTag.putInt("Amount", 1000)
        tag.put("Tank", tankTag)
    }
}
