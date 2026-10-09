package io.github.ummamute.driver.client

import io.github.ummamute.driver.mixin.AbstractWidgetAccessor
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.narration.NarrationThunk
import net.minecraft.client.gui.screens.Screen

/** A clickable element of the open screen. */
@Serializable
data class WidgetSummary(
    val index: Int,
    val type: String,
    val label: String?,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val active: Boolean?,
    val visible: Boolean?,
)

@Serializable
data class ScreenSummary(val screen: String?, val widgets: List<WidgetSummary>)

@Serializable
data class ClickResult(val clicked: String?, val handled: Boolean)

/** Inspects and clicks the open screen through the game's own screen APIs. Call on the render thread. */
object ClientScreens {
    private val mc: Minecraft get() = Minecraft.getInstance()

    fun describe(): ScreenSummary {
        val screen = mc.screen ?: return ScreenSummary(null, emptyList())
        return ScreenSummary(ClassNames.of(screen), screen.children().mapIndexed(::summarize))
    }

    /** Clicks the widget with this index or label, or the point (x, y). */
    fun click(index: Int?, label: String?, x: Double?, y: Double?, button: Int): ClickResult {
        val screen = mc.screen ?: error("No screen is open")
        if (x != null && y != null) return clickAt(screen, x, y, button, clicked = null)
        val target = findTarget(screen, index, label)
        val rectangle = target.rectangle
        markHovered(target)
        val centerX = rectangle.left() + rectangle.width() / 2.0
        val centerY = rectangle.top() + rectangle.height() / 2.0
        return clickAt(screen, centerX, centerY, button, labelOf(target))
    }

    fun close() {
        mc.setScreen(null)
    }

    private fun clickAt(screen: Screen, x: Double, y: Double, button: Int, clicked: String?): ClickResult {
        screen.mouseMoved(x, y)
        val pressed = screen.mouseClicked(x, y, button)
        val released = screen.mouseReleased(x, y, button)
        return ClickResult(clicked, pressed || released)
    }

    private fun findTarget(screen: Screen, index: Int?, label: String?): GuiEventListener {
        val children = screen.children()
        if (index != null) return children.getOrNull(index) ?: error("No widget at index $index")
        if (label == null) error("Give a label, an index or x and y")
        return children.firstOrNull { labelOf(it)?.contains(label, ignoreCase = true) == true } ?: error("No widget labelled \"$label\"")
    }

    private fun summarize(index: Int, child: GuiEventListener): WidgetSummary {
        val rectangle = child.rectangle
        val widget = child as? AbstractWidget
        return WidgetSummary(
            index = index,
            type = ClassNames.of(child),
            label = labelOf(child),
            x = rectangle.left(),
            y = rectangle.top(),
            width = rectangle.width(),
            height = rectangle.height(),
            active = widget?.active,
            visible = widget?.visible,
        )
    }

    private fun labelOf(child: GuiEventListener): String? {
        if (child is AbstractWidget) return child.message.string
        if (child !is NarratableEntry) return null
        return narratedTitle(child)
    }

    /** The title the entry narrates for screen readers, which is the closest typed equivalent of a label. */
    private fun narratedTitle(entry: NarratableEntry): String? {
        val titles = mutableListOf<String>()
        runCatching { entry.updateNarration(TitleCollector(titles)) }
        return titles.firstOrNull()
    }

    private class TitleCollector(private val titles: MutableList<String>) : NarrationElementOutput {
        override fun add(type: NarratedElementType, contents: NarrationThunk<*>) {
            if (type == NarratedElementType.TITLE) contents.getText { titles.add(it) }
        }

        override fun nest(): NarrationElementOutput = this
    }

    /** Widgets only act when hovered, and hover comes from the real cursor, which stays untouched. */
    private fun markHovered(target: GuiEventListener) {
        if (target !is AbstractWidget) return
        (target as AbstractWidgetAccessor).`driver$setHovered`(true)
    }
}
