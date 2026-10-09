package io.github.ummamute.driver.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ToolArgumentsTest {
    private val arguments = ToolArguments(mapOf("name" to "Alex", "count" to 3L, "ratio" to 1.5, "flag" to true))

    @Test
    fun `reads typed values`() {
        assertEquals("Alex", arguments.getString("name"))
        assertEquals(3, arguments.getInt("count"))
        assertEquals(1.5, arguments.getDouble("ratio"))
        assertEquals(true, arguments.getBoolean("flag"))
    }

    @Test
    fun `returns null for a missing value or another type`() {
        assertNull(arguments.getString("count"))
        assertNull(arguments.getInt("ratio"))
        assertNull(arguments.getBoolean("missing"))
    }

    @Test
    fun `require functions explain which argument is wrong`() {
        val error = assertFailsWith<IllegalArgumentException> { arguments.requireInt("name") }

        assertEquals("Argument \"name\" is required and must be an integer", error.message)
    }

    @Test
    fun `a definition without a handler is rejected`() {
        assertFailsWith<IllegalArgumentException> { ToolDefinition.builder("mymod_read_score", "Reads").build() }
    }
}
