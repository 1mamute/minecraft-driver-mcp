package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.Screen
import org.joml.Matrix4f
import org.joml.Vector3f
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import kotlin.math.abs

/** GUI coordinates observed at the entry to a widget's render method, before its custom drawing. */
@Serializable
data class RenderedWidgetBounds(val x: Double, val y: Double, val width: Double, val height: Double)

internal data class WidgetBoundsObservation(
    val rendered: RenderedWidgetBounds?,
    val reason: String?,
)

/** Records one screen frame's widget positions; a pose transform does not establish input coordinate semantics. */
object WidgetRenderCapture {
    private const val MAX_AGE_NANOS = 1_000_000_000L
    private const val EPSILON = 0.0001F
    private var buildingScreen: Screen? = null
    private var building = IdentityHashMap<AbstractWidget, Captured>()
    private var published: Published? = null

    /** A returning screen must render again before its old widgets can be targeted. */
    fun invalidate() {
        published = null
        buildingScreen = null
        building.clear()
    }

    private data class Captured(val raw: ScreenRectangle, val bounds: RenderedWidgetBounds?, val reason: String?)

    private class Published(screen: Screen, entries: Map<AbstractWidget, Captured>) {
        val screen = WeakReference(screen)
        val width = screen.width
        val height = screen.height
        val time = System.nanoTime()
        val entries = entries.map { (widget, capture) -> WeakReference(widget) to capture }
    }

    @JvmStatic
    fun begin(screen: Screen) {
        buildingScreen = screen
        building = IdentityHashMap()
    }

    @JvmStatic
    fun end() {
        val screen = buildingScreen ?: return
        published = Published(screen, building)
        buildingScreen = null
        building.clear()
    }

    @JvmStatic
    fun record(widget: AbstractWidget, graphics: GuiGraphics) {
        if (buildingScreen == null || !widget.visible) return
        val raw = widget.rectangle
        val capture = capture(raw, graphics)
        val previous = building[widget]
        building[widget] = if (previous != null && previous != capture) {
            capture.copy(reason = "The widget rendered more than once with different bounds")
        } else {
            capture
        }
    }

    internal fun observe(screen: Screen, widget: AbstractWidget): WidgetBoundsObservation {
        val frame = published ?: return WidgetBoundsObservation(null, "The screen has not rendered yet")
        if (!isCurrent(frame, screen)) return WidgetBoundsObservation(null, "The last widget frame is stale; inspect the screen again")
        val capture = frame.entries.firstOrNull { it.first.get() === widget }?.second
            ?: return WidgetBoundsObservation(null, "The widget was not rendered in the last screen frame")
        val reason = if (capture.raw != widget.rectangle) "The widget moved after its last render" else capture.reason
        return WidgetBoundsObservation(capture.bounds, reason)
    }

    private fun isCurrent(frame: Published, screen: Screen): Boolean {
        val window = Minecraft.getInstance().window
        val sameScreen = frame.screen.get() === screen && screen.width == frame.width && screen.height == frame.height
        val sameWindow = window.guiScaledWidth == frame.width && window.guiScaledHeight == frame.height
        return sameScreen && sameWindow && System.nanoTime() - frame.time <= MAX_AGE_NANOS
    }

    private fun capture(raw: ScreenRectangle, graphics: GuiGraphics): Captured {
        val pose = graphics.pose().last().pose()
        if (!isAxisAligned(pose)) return Captured(raw, null, "The widget uses an unsupported render transform")
        val topLeft = pose.transformPosition(Vector3f(raw.left().toFloat(), raw.top().toFloat(), 0F))
        val bottomRight = pose.transformPosition(Vector3f(raw.right().toFloat(), raw.bottom().toFloat(), 0F))
        val bounds = RenderedWidgetBounds(
            x = topLeft.x.toDouble(),
            y = topLeft.y.toDouble(),
            width = (bottomRight.x - topLeft.x).toDouble(),
            height = (bottomRight.y - topLeft.y).toDouble(),
        )
        if (!listOf(bounds.x, bounds.y, bounds.width, bounds.height).all { it.isFinite() }) {
            return Captured(raw, null, "The widget render transform produced invalid coordinates")
        }
        return Captured(raw, bounds, uncertainty(raw, bounds, graphics))
    }

    private fun uncertainty(raw: ScreenRectangle, bounds: RenderedWidgetBounds, graphics: GuiGraphics): String? {
        if (raw.width() <= 0 || raw.height() <= 0) return "The widget has no usable rectangle"
        val samePosition = abs(bounds.x - raw.left()) < EPSILON && abs(bounds.y - raw.top()) < EPSILON
        val sameSize = abs(bounds.width - raw.width()) < EPSILON && abs(bounds.height - raw.height()) < EPSILON
        if (!samePosition || !sameSize) return "The widget is drawn with a transform; its input coordinates are unknown"
        val centerX = (bounds.x + bounds.width / 2).toInt()
        val centerY = (bounds.y + bounds.height / 2).toInt()
        if (!graphics.containsPointInScissor(centerX, centerY)) return "The widget center is clipped"
        return null
    }

    private fun isAxisAligned(pose: Matrix4f): Boolean {
        if (!pose.isFinite()) return false
        val noRotation = abs(pose.m01()) < EPSILON && abs(pose.m10()) < EPSILON
        val noPerspective = abs(pose.m03()) < EPSILON && abs(pose.m13()) < EPSILON && abs(pose.m33() - 1F) < EPSILON
        val noTilt = abs(pose.m02()) < EPSILON && abs(pose.m12()) < EPSILON && abs(pose.m23()) < EPSILON
        val noDepthProjection = abs(pose.m20()) < EPSILON && abs(pose.m21()) < EPSILON
        val planar = noRotation && noTilt && noDepthProjection
        val positiveScale = pose.m00() > 0F && pose.m11() > 0F
        return planar && noPerspective && positiveScale
    }
}
