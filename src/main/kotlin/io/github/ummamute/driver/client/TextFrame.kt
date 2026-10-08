package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable

/** A piece of text a screen drew, at the coordinates it was drawn to. */
@Serializable
data class DrawnText(val text: String, val x: Int, val y: Int)

/** What the open screen showed in its last rendered frame. */
@Serializable
data class ScreenTextResult(val screen: String, val title: String, val texts: List<DrawnText>, val tooltips: List<String>)

/** Collects the text of one render pass. Has no Minecraft types so it can be tested on its own. */
class TextFrame {
    private val drawn = LinkedHashSet<DrawnText>()
    private val tooltipLines = LinkedHashSet<DrawnText>()

    fun addText(text: String, x: Int, y: Int) = add(drawn, text, x, y)

    fun addTooltipLine(text: String, x: Int, y: Int) = add(tooltipLines, text, x, y)

    /** Texts in reading order (top to bottom, then left to right). */
    fun texts(): List<DrawnText> = drawn.sortedWith(compareBy({ it.y }, { it.x }))

    /** Tooltips in the order they were drawn, one entry per tooltip with its lines joined by newlines. */
    fun tooltips(): List<String> = groupTooltips(tooltipLines.toList())

    private fun add(target: MutableSet<DrawnText>, text: String, x: Int, y: Int) {
        if (text.isNotBlank()) target.add(DrawnText(text, x, y))
    }

    private fun groupTooltips(lines: List<DrawnText>): List<String> {
        val groups = mutableListOf<MutableList<DrawnText>>()
        for (line in lines) {
            val previous = groups.lastOrNull()?.last()
            val continues = previous != null && line.x == previous.x && line.y > previous.y && line.y - previous.y <= MAX_LINE_GAP
            if (continues) groups.last().add(line) else groups.add(mutableListOf(line))
        }
        return groups.map { group -> group.joinToString("\n") { it.text } }
    }

    private companion object {
        /** Tooltip lines are about 10 px apart; the title line sits a few px further from the body. */
        const val MAX_LINE_GAP = 16
    }
}
