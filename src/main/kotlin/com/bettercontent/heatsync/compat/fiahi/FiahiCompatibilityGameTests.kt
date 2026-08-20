package com.bettercontent.heatsync.compat.fiahi

import com.bettercontent.heatsync.HeatSyncMod
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraftforge.fml.ModList
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder(HeatSyncMod.MOD_ID)
@PrefixGameTestTemplate(false)
class FiahiCompatibilityGameTests {
    @GameTest(template = "coolant_exchanger", timeoutTicks = 20)
    fun frozenAndRottenFoodCanRecover(helper: GameTestHelper) {
        if (!ModList.get().isLoaded(FiahiHeatBridge.MOD_ID)) {
            helper.succeed()
            return
        }
        FiahiGameTestAssertions.assertReversibleTemperatureTransfer(helper)
    }
}
