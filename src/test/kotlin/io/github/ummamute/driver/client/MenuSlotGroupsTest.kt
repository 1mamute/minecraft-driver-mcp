package io.github.ummamute.driver.client

import kotlin.test.Test
import kotlin.test.assertEquals

class MenuSlotGroupsTest {
    @Test
    fun `names furnace input fuel and output`() {
        assertEquals("input", MenuSlotGroups.forMenuSlot("minecraft:furnace", 0))
        assertEquals("fuel", MenuSlotGroups.forMenuSlot("minecraft:furnace", 1))
        assertEquals("output", MenuSlotGroups.forMenuSlot("minecraft:smoker", 2))
    }

    @Test
    fun `names brewing stand bottles ingredient and fuel`() {
        assertEquals("bottle", MenuSlotGroups.forMenuSlot("minecraft:brewing_stand", 2))
        assertEquals("ingredient", MenuSlotGroups.forMenuSlot("minecraft:brewing_stand", 3))
        assertEquals("fuel", MenuSlotGroups.forMenuSlot("minecraft:brewing_stand", 4))
    }

    @Test
    fun `names crafting result and grid`() {
        assertEquals("result", MenuSlotGroups.forMenuSlot("minecraft:crafting", 0))
        assertEquals("grid", MenuSlotGroups.forMenuSlot("minecraft:crafting", 9))
        assertEquals("grid", MenuSlotGroups.forMenuSlot("InventoryMenu", 4))
    }

    @Test
    fun `labels every creative inventory slot as creative`() {
        assertEquals("creative", MenuSlotGroups.forMenuSlot("ItemPickerMenu", 0))
        assertEquals("creative", MenuSlotGroups.forMenuSlot("ItemPickerMenu", 44))
    }

    @Test
    fun `falls back to container past the named slots and for unknown menus`() {
        assertEquals("container", MenuSlotGroups.forMenuSlot("minecraft:furnace", 3))
        assertEquals("container", MenuSlotGroups.forMenuSlot("minecraft:generic_9x3", 0))
        assertEquals("container", MenuSlotGroups.forMenuSlot("SomeModMenu", 1))
    }
}
