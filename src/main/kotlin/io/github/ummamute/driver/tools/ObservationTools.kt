package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientScreens
import io.github.ummamute.driver.client.ClientState
import io.github.ummamute.driver.client.MessageLog
import io.github.ummamute.driver.client.RenderThread
import io.github.ummamute.driver.client.ScreenText
import io.github.ummamute.driver.client.Screenshots
import io.github.ummamute.driver.tools.ToolSupport.jsonResult
import io.github.ummamute.driver.tools.ToolSupport.long
import io.github.ummamute.driver.tools.ToolSupport.onRenderThread
import io.github.ummamute.driver.tools.ToolSupport.string
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import java.util.Base64

/** Tools that read the client without changing it. */
internal object ObservationTools {
    private const val DEFAULT_WAIT_MILLIS = 10_000L
    private const val MAX_WAIT_MILLIS = 60_000L
    private val readOnly = ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)

    fun register(server: Server) {
        registerState(server)
        registerScreen(server)
        registerScreenText(server)
        registerMessages(server)
        registerWaitFor(server)
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

    private fun registerScreenText(server: Server) {
        server.addTool(
            name = "mc_read_screen_text",
            description = "Read the text the open screen drew in its last frame: the title, labels, body text and any tooltip showing, " +
                "with coordinates. Includes text that mc_list_widgets cannot see. A tooltip appears only while the cursor hovers its target.",
            toolAnnotations = readOnly,
        ) { _ -> onRenderThread(ScreenText::snapshot) { jsonResult(it) } }
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

    private fun registerWaitFor(server: Server) {
        server.addTool(
            name = "mc_wait_for",
            description = "Block until a chat or system message contains `message`, or the open screen's class name contains `screen` " +
                "(use `none` to wait for no screen), whichever happens first. Returns `matched` false with reason `timeout` when `timeout_ms` passes. " +
                "Pass `since` (a `latest` from mc_read_messages) to also see messages that arrived before this call; " +
                "by default only newer messages count. Keep `timeout_ms` below your MCP client's request timeout.",
            inputSchema = ToolSupport.schema(
                Property("message", "string", "Text a message must contain, ignoring case"),
                Property("screen", "string", "Text the screen class name must contain, ignoring case, or `none` for no screen"),
                Property("since", "integer", "Only messages with a higher sequence number count. Defaults to the latest at call time"),
                Property("timeout_ms", "integer", "How long to wait. Defaults to $DEFAULT_WAIT_MILLIS, at most $MAX_WAIT_MILLIS"),
            ),
            toolAnnotations = readOnly,
        ) { request ->
            val args = ToolSupport.arguments(request)
            val message = args.string("message")
            val screen = args.string("screen")
            if (message == null && screen == null) {
                return@addTool ToolSupport.failure("Give `message`, `screen` or both. Example: {\"message\": \"Teleported\"} or {\"screen\": \"none\"}")
            }
            val since = args.long("since") ?: MessageLog.latest()
            val timeout = (args.long("timeout_ms") ?: DEFAULT_WAIT_MILLIS).coerceIn(1L, MAX_WAIT_MILLIS)
            val waiter = ConditionWaiter(readState = { RenderThread.call(ClientState::snapshot) }, readMessages = MessageLog::since)
            jsonResult(waiter.await(WaitCondition(messageContains = message, screenContains = screen, sinceSeq = since), timeout))
        }
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
