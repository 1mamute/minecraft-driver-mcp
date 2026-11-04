package io.github.ummamute.driver.server

import io.github.ummamute.driver.api.ToolDefinition
import io.github.ummamute.driver.api.ToolResult
import io.github.ummamute.driver.tools.ExtensionRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpEndpointExtensionTest {
    private val host = "127.0.0.1"
    private val registry = ExtensionRegistry()
    private val registrar = registry.registrarFor("mymod")
    private val http = HttpClient.newHttpClient()
    private var requestId = 0

    private val bound = EndpointBinder.bind(null, host) { port ->
        McpEndpoint(host, port, "test", "test", registry).also { it.start() }
    }

    @AfterTest
    fun stop() {
        bound.endpoint.stop()
    }

    private fun tool(name: String) = ToolDefinition.builder(name, "Test tool")
        .readOnly(true).destructive(false).idempotent(true).openWorld(false)
        .onRenderThread(false)
        .handler { ToolResult.text("{\"tool\": \"$name\"}") }
        .build()

    @Test
    fun `a tool registered after the endpoint answered appears in the next tools list`() {
        registrar.register(tool("mymod_read_early"))
        assertEquals(listOf("mymod_read_early"), extensionToolNames())

        assertTrue(registrar.register(tool("mymod_read_late")))

        assertEquals(listOf("mymod_read_early", "mymod_read_late"), extensionToolNames())
    }

    @Test
    fun `a tool registered late can be called`() {
        extensionToolNames()
        registrar.register(tool("mymod_read_late"))

        val result = call("tools/call", """{"name": "mymod_read_late", "arguments": {}}""")

        assertFalse(result["isError"]?.jsonPrimitive?.content.toBoolean())
        assertEquals("{\"tool\": \"mymod_read_late\"}", result.getValue("content").jsonArray[0].jsonObject.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `a late duplicate is rejected and the list keeps the first tool once`() {
        registrar.register(tool("mymod_read_score"))
        extensionToolNames()

        assertFalse(registrar.register(tool("mymod_read_score")))

        assertEquals(listOf("mymod_read_score"), extensionToolNames())
    }

    @Test
    fun `the endpoint does not announce list changes it cannot push`() {
        val result = call("initialize", """{"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}}""")

        val tools = result.getValue("capabilities").jsonObject.getValue("tools").jsonObject
        assertEquals("false", tools["listChanged"]?.jsonPrimitive?.content ?: "false")
    }

    private fun extensionToolNames(): List<String> = call("tools/list", "{}").getValue("tools").jsonArray
        .map { it.jsonObject.getValue("name").jsonPrimitive.content }
        .filter { it.startsWith("mymod_") }

    private fun call(method: String, params: String): JsonObject {
        val body = """{"jsonrpc": "2.0", "id": ${++requestId}, "method": "$method", "params": $params}"""
        val request = HttpRequest.newBuilder(URI.create("http://$host:${bound.port}${McpEndpoint.PATH}"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), response.body())
        val message = Json.parseToJsonElement(jsonPayload(response)).jsonObject
        return message.getValue("result").jsonObject
    }

    /** The transport answers with plain JSON or with one server-sent event carrying it. */
    private fun jsonPayload(response: HttpResponse<String>): String {
        val isEventStream = response.headers().firstValue("Content-Type").orElse("").contains("text/event-stream")
        if (!isEventStream) return response.body()
        return response.body().lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()
    }
}
