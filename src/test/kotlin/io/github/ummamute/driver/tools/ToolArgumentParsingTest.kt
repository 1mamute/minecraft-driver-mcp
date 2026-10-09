package io.github.ummamute.driver.tools

import io.github.ummamute.driver.tools.ToolSupport.boolean
import io.github.ummamute.driver.tools.ToolSupport.double
import io.github.ummamute.driver.tools.ToolSupport.int
import io.github.ummamute.driver.tools.ToolSupport.requireString
import io.github.ummamute.driver.tools.ToolSupport.string
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ToolArgumentParsingTest {
    private fun arguments(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>) = JsonObject(mapOf(*pairs))

    @Test
    fun `reads values of the right type`() {
        val args = arguments(
            "text" to JsonPrimitive("hi"),
            "count" to JsonPrimitive(3),
            "ratio" to JsonPrimitive(1.5),
            "flag" to JsonPrimitive(true),
        )

        assertEquals("hi", args.string("text"))
        assertEquals(3, args.int("count"))
        assertEquals(1.5, args.double("ratio"))
        assertEquals(true, args.boolean("flag"))
    }

    @Test
    fun `a missing argument and a JSON null are both absent`() {
        val args = arguments("text" to JsonNull, "count" to JsonNull)

        assertNull(args.string("text"))
        assertNull(args.int("count"))
        assertNull(args.boolean("missing"))
    }

    @Test
    fun `a required string rejects JSON null instead of reading the word null`() {
        val error = assertFailsWith<IllegalStateException> { arguments("text" to JsonNull).requireString("text") }

        assertEquals("Argument \"text\" is required", error.message)
    }

    @Test
    fun `a value of the wrong type names the argument and the expected type`() {
        val error = assertFailsWith<IllegalArgumentException> { arguments("count" to JsonPrimitive("many")).int("count") }

        assertEquals("Argument \"count\" must be an integer, got \"many\"", error.message)
        assertFailsWith<IllegalArgumentException> { arguments("flag" to JsonPrimitive("yes")).boolean("flag") }
        assertFailsWith<IllegalArgumentException> { arguments("text" to JsonPrimitive(5)).string("text") }
        assertFailsWith<IllegalArgumentException> { arguments("text" to buildJsonArray { }).string("text") }
    }
}
