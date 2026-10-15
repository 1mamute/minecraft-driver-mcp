package io.github.ummamute.driver.catalogue

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

internal object ConnectionDefinitions {
    private val join = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = true)

    val tools: List<Tool> = listOf(
        joinServer(),
        joinWorld(),
        disconnect(),
    )

    private fun joinServer(): Tool {
        return Tool(
            name = "mc_join_server",
            description = "Connect to a multiplayer server, like Direct Connection. `address` is host or host:port (default port 25565; " +
                "IPv6 as [::1]:25565). Returns at once: the connect screen is open and login finishes later, so follow with " +
                "mc_wait_for or mc_get_state. A failed connection leaves a disconnect screen; read it with mc_read_screen_text. " +
                "Fails if already in a world; call mc_disconnect first.",
            inputSchema = schema(Property("address", "string", "Server host, or host:port"), required = listOf("address")),
            annotations = join,
        )
    }

    private fun joinWorld(): Tool {
        return Tool(
            name = "mc_join_world",
            description = "Load a singleplayer world by its save folder name (the folder under saves/, not the display name). " +
                "Returns at once; the world loads over the next seconds, so follow with mc_wait_for or mc_get_state. " +
                "Fails if already in a world; call mc_disconnect first.",
            inputSchema = schema(Property("folder", "string", "Save folder name"), required = listOf("folder")),
            annotations = join.copy(openWorldHint = false),
        )
    }

    private fun disconnect(): Tool {
        return Tool(
            name = "mc_disconnect",
            description = "Leave the current world or server and return to the title screen. A singleplayer world is saved. " +
                "Does nothing on the title screen.",
            inputSchema = ToolSchema(),
            annotations = ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = true),
        )
    }
}
