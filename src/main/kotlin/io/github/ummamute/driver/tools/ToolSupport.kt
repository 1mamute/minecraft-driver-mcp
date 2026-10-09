package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.RenderThread
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/** Shared pieces for tool definitions: input schemas, argument reading and result building. */
internal object ToolSupport {
    private val logger = LoggerFactory.getLogger(ToolSupport::class.java)

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

    /**
     * Registers a tool whose bad-input failures (`IllegalArgumentException`, `IllegalStateException`) become plain `isError` results.
     * Without it the SDK reports them as `Error executing tool <name>: ...` and logs them at ERROR with a stack trace,
     * although they are mistakes in the call and not faults in the driver. An `Error` other than a `VirtualMachineError` is logged
     * and also becomes an `isError` result, because the SDK catches only `Exception`.
     */
    fun Server.addGuardedTool(
        name: String,
        description: String,
        inputSchema: ToolSchema = ToolSchema(),
        toolAnnotations: ToolAnnotations? = null,
        handler: suspend (CallToolRequest) -> CallToolResult,
    ) {
        addTool(name = name, description = description, inputSchema = inputSchema, toolAnnotations = toolAnnotations) { request ->
            callGuarded(handler, request)
        }
    }

    /** Runs a tool handler and turns bad-input exceptions and non-fatal errors into `isError` results. */
    suspend fun callGuarded(handler: suspend (CallToolRequest) -> CallToolResult, request: CallToolRequest): CallToolResult {
        val outcome = runCatching { handler(request) }
        val problem = outcome.exceptionOrNull() ?: return outcome.getOrThrow()
        return when (problem) {
            // CancellationException extends IllegalStateException; a cancelled call must stay cancelled.
            is CancellationException, is VirtualMachineError -> throw problem
            is IllegalArgumentException, is IllegalStateException -> failure(problem.message ?: problem.toString())
            is Error -> reportError(request, problem)
            else -> throw problem
        }
    }

    /** An `Error` such as a `LinkageError` escapes the SDK's catch, which handles only `Exception`, and would fail the whole request. */
    private fun reportError(request: CallToolRequest, error: Error): CallToolResult {
        logger.error("Tool {} failed", request.name, error)
        return failure("The tool failed inside the driver: $error. Check the game log; the client keeps running")
    }

    fun failure(message: String): CallToolResult = CallToolResult(content = listOf<ContentBlock>(TextContent(text = message)), isError = true)

    /** Runs a game call on the render thread and turns an exception or error into a result the agent can read. */
    suspend fun <T> onRenderThread(action: () -> T, toResult: (T) -> CallToolResult): CallToolResult {
        val outcome = runCatching { toResult(RenderThread.call(action)) }
        val problem = outcome.exceptionOrNull() ?: return outcome.getOrThrow()
        if (problem is VirtualMachineError || problem is CancellationException) throw problem
        return failure(problem.message ?: problem.toString())
    }

    inline fun <reified T> jsonResult(value: T): CallToolResult = text(json.encodeToString(serializer<T>(), value))

    fun arguments(request: CallToolRequest): JsonObject = request.arguments ?: JsonObject(emptyMap())

    /** A missing argument and an explicit JSON `null` both mean "not given"; a present value of the wrong type is an error. */
    private fun <T : Any> JsonObject.typed(name: String, expected: String, parse: (JsonPrimitive) -> T?): T? {
        val value = this[name]
        if (value == null || value is JsonNull) return null
        val primitive = value as? JsonPrimitive
        return primitive?.let(parse) ?: throw IllegalArgumentException("Argument \"$name\" must be $expected, got $value")
    }

    fun JsonObject.string(name: String): String? = typed(name, "a string") { it.takeIf(JsonPrimitive::isString)?.content }

    fun JsonObject.int(name: String): Int? = typed(name, "an integer") { it.intOrNull }

    fun JsonObject.long(name: String): Long? = typed(name, "an integer") { it.longOrNull }

    fun JsonObject.double(name: String): Double? = typed(name, "a number") { it.doubleOrNull }

    fun JsonObject.boolean(name: String): Boolean? = typed(name, "true or false") { it.booleanOrNull }

    fun JsonObject.requireDouble(name: String): Double = double(name) ?: error("Argument \"$name\" must be a number")

    fun JsonObject.requireString(name: String): String = string(name) ?: error("Argument \"$name\" is required")
}

/** One input property of a tool. */
internal data class Property(val name: String, val type: String, val description: String, val allowed: List<String>? = null)
