package com.bettercontent.heatsync.food

import com.bettercontent.heatsync.HeatSyncMod
import com.bettercontent.heatsync.HeatSyncRegistries
import com.bettercontent.heatsync.content.heat.ConstantTemperatureBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BarrelBlockEntity
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder(HeatSyncMod.MOD_ID)
@PrefixGameTestTemplate(false)
class FoodThermalGameTests {
    @GameTest(template = "coolant_exchanger", timeoutTicks = 20)
    fun chestNearColdAndHeatSourcesFreezesAndThaws(helper: GameTestHelper) {
        val chestPos = BlockPos(2, 1, 2)
        val sourcePos = chestPos.west()
        helper.setBlock(chestPos, Blocks.CHEST)
        val chest = requireNotNull(helper.getBlockEntity(chestPos) as? ChestBlockEntity)
        val worldChestPos = chest.blockPos
        chest.setItem(0, ItemStack(Items.COOKED_BEEF))

        helper.setBlock(sourcePos, HeatSyncRegistries.CREATIVE_COLD_SOURCE.get())
        val cold = requireNotNull(helper.getBlockEntity(sourcePos) as? ConstantTemperatureBlockEntity)
        ConstantTemperatureBlockEntity.tick(helper.level, cold.blockPos, cold.blockState, cold)
        helper.assertTrue((FoodThermalService.adjacentThermalTarget(helper.level, worldChestPos) ?: 9999.0) <= 0.0, "Cold source was not exposed as a 0 K thermal target")
        FoodThermalService.tickContainer(helper.level, worldChestPos, chest, 0)
        FoodThermalService.tickContainer(helper.level, worldChestPos, chest, 2_000)
        helper.assertTrue(FoodThermalService.isFrozen(chest.getItem(0)), "Food in a chest beside a cold source must freeze")

        helper.setBlock(sourcePos, HeatSyncRegistries.CREATIVE_HEAT_SOURCE.get())
        val heat = requireNotNull(helper.getBlockEntity(sourcePos) as? ConstantTemperatureBlockEntity)
        heat.setHeat(10_000f)
        helper.assertTrue((FoodThermalService.adjacentThermalTarget(helper.level, worldChestPos) ?: 0.0) > 600.0, "Heat source was not exposed as a hot thermal target")
        FoodThermalService.tickContainer(helper.level, worldChestPos, chest, 4_000)
        FoodThermalService.tickContainer(helper.level, worldChestPos, chest, 6_000)

        helper.succeedIf {
            helper.assertTrue(!FoodThermalService.isFrozen(chest.getItem(0)), "Food in a chest beside a heat source must thaw")
        }
    }

    @GameTest(template = "coolant_exchanger", timeoutTicks = 20)
    fun barrelBesidePackedIceFreezesThroughInventoryScheduler(helper: GameTestHelper) {
        val barrelPos = BlockPos(2, 1, 2)
        helper.setBlock(barrelPos, Blocks.BARREL)
        helper.setBlock(barrelPos.west(), Blocks.PACKED_ICE)
        val barrel = requireNotNull(helper.getBlockEntity(barrelPos) as? BarrelBlockEntity)
        barrel.setItem(0, ItemStack(Items.COOKED_BEEF))

        val level = helper.level as ServerLevel
        FoodThermalService.trackInventory(level, barrel)
        FoodThermalService.tickTrackedInventories(level, 0)
        FoodThermalService.tickTrackedInventories(level, 4_000)

        helper.succeedIf {
            helper.assertTrue(FoodThermalService.isFrozen(barrel.getItem(0)), "Packed ice beside a barrel must freeze food through the normal inventory scheduler")
        }
    }

    @GameTest(template = "coolant_exchanger", timeoutTicks = 20)
    fun frozenFoodPausesSpoilage(helper: GameTestHelper) {
        val frozenApple = ItemStack(Items.APPLE)
        thermalState(frozenApple, temperature = 268.15)
        FoodThermalService.tick(frozenApple, 268.15, 24_000)

        val warmApple = ItemStack(Items.APPLE)
        thermalState(warmApple, temperature = 295.15)
        FoodThermalService.tick(warmApple, 295.15, 24_000)

        helper.succeedIf {
            helper.assertTrue(decay(frozenApple) == 0.0, "Frozen food must not accumulate spoilage")
            helper.assertTrue(decay(warmApple) > 0.0, "Warm food must accumulate spoilage")
        }
    }

    @GameTest(template = "coolant_exchanger", timeoutTicks = 20)
    fun foodTintIntensifiesThroughSpoilageStages(helper: GameTestHelper) {
        val food = ItemStack(Items.APPLE)
        thermalState(food, temperature = 295.15)
        food.tag!!.getCompound("heat_sync_food").putDouble("decay", 0.0)
        val fresh = FoodThermalService.itemTint(food)
        food.tag!!.getCompound("heat_sync_food").putDouble("decay", 1.0 / 7.0)
        val stale = FoodThermalService.itemTint(food)
        food.tag!!.getCompound("heat_sync_food").putDouble("decay", 3.0 / 7.0)
        val spoiled = FoodThermalService.itemTint(food)
        food.tag!!.getCompound("heat_sync_food").putDouble("decay", 5.0 / 7.0)
        val rotten = FoodThermalService.itemTint(food)
        food.tag!!.getCompound("heat_sync_food").putInt("temperature_bucket_c", -1)
        val frozen = FoodThermalService.itemTint(food)

        helper.succeedIf {
            helper.assertTrue(fresh == 0xFFFFFF, "Fresh food must retain its native colour")
            helper.assertTrue(stale == 0xD9C9AA && spoiled == 0x916D43 && rotten == 0x392416, "Spoilage tint must intensify through each stage")
            helper.assertTrue(frozen == 0x9DDCFF, "Frozen tint must override spoilage with an explicit ice-blue tint")
        }
    }

    @GameTest(template = "coolant_exchanger", timeoutTicks = 20)
    fun staleFoodDebuffsLastTenSeconds(helper: GameTestHelper) {
        helper.succeedIf {
            helper.assertTrue(FoodThermalService.debuffDurationTicks(FoodThermalService.Stage.STALE) == 200, "Stale-food debuffs must last 10 seconds")
            helper.assertTrue(FoodThermalService.debuffDurationTicks(FoodThermalService.Stage.SPOILED) == 1200, "More severe food stages retain their one-minute duration")
        }
    }

    private fun thermalState(stack: ItemStack, temperature: Double) {
        val state = stack.orCreateTag.getCompound("heat_sync_food")
        state.putInt("version", 2)
        state.putInt("temperature_bucket_c", kotlin.math.round((temperature - 273.15) / 5.0).toInt())
        state.putDouble("decay", 0.0)
        state.putLong("last_time", 0)
        stack.orCreateTag.put("heat_sync_food", state)
    }

    private fun decay(stack: ItemStack): Double = stack.tag!!.getCompound("heat_sync_food").getDouble("decay")
}
