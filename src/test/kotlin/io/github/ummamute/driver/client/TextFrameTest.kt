package io.github.ummamute.driver.client

import kotlin.test.Test
import kotlin.test.assertEquals

class TextFrameTest {
    @Test
    fun `texts come back in reading order`() {
        val frame = TextFrame()
        frame.addText("right", 100, 10)
        frame.addText("bottom", 5, 50)
        frame.addText("left", 5, 10)

        assertEquals(listOf("left", "right", "bottom"), frame.texts().map { it.text })
    }

    @Test
    fun `a text drawn twice at the same place appears once`() {
        val frame = TextFrame()
        frame.addText("Play", 10, 10)
        frame.addText("Play", 10, 10)

        assertEquals(1, frame.texts().size)
    }

    @Test
    fun `blank text is ignored`() {
        val frame = TextFrame()
        frame.addText("  ", 0, 0)

        assertEquals(emptyList(), frame.texts())
    }

    @Test
    fun `tooltip lines stacked at one x form one tooltip`() {
        val frame = TextFrame()
        frame.addTooltipLine("Diamond Sword", 20, 30)
        frame.addTooltipLine("+7 Attack Damage", 20, 44)
        frame.addTooltipLine("Other", 200, 100)

        assertEquals(listOf("Diamond Sword\n+7 Attack Damage", "Other"), frame.tooltips())
    }
}
