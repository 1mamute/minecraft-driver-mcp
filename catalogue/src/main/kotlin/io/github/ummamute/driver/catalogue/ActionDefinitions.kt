package io.github.ummamute.driver.catalogue

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

internal object ActionDefinitions {
    private val action = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false)

    val tools: List<Tool> = listOf(
        click(),
        clickSlot(),
        closeScreen(),
        setKey(),
        lookAt(),
        use(),
        sendChat(),
        typeText(),
        pressKey(),
    )

    private fun click(): Tool {
        return Tool(
            name = "mc_click",
            description = "Click a widget of the open screen by `label` (case-insensitive substring) or `index` from mc_list_widgets, " +
                "or a point with `x` and `y`. Runs through the screen click handlers; the real cursor is untouched.",
            inputSchema = schema(
                Property("label", "string", "Part of the widget label"),
                Property("index", "integer", "Widget index from mc_list_widgets"),
                Property("x", "number", "Screen x coordinate, used with y"),
                Property("y", "number", "Screen y coordinate, used with x"),
                Property("button", "integer", "Mouse button: 0 left (default), 1 right"),
            ),
            annotations = action,
        )
    }

    private fun clickSlot(): Tool {
        return Tool(
            name = "mc_click_slot",
            description = "Click a slot of the open container screen to move items. `slot` is the menu index from mc_read_container. " +
                "Actions: pick_up (button 0 left, 1 right; also puts the cursor stack down), quick_move (shift-click), " +
                "swap (with `hotbar` 0-8, or 40 for the offhand), throw (button 0 one item, 1 the whole stack), clone (creative), " +
                "pick_up_all (gather matching items). `outside` true with pick_up drops the cursor stack. " +
                "Returns the slot and cursor stack as the client predicts them; the server confirms later.",
            inputSchema = schema(
                Property("slot", "integer", "Menu slot index from mc_read_container"),
                Property("action", "string", "Click type, default pick_up", allowed = ToolContract.SLOT_ACTIONS),
                Property("button", "integer", "0 left (default) or 1 right, for pick_up and throw"),
                Property("hotbar", "integer", "Hotbar key 0-8, or 40 for the offhand, for swap"),
                Property("outside", "boolean", "Click outside the window to drop the cursor stack, instead of slot"),
            ),
            annotations = action.copy(destructiveHint = true),
        )
    }

    private fun closeScreen(): Tool {
        return Tool(
            name = "mc_close_screen",
            description = "Close the open screen as Escape does: a sub-screen returns to its parent (Video Settings to Options), " +
                "a container screen tells the server it closed, and the last screen returns to the game. Returns the state after closing.",
            inputSchema = ToolSchema(),
            annotations = action,
        )
    }

    private fun setKey(): Tool {
        return Tool(
            name = "mc_set_key",
            description = "Hold or release a movement key. The key stays held until it is set again, so release it when done. " +
                "As in vanilla, an open screen takes the keyboard: the player stops while a screen is open and a held key resumes when it closes. " +
                "\"inventory\" is one press (down true) that opens the inventory, or closes it when a container screen is open.",
            inputSchema = schema(
                Property("name", "string", "The key", allowed = ToolContract.MOVEMENT_KEYS),
                Property("down", "boolean", "true to hold (default), false to release"),
                required = listOf("name"),
            ),
            annotations = action.copy(idempotentHint = true),
        )
    }

    private fun lookAt(): Tool {
        return Tool(
            name = "mc_look_at",
            description = "Turn the player to face a world position.",
            inputSchema = schema(
                Property("x", "number", "World x"),
                Property("y", "number", "World y"),
                Property("z", "number", "World z"),
                required = listOf("x", "y", "z"),
            ),
            annotations = action.copy(idempotentHint = true),
        )
    }

    private fun use(): Tool {
        return Tool(
            name = "mc_use",
            description = "Use what is under the crosshair (right click), for example open a block or interact with an entity. " +
                "When that does nothing, uses the held item (main hand, then off hand), so eating, drinking, throwing and raising a shield work too. " +
                "Aim first with mc_look_at. A single use only starts eating, drinking, drawing a bow or raising a shield, and the game " +
                "ends it on the next tick. Give hold_ticks to keep the use key down for that many ticks (20 per second) and wait for the " +
                "release: eating or drinking takes 32, a bow reaches full draw at 20. It applies only when the use starts an item.",
            inputSchema = schema(
                Property("hold_ticks", "integer", "Ticks to hold the use key, 0 to ${ToolContract.MAX_HOLD_TICKS}; default 0 (a single press)"),
            ),
            annotations = action,
        )
    }

    private fun sendChat(): Tool {
        return Tool(
            name = "mc_send_chat",
            description = "Send a chat message, or a command when the text starts with `/`, as the player; the text is " +
                "trimmed, collapsed to single spaces and cut to 256 characters. Replies arrive later; read them with mc_read_messages.",
            inputSchema = schema(Property("text", "string", "Chat text or /command"), required = listOf("text")),
            annotations = action.copy(destructiveHint = true, openWorldHint = true),
        )
    }

    private fun typeText(): Tool {
        return Tool(
            name = "mc_type_text",
            description = "Type text into the focused widget of the open screen, such as the server address, a world name, an anvil rename box, " +
                "a search box, a sign or a command block. Focus a text field first with mc_click. For chat use mc_send_chat. " +
                "Returns how many characters the widget accepted; read the result with mc_read_screen_text.",
            inputSchema = schema(
                Property("text", "string", "Characters to type, at most ${ToolContract.MAX_TYPED_CHARS}"),
                required = listOf("text"),
            ),
            annotations = action,
        )
    }

    private fun pressKey(): Tool {
        return Tool(
            name = "mc_press_key",
            description = "Press and release one keyboard key on the open screen, for example enter to confirm, tab to move focus, " +
                "or backspace with `times` to empty a text field. Returns how many presses were handled. " +
                "With no screen open, escape opens the pause screen (mc_close_screen closes it). For movement use mc_set_key.",
            inputSchema = schema(
                Property("name", "string", "The key", allowed = ToolContract.SCREEN_KEYS),
                Property("times", "integer", "Presses, 1 to ${ToolContract.MAX_PRESSES}; default 1"),
                required = listOf("name"),
            ),
            annotations = action,
        )
    }
}
