package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW

/** Outcome of [ClientKeyboard.typeText]: [typed] characters were sent and the focused widget [accepted] some of them. */
@Serializable
data class TypeResult(val typed: Int, val accepted: Int)

/** Outcome of [ClientKeyboard.pressKey]: how many presses the screen (or the game, for Escape in a world) handled. */
@Serializable
data class KeyPressResult(val handled: Int)

/** Sends typed characters and key presses to the open screen, as the keyboard does. Call on the render thread. */
object ClientKeyboard {
    /** Longest text one `mc_type_text` call sends, which covers a book page and keeps the render thread free. */
    const val MAX_TYPED_CHARS = 1000

    private val keyCodes = mapOf(
        "enter" to GLFW.GLFW_KEY_ENTER,
        "escape" to GLFW.GLFW_KEY_ESCAPE,
        "tab" to GLFW.GLFW_KEY_TAB,
        "backspace" to GLFW.GLFW_KEY_BACKSPACE,
        "delete" to GLFW.GLFW_KEY_DELETE,
        "left" to GLFW.GLFW_KEY_LEFT,
        "right" to GLFW.GLFW_KEY_RIGHT,
        "up" to GLFW.GLFW_KEY_UP,
        "down" to GLFW.GLFW_KEY_DOWN,
        "home" to GLFW.GLFW_KEY_HOME,
        "end" to GLFW.GLFW_KEY_END,
        "page_up" to GLFW.GLFW_KEY_PAGE_UP,
        "page_down" to GLFW.GLFW_KEY_PAGE_DOWN,
    )

    /** Keys [pressKey] accepts. */
    val keyNames: List<String> = keyCodes.keys.toList()

    /** Most presses one `mc_press_key` call repeats, enough to empty any text field. */
    const val MAX_PRESSES = 256

    private val mc: Minecraft get() = Minecraft.getInstance()

    /**
     * Sends each character of [text] to the open screen's focused widget, like typing. Returns how many the widget took;
     * an edit box skips characters it cannot hold (control characters, text past its limit).
     */
    fun typeText(text: String): TypeResult {
        require(text.isNotEmpty()) { "Text is empty; pass the characters to type" }
        require(text.length <= MAX_TYPED_CHARS) { "Text is ${text.length} characters; type at most $MAX_TYPED_CHARS per call and split the rest" }
        val screen = mc.screen ?: error("No screen is open, so there is no text field to type into. Open one, or use mc_send_chat for chat")
        val accepted = text.count { screen.charTyped(it, 0) }
        check(accepted > 0) { "No widget took the text. Focus a text field first: mc_click it by label or index from mc_list_widgets, then type again" }
        return TypeResult(text.length, accepted)
    }

    /**
     * Presses and releases [name] [times] times on the open screen. Escape with no screen in a world opens the pause
     * screen, as vanilla does; any other key needs a screen. Modifiers are not offered: widgets read shift and control from
     * the physical keyboard, not from the event, so a sent modifier would do nothing.
     */
    fun pressKey(name: String, times: Int): KeyPressResult {
        val keyCode = keyCodes[name] ?: error("Unknown key \"$name\". Use one of $keyNames")
        require(times in 1..MAX_PRESSES) { "times must be between 1 and $MAX_PRESSES, got $times" }
        val screen = mc.screen ?: return openPauseScreen(keyCode)
        val handled = (1..times).count { pressOnce(screen, keyCode) }
        return KeyPressResult(handled)
    }

    private fun pressOnce(screen: Screen, keyCode: Int): Boolean {
        val handled = screen.keyPressed(keyCode, 0, 0)
        screen.keyReleased(keyCode, 0, 0)
        return handled
    }

    private fun openPauseScreen(keyCode: Int): KeyPressResult {
        check(keyCode == GLFW.GLFW_KEY_ESCAPE && mc.level != null) {
            "No screen is open, so the key has nothing to act on. Only escape does something in a world (it opens the pause screen); use mc_set_key for movement"
        }
        mc.pauseGame(false)
        return KeyPressResult(1)
    }
}
