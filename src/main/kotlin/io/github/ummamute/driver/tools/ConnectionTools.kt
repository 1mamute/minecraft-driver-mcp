package io.github.ummamute.driver.tools

import io.github.ummamute.driver.catalogue.BuiltInTools
import io.github.ummamute.driver.client.ClientConnection
import io.github.ummamute.driver.tools.ToolSupport.addGuardedTool
import io.github.ummamute.driver.tools.ToolSupport.arguments
import io.github.ummamute.driver.tools.ToolSupport.jsonResult
import io.github.ummamute.driver.tools.ToolSupport.onRenderThread
import io.github.ummamute.driver.tools.ToolSupport.requireString
import io.modelcontextprotocol.kotlin.sdk.server.Server

/** Tools that join and leave servers and worlds from the title screen. */
internal object ConnectionTools {

    fun register(server: Server) {
        registerJoinServer(server)
        registerJoinWorld(server)
        registerDisconnect(server)
    }

    private fun registerJoinServer(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_join_server"]) { request ->
            val target = try {
                ServerAddressParser.parse(arguments(request).requireString("address"))
            } catch (exception: IllegalStateException) {
                return@addGuardedTool ToolSupport.failure(exception.message ?: "Invalid address")
            }
            onRenderThread({ ClientConnection.joinServer(target.host, target.port) }) { jsonResult(it) }
        }
    }

    private fun registerJoinWorld(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_join_world"]) { request ->
            val folder = arguments(request).requireString("folder")
            onRenderThread({ ClientConnection.joinWorld(folder) }) { jsonResult(it) }
        }
    }

    private fun registerDisconnect(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_disconnect"]) { _ ->
            onRenderThread(ClientConnection::disconnect) { jsonResult(it) }
        }
    }
}
