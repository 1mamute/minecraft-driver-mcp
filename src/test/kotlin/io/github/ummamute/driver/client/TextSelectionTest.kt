package io.github.ummamute.driver.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextSelectionTest {
    private val bounds = TextBounds(x = 20.0, y = 30.0, width = 40.0, height = 9.0)

    @Test
    fun `unique exact text resolves its measured rectangle`() {
        val target = DrawnText("Fight", 0, 0, bounds)
        val selection = TextSelection(1, 2, listOf(target), 0)

        assertEquals(target, selection.resolve(1, 2, "Fight", null, 1))
        assertEquals(40.0, bounds.centerX)
        assertEquals(34.5, bounds.centerY)
    }

    @Test
    fun `repeated text requires its index from the captured reading order`() {
        val first = DrawnText("Confirm", 10, 10, bounds)
        val second = DrawnText("Confirm", 10, 40, bounds.copy(y = 40.0))
        val selection = TextSelection(1, 1, listOf(first, second), 0)

        assertFailsWith<IllegalArgumentException> { selection.resolve(1, 1, "Confirm", null, 1) }
        assertEquals(second, selection.resolve(1, 1, "Confirm", 1, 1))
        assertFailsWith<IllegalStateException> { selection.resolve(1, 1, "Confirm", 2, 1) }
    }

    @Test
    fun `changed content different capture and expired frames cannot resolve`() {
        val selection = TextSelection(3, 4, listOf(DrawnText("Fight", 0, 0, bounds)), 0)

        assertFailsWith<IllegalArgumentException> { selection.resolve(2, 4, "Fight", null, 1) }
        assertFailsWith<IllegalArgumentException> { selection.resolve(3, 5, "Fight", null, 1) }
        assertFailsWith<IllegalArgumentException> { selection.resolve(3, 4, "Fight", null, 5_000_000_001) }
    }

    @Test
    fun `missing substring and incorrect case do not select text`() {
        val selection = TextSelection(1, 1, listOf(DrawnText("Fight", 0, 0, bounds)), 0)

        for (text in listOf("", " ", "fight", "Figh", "Switch")) {
            assertFailsWith<IllegalArgumentException> { selection.resolve(1, 1, text, null, 1) }
        }
    }

    @Test
    fun `text without supported bounds cannot be clicked`() {
        val selection = TextSelection(1, 1, listOf(DrawnText("Fight", 0, 0)), 0)

        assertFailsWith<IllegalArgumentException> { selection.resolve(1, 1, "Fight", null, 1) }
    }
}
