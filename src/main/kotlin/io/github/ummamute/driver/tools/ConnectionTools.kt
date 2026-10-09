package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientConnection
import io.github.ummamute.driver.tools.ToolSupport.addGuardedTool
import io.github.ummamute.driver.tools.ToolSupport.arguments
import io.github.ummamute.driver.tools.ToolSupport.jsonResult
import io.github.ummamute.driver.tools.ToolSupport.onRenderThread
import io.github.ummamute.driver.tools.ToolSupport.requireString
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations

/** Tools that join and leave servers and worlds from the title screen. */
internal object ConnectionTools {
    private val join = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = true)

    fun register(server: Server) {
        registerJoinServer(server)
        registerJoinWorld(server)
        registerDisconnect(server)
    }

    private fun registerJoinServer(server: Server) {
        server.addGuardedTool(
            name = "mc_join_server",
            description = "Connect to a multiplayer server, like Direct Connection. `address` is host or host:port (default port 25565; " +
                "IPv6 as [::1]:25565). Returns at once: the connect screen is open and login finishes later, so follow with " +
                "mc_wait_for or mc_get_state. A failed connection leaves a disconnect screen; read it with mc_read_screen_text. " +
                "Fails if already in a world; call mc_disconnect first.",
            inputSchema = ToolSupport.schema(Property("address", "string", "Server host, or host:port"), required = listOf("address")),
            toolAnnotations = join,
        ) { request ->
            val target = try {
                ServerAddressParser.parse(arguments(request).requireString("address"))
            } catch (exception: IllegalStateException) {
                return@addGuardedTool ToolSupport.failure(exception.message ?: "Invalid address")
            }
            onRenderThread({ ClientConnection.joinServer(target.host, target.port) }) { jsonResult(it) }
        }
    }

    private fun registerJoinWorld(server: Server) {
        server.addGuardedTool(
            name = "mc_join_world",
            description = "Load a singleplayer world by its save folder name (the folder under saves/, not the display name). " +
                "Returns at once; the world loads over the next seconds, so follow with mc_wait_for or mc_get_state. " +
                "Fails if already in a world; call mc_disconnect first.",
            inputSchema = ToolSupport.schema(Property("folder", "string", "Save folder name"), required = listOf("folder")),
            toolAnnotations = join.copy(openWorldHint = false),
        ) { request ->
            val folder = arguments(request).requireString("folder")
            onRenderThread({ ClientConnection.joinWorld(folder) }) { jsonResult(it) }
        }
    }

    private fun registerDisconnect(server: Server) {
        server.addGuardedTool(
            name = "mc_disconnect",
            description = "Leave the current world or server and return to the title screen. A singleplayer world is saved. " +
                "Does nothing on the title screen.",
            toolAnnotations = ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true, openWorldHint = true),
        ) { _ ->
            onRenderThread(ClientConnection::disconnect) { jsonResult(it) }
        }
    }
}
