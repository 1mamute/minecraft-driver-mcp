package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.util.FormattedCharSequence
import java.lang.ref.WeakReference

/**
 * Records the text the open screen draws, fed by the mixins on [net.minecraft.client.gui.GuiGraphics] and the tooltip renderer.
 * Everything runs on the render thread; only the finished frame is shared.
 */
object ScreenText {
    private var building: TextFrame? = null
    private var buildingScreen: Screen? = null
    private var revision = 0L
    private var nextFrameId = 0L
    private var selection: TextSelection? = null

    /** A screen transition invalidates captures even when the same screen object is later reopened. */
    @JvmStatic
    fun invalidate() {
        WidgetRenderCapture.invalidate()
        last = null
        selection = null
        building = null
        buildingScreen = null
        revision++
    }

    @Volatile
    private var last: Published? = null

    /** Holds the screen weakly so a closed screen is not kept alive until the next one renders. */
    private class Published(screen: Screen, val frame: TextFrame, val revision: Long) {
        private val screen = WeakReference(screen)
        val screenClass = ClassNames.of(screen)
        val title = screen.title.string
        val width = screen.width
        val height = screen.height
        val renderedNanos = System.nanoTime()

        fun isFrom(other: Screen): Boolean = screen.get() === other
    }

    @JvmStatic
    fun begin(screen: Screen) {
        building = TextFrame()
        buildingScreen = screen
        WidgetRenderCapture.begin(screen)
    }

    @JvmStatic
    fun end() {
        val frame = building ?: return
        val screen = buildingScreen ?: return
        val previous = last
        if (previous == null || !sameContent(previous, screen, frame)) revision++
        last = Published(screen, frame, revision)
        building = null
        buildingScreen = null
        WidgetRenderCapture.end()
    }

    @JvmStatic
    fun text(graphics: GuiGraphics, font: Font, text: String?, x: Int, y: Int) {
        if (text == null || building == null) return
        building?.addText(text, x, y, TextGeometry.measure(graphics, x, y, font.width(text), font.lineHeight))
    }

    @JvmStatic
    fun text(graphics: GuiGraphics, font: Font, text: FormattedCharSequence, x: Int, y: Int) {
        if (building == null) return
        building?.addText(plain(text), x, y, TextGeometry.measure(graphics, x, y, font.width(text), font.lineHeight))
    }

    @JvmStatic
    fun tooltipLine(text: FormattedCharSequence, x: Int, y: Int) {
        building?.addTooltipLine(plain(text), x, y)
    }

    /** The last frame of the open screen. Fails when no screen is open or the screen has not rendered yet. */
    fun snapshot(): ScreenTextResult {
        val published = current()
        val frameId = ++nextFrameId
        val texts = published.frame.texts()
        selection = TextSelection(frameId, published.revision, texts, System.nanoTime())
        return ScreenTextResult(published.screenClass, published.title, texts, published.frame.tooltips(), frameId)
    }

    /** Clicks measured text from the most recent read, after checking screen identity and unchanged frame content. */
    fun click(frameId: Long, text: String, textIndex: Int?, button: Int): TextClickResult {
        require(button in 0..1) { "button must be 0 (left) or 1 (right); retry with a supported mouse button" }
        val published = current()
        val capture = selection ?: error("Call mc_read_screen_text first and use its frameId with mc_click_text")
        val target = capture.resolve(frameId, published.revision, text, textIndex, System.nanoTime())
        val bounds = checkNotNull(target.bounds)
        val screen = checkNotNull(Minecraft.getInstance().screen)
        require(bounds.x >= 0 && bounds.y >= 0 && bounds.x + bounds.width <= screen.width && bounds.y + bounds.height <= screen.height) {
            "The text is outside the visible screen. Call mc_read_screen_text or inspect mc_screenshot"
        }
        selection = null
        val result = ClientScreens.click(null, null, bounds.centerX, bounds.centerY, button)
        return TextClickResult(text, bounds.centerX, bounds.centerY, result.handled)
    }

    private fun current(): Published {
        val screen = Minecraft.getInstance().screen ?: error("No screen is open. Use mc_get_state to see what the client shows")
        val published = last
        check(published != null && published.isFrom(screen) && published.width == screen.width && published.height == screen.height) {
            "The screen has not rendered at its current size. Call mc_read_screen_text again in a moment"
        }
        check(System.nanoTime() - published.renderedNanos <= MAX_RENDER_AGE_NANOS) {
            "The screen has no fresh rendered frame. Wait for rendering, then call mc_read_screen_text"
        }
        return published
    }

    private fun sameContent(previous: Published, screen: Screen, frame: TextFrame): Boolean =
        previous.isFrom(screen) && previous.width == screen.width && previous.height == screen.height &&
            previous.title == screen.title.string && previous.frame.texts() == frame.texts() && previous.frame.tooltips() == frame.tooltips()

    private fun plain(text: FormattedCharSequence): String {
        val builder = StringBuilder()
        text.accept { _, _, codePoint ->
            builder.appendCodePoint(codePoint)
            true
        }
        return builder.toString()
    }

    private const val MAX_RENDER_AGE_NANOS = 1_000_000_000L
}
