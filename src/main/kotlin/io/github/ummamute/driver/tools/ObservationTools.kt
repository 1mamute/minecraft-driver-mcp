package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientScreens
import io.github.ummamute.driver.client.ClientState
import io.github.ummamute.driver.client.MessageLog
import io.github.ummamute.driver.client.Screenshots
import io.github.ummamute.driver.tools.ToolSupport.jsonResult
import io.github.ummamute.driver.tools.ToolSupport.long
import io.github.ummamute.driver.tools.ToolSupport.onRenderThread
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import java.util.Base64

/** Tools that read the client without changing it. */
internal object ObservationTools {
    private val readOnly = ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)

    fun register(server: Server) {
        registerState(server)
        registerScreen(server)
        registerMessages(server)
        registerEntities(server)
        registerScreenshot(server)
    }

    private fun registerState(server: Server) {
        server.addTool(
            name = "mc_get_state",
            description = "Get the open screen class, whether the client is connected to a server, window focus and the player position and rotation.",
            toolAnnotations = readOnly,
        ) { _ -> onRenderThread(ClientState::snapshot) { jsonResult(it) } }
    }

    private fun registerScreen(server: Server) {
        server.addTool(
            name = "mc_list_widgets",
            description = "List the clickable widgets of the open screen with index, label and bounds. Use the index or label with mc_click.",
            toolAnnotations = readOnly,
        ) { _ -> onRenderThread(ClientScreens::describe) { jsonResult(it) } }
    }

    private fun registerMessages(server: Server) {
        server.addTool(
            name = "mc_read_messages",
            description = "Read chat and system messages the client received after sequence number `since`. " +
                "Pass the returned `latest` as `since` next time to read only new messages.",
            inputSchema = ToolSupport.schema(Property("since", "integer", "Last seen sequence number. Defaults to 0 (everything buffered)")),
            toolAnnotations = readOnly,
        ) { request -> jsonResult(MessageLog.since(ToolSupport.arguments(request).long("since") ?: 0L)) }
    }

    private fun registerEntities(server: Server) {
        server.addTool(
            name = "mc_list_entities",
            description = "List entities within 64 blocks of the player, with id, type, name and position.",
            toolAnnotations = readOnly,
        ) { _ -> onRenderThread(ClientState::entitiesNearby) { jsonResult(it) } }
    }

    private fun registerScreenshot(server: Server) {
        server.addTool(
            name = "mc_screenshot",
            description = "Take a screenshot of the game framebuffer (not the desktop) and return it as a PNG image.",
            toolAnnotations = readOnly,
        ) { _ -> onRenderThread(Screenshots::capturePng, ::imageResult) }
    }

    private fun imageResult(png: ByteArray): CallToolResult =
        CallToolResult(content = listOf<ContentBlock>(ImageContent(data = Base64.getEncoder().encodeToString(png), mimeType = "image/png")))
}
