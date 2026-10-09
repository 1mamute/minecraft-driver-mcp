package io.github.ummamute.driver.tools

import io.github.ummamute.driver.api.ToolArguments
import io.github.ummamute.driver.api.ToolDefinition
import io.github.ummamute.driver.api.ToolResult
import io.github.ummamute.driver.client.RenderThread
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory

/** Adds the tools other mods registered through the extension API to the MCP server and runs their handlers safely. */
internal object ExtensionTools {
    private val logger = LoggerFactory.getLogger(ExtensionTools::class.java)

    fun register(server: Server, tools: List<ToolDefinition>) {
        tools.forEach { tool -> register(server, tool) }
    }

    private fun register(server: Server, tool: ToolDefinition) {
        val annotations = ToolAnnotations(
            readOnlyHint = tool.readOnly,
            destructiveHint = tool.destructive,
            idempotentHint = tool.idempotent,
            openWorldHint = tool.openWorld,
        )
        server.addTool(name = tool.name, description = tool.description, inputSchema = schemaOf(tool), toolAnnotations = annotations) { request ->
            run(tool, ToolArguments(toPlainMap(ToolSupport.arguments(request))))
        }
    }

    private fun schemaOf(tool: ToolDefinition) = ToolSupport.schema(
        properties = tool.properties.map { Property(it.name, it.type.json, it.description, it.allowed) },
        required = tool.properties.filter { it.required }.map { it.name },
    )

    /** Runs the handler where the tool asks and turns any failure, including an unexpected [Throwable], into an error result. */
    private suspend fun run(tool: ToolDefinition, arguments: ToolArguments): CallToolResult =
        if (tool.onRenderThread) RenderThread.call { invoke(tool, arguments) } else invoke(tool, arguments)

    private fun invoke(tool: ToolDefinition, arguments: ToolArguments): CallToolResult {
        val result = runCatching { tool.handler.handle(arguments) }.getOrElse { failure ->
            logger.warn("MCP tool {} failed", tool.name, failure)
            ToolResult.error("Tool ${tool.name} failed: ${failure.message ?: failure}. Check the arguments, then the game log.")
        }
        return if (result.isError) ToolSupport.failure(result.text) else ToolSupport.text(result.text)
    }

    /** Converts tool arguments to plain Kotlin values so extensions never see kotlinx.serialization types. */
    fun toPlainMap(arguments: JsonObject): Map<String, Any?> = arguments.mapValues { toPlain(it.value) }

    private fun toPlain(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> toPlainMap(element)
        is JsonArray -> element.map { toPlain(it) }
        is JsonPrimitive -> toPlain(element)
    }

    private fun toPlain(primitive: JsonPrimitive): Any? = when {
        primitive.isString -> primitive.content
        else -> primitive.booleanOrNull ?: primitive.longOrNull ?: primitive.doubleOrNull
    }
}
