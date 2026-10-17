package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientEffects
import io.github.ummamute.driver.client.ClientInventory
import io.github.ummamute.driver.client.ClientScreens
import io.github.ummamute.driver.client.ClientState
import io.github.ummamute.driver.client.ClientTarget
import io.github.ummamute.driver.client.LogBuffer
import io.github.ummamute.driver.client.LogCapture
import io.github.ummamute.driver.client.LogQuery
import io.github.ummamute.driver.client.MessageLog
import io.github.ummamute.driver.client.RenderThread
import io.github.ummamute.driver.client.ScreenText
import io.github.ummamute.driver.client.Screenshots
import io.github.ummamute.driver.tools.ToolSupport.addGuardedTool
import io.github.ummamute.driver.tools.ToolSupport.boolean
import io.github.ummamute.driver.tools.ToolSupport.int
import io.github.ummamute.driver.tools.ToolSupport.jsonResult
import io.github.ummamute.driver.tools.ToolSupport.long
import io.github.ummamute.driver.tools.ToolSupport.onRenderThread
import io.github.ummamute.driver.tools.ToolSupport.string
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tools that read the client without changing it. */
internal object ObservationTools {
    private const val DEFAULT_WAIT_MILLIS = 10_000L
    private const val MAX_WAIT_MILLIS = 60_000L
    private const val DEFAULT_LOG_LEVEL = "INFO"

    fun register(server: Server, instanceName: String, screenshotEvidence: ScreenshotEvidence = ScreenshotEvidence(null)) {
        registerState(server)
        registerTarget(server)
        registerEffects(server)
        registerScreen(server)
        registerScreenText(server)
        registerInventory(server)
        registerContainer(server)
        registerMessages(server)
        registerLog(server)
        registerWaitFor(server)
        registerEntities(server)
        registerScreenshot(server, instanceName, screenshotEvidence)
    }

    private fun registerState(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_get_state"]) { _ -> onRenderThread(ClientState::snapshot) { jsonResult(it) } }
    }

    private fun registerTarget(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_get_target"]) { _ -> onRenderThread(ClientTarget::snapshot) { jsonResult(it) } }
    }

    private fun registerScreen(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_list_widgets"]) { _ -> onRenderThread(ClientScreens::describe) { jsonResult(it) } }
    }

    private fun registerEffects(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_read_effects"]) { _ -> onRenderThread(ClientEffects::snapshot) { jsonResult(it) } }
    }

    private fun registerScreenText(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_read_screen_text"]) { _ -> onRenderThread(ScreenText::snapshot) { jsonResult(it) } }
    }

    private fun registerInventory(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_read_inventory"]) { request ->
            val includeEmpty = ToolSupport.arguments(request).boolean("include_empty") ?: false
            onRenderThread({ ClientInventory.inventory(includeEmpty) }) { jsonResult(it) }
        }
    }

    private fun registerContainer(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_read_container"]) { request ->
            val includeEmpty = ToolSupport.arguments(request).boolean("include_empty") ?: false
            onRenderThread({ ClientInventory.container(includeEmpty) }) { jsonResult(it) }
        }
    }

    private fun registerMessages(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_read_messages"]) { request -> jsonResult(MessageLog.since(ToolSupport.arguments(request).long("since") ?: 0L)) }
    }

    private fun registerLog(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_read_log"]) { request ->
            val args = ToolSupport.arguments(request)
            val level = args.string("min_level")
            if (level != null && !LogBuffer.isLevel(level)) {
                return@addGuardedTool ToolSupport.failure("Unknown min_level \"$level\". Use one of ${LogBuffer.LEVELS.joinToString()}")
            }
            val query = LogQuery(
                since = args.long("since") ?: 0L,
                minLevel = level ?: DEFAULT_LOG_LEVEL,
                contains = args.string("contains"),
                limit = args.int("max_lines") ?: LogBuffer.DEFAULT_LIMIT,
            )
            jsonResult(LogCapture.buffer.read(query))
        }
    }

    private fun registerWaitFor(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_wait_for"]) { request ->
            val args = ToolSupport.arguments(request)
            val message = args.string("message")
            val screen = args.string("screen")
            if (message == null && screen == null) {
                return@addGuardedTool ToolSupport.failure("Give `message`, `screen` or both. Example: {\"message\": \"Teleported\"} or {\"screen\": \"none\"}")
            }
            val since = args.long("since") ?: MessageLog.latest()
            val timeout = (args.long("timeout_ms") ?: DEFAULT_WAIT_MILLIS).coerceIn(1L, MAX_WAIT_MILLIS)
            val waiter = ConditionWaiter(readState = { RenderThread.call(ClientState::snapshot) }, readMessages = MessageLog::since)
            jsonResult(waiter.await(WaitCondition(messageContains = message, screenContains = screen, sinceSeq = since), timeout))
        }
    }

    private fun registerEntities(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_list_entities"]) { _ -> onRenderThread(ClientState::entitiesNearby) { jsonResult(it) } }
    }

    private fun registerScreenshot(server: Server, instanceName: String, evidence: ScreenshotEvidence) {
        server.addGuardedTool(BuiltInTools["mc_screenshot"]) { request ->
            val args = ToolSupport.arguments(request)
            val saveAs = args.string("save_as")
            if (saveAs != null) evidence.validate(saveAs)
            val options = ScreenshotOptions(
                x = args.int("x"),
                y = args.int("y"),
                width = args.int("width"),
                height = args.int("height"),
                maxWidth = args.int("max_width"),
            )
            val capture = RenderThread.call { Screenshots.capture(instanceName) }
            val image = withContext(Dispatchers.Default) {
                val framebuffer = capture.metadata.framebuffer
                ScreenshotTransform.apply(capture.png, framebuffer.width, framebuffer.height, options)
            }
            val metadata = image.metadata(capture.metadata)
            val paths = if (saveAs == null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    evidence.save(image.png, ToolSupport.json.encodeToString(metadata), saveAs)
                }
            }
            ScreenshotResults.inline(image.png, metadata.copy(evidence = paths))
        }
    }
}
