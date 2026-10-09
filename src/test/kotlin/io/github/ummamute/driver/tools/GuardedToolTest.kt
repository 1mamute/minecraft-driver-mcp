package io.github.ummamute.driver.tools

import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GuardedToolTest {
    private val request = CallToolRequest(CallToolRequestParams(name = "mc_test"))

    private fun call(problem: Throwable): CallToolResult = runBlocking { ToolSupport.callGuarded({ throw problem }, request) }

    private fun textOf(result: CallToolResult): String = (result.content.single() as TextContent).text

    @Test
    fun `reports a bad argument as a plain error`() {
        val result = call(IllegalArgumentException("Argument \"x\" must be an integer"))

        assertEquals(true, result.isError)
        assertEquals("Argument \"x\" must be an integer", textOf(result))
    }

    @Test
    fun `reports a linkage error instead of failing the request`() {
        val result = call(NoSuchMethodError("Screen.foo"))

        assertEquals(true, result.isError)
        assertTrue(textOf(result).contains("NoSuchMethodError: Screen.foo"))
    }

    @Test
    fun `keeps a cancelled call cancelled`() {
        assertFailsWith<CancellationException> { call(CancellationException("cancelled")) }
    }

    @Test
    fun `rethrows fatal errors`() {
        assertFailsWith<OutOfMemoryError> { call(OutOfMemoryError("heap")) }
    }

    @Test
    fun `leaves other exceptions to the SDK`() {
        assertFailsWith<UnsupportedOperationException> { call(UnsupportedOperationException("nope")) }
    }
}
