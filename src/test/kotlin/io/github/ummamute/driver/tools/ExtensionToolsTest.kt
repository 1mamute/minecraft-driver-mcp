package io.github.ummamute.driver.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionToolsTest {
    @Test
    fun `converts json arguments to plain values`() {
        val json = buildJsonObject {
            put("name", "Alex")
            put("count", 3)
            put("ratio", 1.5)
            put("flag", true)
            put("list", buildJsonArray { add(JsonPrimitive("a")) })
        }

        val plain = ExtensionTools.toPlainMap(json)

        assertEquals(mapOf("name" to "Alex", "count" to 3L, "ratio" to 1.5, "flag" to true, "list" to listOf("a")), plain)
    }
}
