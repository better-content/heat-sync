package com.bettercontent.heatsync.food

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ContainerFoodAdmissionPolicyTest {
    @Test
    fun `only food-bearing containers without unresolved loot activate automatically`() {
        assertFalse(ContainerFoodAdmissionPolicy.shouldActivate(hasTrackedFood = false, hasUnopenedLoot = false))
        assertFalse(ContainerFoodAdmissionPolicy.shouldActivate(hasTrackedFood = true, hasUnopenedLoot = true))
        assertTrue(ContainerFoodAdmissionPolicy.shouldActivate(hasTrackedFood = true, hasUnopenedLoot = false))
    }

    @Test
    fun `container activation reads the unresolved loot table through a registered accessor`() {
        val root = Path.of("src/main")
        val mixins = Files.readString(root.resolve("resources/heat_sync.mixins.json"))
        val accessor = Files.readString(root.resolve("java/com/bettercontent/heatsync/mixin/minecraft/RandomizableContainerBlockEntityAccessor.java"))
        val service = Files.readString(root.resolve("kotlin/com/bettercontent/heatsync/food/FoodThermalService.kt"))

        assertTrue(mixins.contains("minecraft.RandomizableContainerBlockEntityAccessor"))
        assertTrue(accessor.contains("@Accessor(\"lootTable\")"))
        assertTrue(service.contains("heatSyncLootTable != null"))
        assertTrue(service.contains("ContainerFoodAdmissionPolicy.shouldActivate"))
    }
}
