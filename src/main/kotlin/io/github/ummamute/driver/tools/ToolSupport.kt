package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.RenderThread
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer

/** Shared pieces for tool definitions: input schemas, argument reading and result building. */
internal object ToolSupport {
    val json = Json { encodeDefaults = true }

    /** Builds an object schema. Each property is a name with its JSON type and description. */
    fun schema(vararg properties: Property, required: List<String> = emptyList()): ToolSchema = schema(properties.toList(), required)

    /** Builds an object schema from a list of properties. */
    fun schema(properties: List<Property>, required: List<String>): ToolSchema {
        val body = buildJsonObject {
            properties.forEach { property ->
                putJsonObject(property.name) {
                    put("type", property.type)
                    put("description", property.description)
                    property.allowed?.let { allowed -> put("enum", buildJsonArray { allowed.forEach { add(JsonPrimitive(it)) } }) }
                }
            }
        }
        return ToolSchema(properties = body, required = required.ifEmpty { null })
    }

    fun text(text: String): CallToolResult = CallToolResult(content = listOf<ContentBlock>(TextContent(text = text)))

    fun failure(message: String): CallToolResult = CallToolResult(content = listOf<ContentBlock>(TextContent(text = message)), isError = true)

    /** Runs a game call on the render thread and turns an exception or error into a result the agent can read. */
    suspend fun <T> onRenderThread(action: () -> T, toResult: (T) -> CallToolResult): CallToolResult {
        val outcome = runCatching { toResult(RenderThread.call(action)) }
        val problem = outcome.exceptionOrNull() ?: return outcome.getOrThrow()
        if (problem is VirtualMachineError) throw problem
        return failure(problem.message ?: problem.toString())
    }

    inline fun <reified T> jsonResult(value: T): CallToolResult = text(json.encodeToString(serializer<T>(), value))

    fun arguments(request: CallToolRequest): JsonObject = request.arguments ?: JsonObject(emptyMap())

    fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.content

    fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

    fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

    fun JsonObject.double(name: String): Double? = this[name]?.jsonPrimitive?.doubleOrNull

    fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

    fun JsonObject.requireDouble(name: String): Double = double(name) ?: error("Argument \"$name\" must be a number")

    fun JsonObject.requireString(name: String): String = string(name) ?: error("Argument \"$name\" is required")
}

/** One input property of a tool. */
internal data class Property(val name: String, val type: String, val description: String, val allowed: List<String>? = null)
