package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.util.FormattedCharSequence

/**
 * Records the text the open screen draws, fed by the mixins on [net.minecraft.client.gui.GuiGraphics] and the tooltip renderer.
 * Everything runs on the render thread; only the finished frame is shared.
 */
object ScreenText {
    private var building: TextFrame? = null
    private var buildingScreen: Screen? = null

    @Volatile
    private var last: Published? = null

    private class Published(val screenClass: String, val title: String, val frame: TextFrame)

    @JvmStatic
    fun begin(screen: Screen) {
        building = TextFrame()
        buildingScreen = screen
    }

    @JvmStatic
    fun end() {
        val frame = building ?: return
        val screen = buildingScreen ?: return
        last = Published(screen.javaClass.name, screen.title.string, frame)
        building = null
        buildingScreen = null
    }

    @JvmStatic
    fun text(text: String, x: Int, y: Int) {
        building?.addText(text, x, y)
    }

    @JvmStatic
    fun text(text: FormattedCharSequence, x: Int, y: Int) {
        building?.addText(plain(text), x, y)
    }

    @JvmStatic
    fun tooltipLine(text: FormattedCharSequence, x: Int, y: Int) {
        building?.addTooltipLine(plain(text), x, y)
    }

    /** The last frame of the open screen. Fails when no screen is open or the screen has not rendered yet. */
    fun snapshot(): ScreenTextResult {
        val screen = Minecraft.getInstance().screen ?: error("No screen is open. Use mc_get_state to see what the client shows")
        val published = last
        if (published == null || published.screenClass != screen.javaClass.name) {
            error("The screen has not rendered yet. Call again in a moment")
        }
        return ScreenTextResult(published.screenClass, published.title, published.frame.texts(), published.frame.tooltips())
    }

    private fun plain(text: FormattedCharSequence): String {
        val builder = StringBuilder()
        text.accept { _, _, codePoint ->
            builder.appendCodePoint(codePoint)
            true
        }
        return builder.toString()
    }
}
