package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientChat
import io.github.ummamute.driver.client.ClientInput
import io.github.ummamute.driver.client.ClientKeyboard
import io.github.ummamute.driver.client.ClientScreens
import io.github.ummamute.driver.client.ClientSlots
import io.github.ummamute.driver.client.ClientState
import io.github.ummamute.driver.client.RenderThread
import io.github.ummamute.driver.client.SlotClickArgs
import io.github.ummamute.driver.client.SlotClickPlanner
import io.github.ummamute.driver.tools.ToolSupport.addGuardedTool
import io.github.ummamute.driver.tools.ToolSupport.arguments
import io.github.ummamute.driver.tools.ToolSupport.boolean
import io.github.ummamute.driver.tools.ToolSupport.double
import io.github.ummamute.driver.tools.ToolSupport.int
import io.github.ummamute.driver.tools.ToolSupport.jsonResult
import io.github.ummamute.driver.tools.ToolSupport.onRenderThread
import io.github.ummamute.driver.tools.ToolSupport.requireDouble
import io.github.ummamute.driver.tools.ToolSupport.requireString
import io.github.ummamute.driver.tools.ToolSupport.string
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import kotlinx.coroutines.delay

/** Tools that act on the client the way a player would. */
internal object ActionTools {
    private const val HOLD_POLL_MS = 25L

