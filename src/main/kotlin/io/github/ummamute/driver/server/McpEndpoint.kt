package io.github.ummamute.driver.server

import io.github.ummamute.driver.api.ToolDefinition
import io.github.ummamute.driver.tools.ActionTools
import io.github.ummamute.driver.tools.ExtensionTools
import io.github.ummamute.driver.tools.InstanceTools
import io.github.ummamute.driver.tools.ObservationTools
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities

/** The MCP server on localhost. Stateless Streamable HTTP: every request is independent, so restarting the agent needs no session. */
class McpEndpoint(
    private val host: String,
    private val port: Int,
    private val version: String,
    private val instanceName: String,
    private val extensionTools: List<ToolDefinition> = emptyList(),
) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        val started = embeddedServer(CIO, host = host, port = port) {
            mcpStatelessStreamableHttp(path = PATH) { buildServer() }
        }
        started.start(wait = false)
        engine = started
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MILLIS)
        engine = null
    }

    private fun buildServer(): Server {
        val server = Server(
            serverInfo = Implementation(name = "$SERVER_NAME ($instanceName)", version = version),
            options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
            instructions = "This is the client \"$instanceName\". $INSTRUCTIONS",
        )
        InstanceTools.register(server)
        ObservationTools.register(server)
        ActionTools.register(server)
        ExtensionTools.register(server, extensionTools)
        return server
    }

    companion object {
        const val PATH = "/mcp"
        private const val SERVER_NAME = "minecraft-driver-mcp"
        private const val STOP_TIMEOUT_MILLIS = 1000L
        private const val INSTRUCTIONS =
            "Drives a running Minecraft client. Start with mc_get_state. Prefer text tools (mc_list_widgets, mc_read_messages) " +
                "over mc_screenshot. Screens and the player are only available after the client joined a world."
    }
}
