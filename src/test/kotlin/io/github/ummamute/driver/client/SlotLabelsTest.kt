package io.github.ummamute.driver.client

import kotlin.test.Test
import kotlin.test.assertEquals

class SlotLabelsTest {
    @Test
    fun `labels the inventory index boundaries`() {
        assertEquals(SlotLabel("hotbar", null), SlotLabels.forInventoryIndex(8))
        assertEquals(SlotLabel("main", null), SlotLabels.forInventoryIndex(9))
        assertEquals(SlotLabel("main", null), SlotLabels.forInventoryIndex(35))
        assertEquals(SlotLabel("armor", "boots"), SlotLabels.forInventoryIndex(36))
        assertEquals(SlotLabel("armor", "helmet"), SlotLabels.forInventoryIndex(39))
        assertEquals(SlotLabel("offhand", null), SlotLabels.forInventoryIndex(40))
    }

    @Test
    fun `drops empty slots unless asked to keep them`() {
        val slots = listOf(SlotInfo("main", 9, null, null), SlotInfo("main", 10, null, ItemStackInfo("minecraft:stone", "Stone", 1)))

        assertEquals(listOf(10), SlotLabels.filter(slots, includeEmpty = false).map { it.slot })
        assertEquals(listOf(9, 10), SlotLabels.filter(slots, includeEmpty = true).map { it.slot })
    }
}