    private val action = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false)

    fun register(server: Server) {
        registerClick(server)
        registerClickSlot(server)
        registerCloseScreen(server)
        registerKey(server)
        registerLookAt(server)
        registerUse(server)
        registerChat(server)
        registerTypeText(server)
        registerPressKey(server)
    }

    private fun registerClick(server: Server) {
        server.addGuardedTool(
            name = "mc_click",
            description = "Click a widget of the open screen by `label` (case-insensitive substring) or `index` from mc_list_widgets, " +
                "or a point with `x` and `y`. Runs through the screen click handlers; the real cursor is untouched.",
            inputSchema = ToolSupport.schema(
                Property("label", "string", "Part of the widget label"),
                Property("index", "integer", "Widget index from mc_list_widgets"),
                Property("x", "number", "Screen x coordinate, used with y"),
                Property("y", "number", "Screen y coordinate, used with x"),
                Property("button", "integer", "Mouse button: 0 left (default), 1 right"),
            ),
            toolAnnotations = action,
        ) { request ->
            val args = arguments(request)
            val index = args.int("index")
            val label = args.string("label")
            val x = args.double("x")
            val y = args.double("y")
            val button = args.int("button") ?: 0
            onRenderThread({ ClientScreens.click(index, label, x, y, button) }) { jsonResult(it) }
        }
    }

    private fun registerClickSlot(server: Server) {
        server.addGuardedTool(
            name = "mc_click_slot",
            description = "Click a slot of the open container screen to move items. `slot` is the menu index from mc_read_container. " +
                "Actions: pick_up (button 0 left, 1 right; also puts the cursor stack down), quick_move (shift-click), " +
                "swap (with `hotbar` 0-8, or 40 for the offhand), throw (button 0 one item, 1 the whole stack), clone (creative), " +
                "pick_up_all (gather matching items). `outside` true with pick_up drops the cursor stack. " +
                "Returns the slot and cursor stack as the client predicts them; the server confirms later.",
            inputSchema = ToolSupport.schema(
                Property("slot", "integer", "Menu slot index from mc_read_container"),
                Property("action", "string", "Click type, default pick_up", allowed = SlotClickPlanner.actionNames),
                Property("button", "integer", "0 left (default) or 1 right, for pick_up and throw"),
                Property("hotbar", "integer", "Hotbar key 0-8, or 40 for the offhand, for swap"),
                Property("outside", "boolean", "Click outside the window to drop the cursor stack, instead of slot"),
            ),
            toolAnnotations = action.copy(destructiveHint = true),
        ) { request ->
            val args = arguments(request)
            val click = SlotClickArgs(
                action = args.string("action"),
                slot = args.int("slot"),
                outside = args.boolean("outside") ?: false,
                button = args.int("button"),
                hotbar = args.int("hotbar"),
            )
            onRenderThread({ ClientSlots.click(click) }) { jsonResult(it) }
        }
    }

    private fun registerCloseScreen(server: Server) {
        server.addGuardedTool(
            name = "mc_close_screen",
            description = "Close the open screen as Escape does: a sub-screen returns to its parent (Video Settings to Options), " +
                "a container screen tells the server it closed, and the last screen returns to the game. Returns the state after closing.",
            toolAnnotations = action,
        ) { _ ->
            onRenderThread({ ClientScreens.close() }) { jsonResult(ClientState.snapshot()) }
        }
    }

    private fun registerKey(server: Server) {
        server.addGuardedTool(
            name = "mc_set_key",
            description = "Hold or release a movement key. The key stays held until it is set again, so release it when done. " +
                "As in vanilla, an open screen takes the keyboard: the player stops while a screen is open and a held key resumes when it closes. " +
                "\"inventory\" is one press (down true) that opens the inventory, or closes it when a container screen is open.",
            inputSchema = ToolSupport.schema(
                Property("name", "string", "The key", allowed = ClientInput.keyNames),
                Property("down", "boolean", "true to hold (default), false to release"),
                required = listOf("name"),
            ),
            toolAnnotations = action.copy(idempotentHint = true),
        ) { request ->
            val args = arguments(request)
            onRenderThread({ ClientInput.setKey(args.requireString("name"), args.boolean("down") ?: true) }) { jsonResult(mapOf("down" to it)) }
        }
    }

    private fun registerLookAt(server: Server) {
        server.addGuardedTool(
            name = "mc_look_at",
            description = "Turn the player to face a world position.",
            inputSchema = ToolSupport.schema(
                Property("x", "number", "World x"),
                Property("y", "number", "World y"),
                Property("z", "number", "World z"),
                required = listOf("x", "y", "z"),
            ),
            toolAnnotations = action.copy(idempotentHint = true),
        ) { request ->
            val args = arguments(request)
            onRenderThread({ ClientInput.lookAt(args.requireDouble("x"), args.requireDouble("y"), args.requireDouble("z")) }) {
                jsonResult(ClientState.snapshot())
            }
        }
    }

    private fun registerUse(server: Server) {
        server.addGuardedTool(
            name = "mc_use",
            description = "Use what is under the crosshair (right click), for example open a block or interact with an entity. " +
                "When that does nothing, uses the held item (main hand, then off hand), so eating, drinking, throwing and raising a shield work too. " +
                "Aim first with mc_look_at. A single use only starts eating, drinking, drawing a bow or raising a shield, and the game " +
                "ends it on the next tick. Give hold_ticks to keep the use key down for that many ticks (20 per second) and wait for the " +
                "release: eating or drinking takes 32, a bow reaches full draw at 20. It applies only when the use starts an item.",
            inputSchema = ToolSupport.schema(
                Property("hold_ticks", "integer", "Ticks to hold the use key, 0 to ${ClientInput.MAX_HOLD_TICKS}; default 0 (a single press)"),
            ),
            toolAnnotations = action,
        ) { request ->
            val holdTicks = arguments(request).int("hold_ticks") ?: 0
            val result = onRenderThread({ ClientInput.use(holdTicks) }) { jsonResult(mapOf("result" to it)) }
            if (result.isError != true) awaitUseRelease()
            result
        }
    }

    /** Polls between short render-thread reads, so the hold never blocks the game loop. */
    private suspend fun awaitUseRelease() {
        while (RenderThread.call { ClientInput.isHoldingUse }) delay(HOLD_POLL_MS)
    }

    private fun registerChat(server: Server) {
        server.addGuardedTool(
            name = "mc_send_chat",
            description = "Send a chat message, or a command when the text starts with `/`, as the player; the text is " +
                "trimmed, collapsed to single spaces and cut to 256 characters. Replies arrive later; read them with mc_read_messages.",
            inputSchema = ToolSupport.schema(Property("text", "string", "Chat text or /command"), required = listOf("text")),
            toolAnnotations = action.copy(destructiveHint = true, openWorldHint = true),
        ) { request ->
            val args = arguments(request)
            onRenderThread({ ClientChat.send(args.requireString("text")) }) { jsonResult(mapOf("sent" to it)) }
        }
    }

    private fun registerTypeText(server: Server) {
        server.addGuardedTool(
            name = "mc_type_text",
            description = "Type text into the focused widget of the open screen, such as the server address, a world name, an anvil rename box, " +
                "a search box, a sign or a command block. Focus a text field first with mc_click. For chat use mc_send_chat. " +
                "Returns how many characters the widget accepted; read the result with mc_read_screen_text.",
            inputSchema = ToolSupport.schema(
                Property("text", "string", "Characters to type, at most ${ClientKeyboard.MAX_TYPED_CHARS}"),
                required = listOf("text"),
            ),
            toolAnnotations = action,
        ) { request ->
            val text = arguments(request).requireString("text")
            onRenderThread({ ClientKeyboard.typeText(text) }) { jsonResult(it) }
        }
    }

    private fun registerPressKey(server: Server) {
        server.addGuardedTool(
            name = "mc_press_key",
            description = "Press and release one keyboard key on the open screen, for example enter to confirm, tab to move focus, " +
                "or backspace with `times` to empty a text field. Returns how many presses were handled. " +
                "With no screen open, escape opens the pause screen (mc_close_screen closes it). For movement use mc_set_key.",
            inputSchema = ToolSupport.schema(
                Property("name", "string", "The key", allowed = ClientKeyboard.keyNames),
                Property("times", "integer", "Presses, 1 to ${ClientKeyboard.MAX_PRESSES}; default 1"),
                required = listOf("name"),
            ),
            toolAnnotations = action,
        ) { request ->
            val args = arguments(request)
            val name = args.requireString("name")
            val times = args.int("times") ?: 1
            onRenderThread({ ClientKeyboard.pressKey(name, times) }) { jsonResult(it) }
        }
    }
}
