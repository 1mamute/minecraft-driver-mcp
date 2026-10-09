package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientChat
import io.github.ummamute.driver.client.ClientInput
import io.github.ummamute.driver.client.ClientScreens
import io.github.ummamute.driver.client.ClientSlots
import io.github.ummamute.driver.client.ClientState
import io.github.ummamute.driver.client.SlotClickArgs
import io.github.ummamute.driver.client.SlotClickPlanner
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

/** Tools that act on the client the way a player would. */
internal object ActionTools {
    private val action = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false)

    fun register(server: Server) {
        registerClick(server)
        registerClickSlot(server)
        registerCloseScreen(server)
        registerKey(server)
        registerLookAt(server)
        registerUse(server)
        registerChat(server)
    }

    private fun registerClick(server: Server) {
        server.addTool(
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
        server.addTool(
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
        server.addTool(
            name = "mc_close_screen",
            description = "Close the open screen and return to the game.",
            toolAnnotations = action.copy(idempotentHint = true),
        ) { _ ->
            onRenderThread({ ClientScreens.close() }) { jsonResult(ClientState.snapshot()) }
        }
    }

    private fun registerKey(server: Server) {
        server.addTool(
            name = "mc_set_key",
            description = "Hold or release a movement key. The key stays in that state until it is set again, so release it when done. " +
                "\"inventory\" is one press (down true) that opens or closes the inventory screen.",
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
        server.addTool(
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
        server.addTool(
            name = "mc_use",
            description = "Use what is under the crosshair (right click), for example open a block or interact with an entity. Aim first with mc_look_at.",
            toolAnnotations = action,
        ) { _ -> onRenderThread(ClientInput::use) { jsonResult(mapOf("result" to it)) } }
    }

    private fun registerChat(server: Server) {
        server.addTool(
            name = "mc_send_chat",
            description = "Send a chat message, or a command when the text starts with `/`, as the player. " +
                "Replies arrive later; read them with mc_read_messages.",
            inputSchema = ToolSupport.schema(Property("text", "string", "Chat text or /command"), required = listOf("text")),
            toolAnnotations = action.copy(destructiveHint = true, openWorldHint = true),
        ) { request ->
            val args = arguments(request)
            onRenderThread({ args.requireString("text").also(ClientChat::send) }) { jsonResult(mapOf("sent" to it)) }
        }
    }
}
