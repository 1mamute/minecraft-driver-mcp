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
    val inspection: WidgetInspection,
)

/** A selector and coordinate evidence for one event-tree entry. Paths last only while the tree is unchanged. */
@Serializable
data class WidgetInspection(
    val path: String,
    /** `screen` means observed GUI geometry and ancestor hit tests agree; it does not guarantee an action. */
    val boundsStatus: String,
    val boundsReason: String?,
    val renderedBounds: RenderedWidgetBounds?,
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
        val widgets = ClientWidgets.targets(screen).mapIndexed { index, target -> summarize(screen, index, target) }
        return ScreenSummary(ClassNames.of(screen), widgets)
    }

    /** Clicks the widget with this index or label, or the point (x, y). */
    fun click(index: Int?, label: String?, x: Double?, y: Double?, button: Int, path: String? = null): ClickResult {
        val screen = mc.screen ?: error("No screen is open, so there is nothing to click. Use mc_get_state to see what the client shows")
        if (x != null && y != null) return clickAt(screen, x, y, button, clicked = null)
        val target = findTarget(screen, index, label, path)
        val observation = boundsOf(screen, target)
        check(observation.reason == null) {
            "Cannot click widget ${target.path}: ${observation.reason}. Use mc_read_screen_text or mc_screenshot, then explicit GUI coordinates"
        }
        val rectangle = target.listener.rectangle
        markHovered(target.listener)
        val centerX = rectangle.left() + rectangle.width() / 2.0
        val centerY = rectangle.top() + rectangle.height() / 2.0
        return clickAt(screen, centerX, centerY, button, labelOf(target.listener))
    }

    /** Closes the open screen the way Escape does, so parents reopen and container screens send the close packet. */
    fun close() {
        mc.screen?.onClose()
    }

    private fun clickAt(screen: Screen, x: Double, y: Double, button: Int, clicked: String?): ClickResult {
        screen.mouseMoved(x, y)
        val pressed = screen.mouseClicked(x, y, button)
        val released = screen.mouseReleased(x, y, button)
        return ClickResult(clicked, pressed || released)
    }

    private fun findTarget(screen: Screen, index: Int?, label: String?, path: String?): WidgetTarget {
        val children = ClientWidgets.targets(screen)
        if (path != null) {
            return children.firstOrNull { it.path == path }
                ?: error("No widget at path $path. Call mc_list_widgets for the current paths")
        }
        if (index != null) {
            return children.getOrNull(index) ?: error("No widget at index $index of ${children.size}. Call mc_list_widgets for the current indexes")
        }
        if (label == null) error("Give a label, an index, or both x and y")
        val match = children.firstOrNull { labelOf(it.listener)?.contains(label, ignoreCase = true) == true }
        return match ?: error("No widget labelled \"$label\". Call mc_list_widgets to see the labels on this screen")
    }

    private fun summarize(screen: Screen, index: Int, target: WidgetTarget): WidgetSummary {
        val child = target.listener
        val rectangle = child.rectangle
        val widget = child as? AbstractWidget
        val observation = boundsOf(screen, target)
        return WidgetSummary(
            index = index,
            type = ClassNames.widget(child),
            label = labelOf(child),
            x = rectangle.left(),
            y = rectangle.top(),
            width = rectangle.width(),
            height = rectangle.height(),
            active = widget?.active,
            visible = widget?.visible,
            inspection = WidgetInspection(
                path = target.path,
                boundsStatus = if (observation.reason == null) "screen" else "uncertain",
                boundsReason = observation.reason,
                renderedBounds = observation.rendered,
            ),
        )
    }

    private fun boundsOf(screen: Screen, target: WidgetTarget): WidgetBoundsObservation {
        val widget = target.listener as? AbstractWidget
            ?: return WidgetBoundsObservation(null, "This event listener does not expose supported widget bounds")
        if (!widget.javaClass.name.startsWith("net.minecraft.")) {
            return WidgetBoundsObservation(null, "Custom widget input and drawing bounds are not verified")
        }
        val observation = WidgetRenderCapture.observe(screen, widget)
        if (observation.reason != null) return observation
        val reason = unsupportedBounds(screen, target, widget)
        return observation.copy(reason = reason)
    }

    private fun unsupportedBounds(screen: Screen, target: WidgetTarget, widget: AbstractWidget): String? {
        if (!widget.active || !widget.visible) return "The widget is inactive or hidden"
        val rectangle = widget.rectangle
        val centerX = rectangle.left() + rectangle.width() / 2.0
        val centerY = rectangle.top() + rectangle.height() / 2.0
        val outsideX = centerX < 0 || centerX >= screen.width
        val outsideY = centerY < 0 || centerY >= screen.height
        if (outsideX || outsideY) return "The widget center is outside the screen"
        if (!target.receivesPoint(centerX, centerY)) return "The widget center does not route through its parent containers"
        return null
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
