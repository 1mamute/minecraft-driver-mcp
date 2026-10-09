package io.github.ummamute.driver.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SlotClickPlannerTest {
    private fun plan(action: String? = null, slot: Int? = 3, outside: Boolean = false, button: Int? = null, hotbar: Int? = null) =
        SlotClickPlanner.plan(SlotClickArgs(action, slot, outside, button, hotbar), slotCount = 63)

    @Test
    fun `defaults to a left pick up`() {
        assertEquals(SlotClick(SlotAction.PICK_UP, 3, 0), plan())
    }

    @Test
    fun `uses the hotbar key as the swap button`() {
        assertEquals(SlotClick(SlotAction.SWAP, 3, 8), plan("swap", hotbar = 8))
        assertEquals(SlotClick(SlotAction.SWAP, 3, 40), plan("swap", hotbar = 40))
    }

    @Test
    fun `rejects a swap without a valid hotbar key`() {
        assertFailsWith<IllegalArgumentException> { plan("swap") }
        assertFailsWith<IllegalArgumentException> { plan("swap", hotbar = 9) }
    }

    @Test
    fun `drops the cursor stack with an outside pick up`() {
        assertEquals(SlotClick(SlotAction.PICK_UP, SlotClickPlanner.OUTSIDE, 1), plan(slot = null, outside = true, button = 1))
        assertFailsWith<IllegalArgumentException> { plan("quick_move", slot = null, outside = true) }
    }

    @Test
    fun `rejects slots outside the menu and a missing slot`() {
        val error = assertFailsWith<IllegalArgumentException> { plan(slot = 63) }
        assertTrue("0 to 62" in error.message.orEmpty())
        assertFailsWith<IllegalArgumentException> { plan(slot = -1) }
        assertFailsWith<IllegalArgumentException> { plan(slot = null) }
    }

    @Test
    fun `rejects unknown actions and buttons`() {
        assertFailsWith<IllegalArgumentException> { plan("smash") }
        assertFailsWith<IllegalArgumentException> { plan(button = 2) }
    }

    @Test
    fun `maps clone to the middle button`() {
        assertEquals(2, plan("clone").button)
    }
}
