package io.github.ummamute.driver.tools

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class BuiltInToolsTest {
    @Test
    fun `bundled gateway definitions preserve the direct endpoint contract`() {
        // Captured before catalogue extraction, including resolved client constants.
        val fixture = assertNotNull(javaClass.getResource("/built-in-tools.json")).readText()
        assertEquals(Json.decodeFromString<List<Tool>>(fixture), BuiltInTools.all)
    }

    @Test
    fun `every bundled definition has a unique name and can be looked up`() {
        assertEquals(BuiltInTools.all.size, BuiltInTools.all.map(Tool::name).toSet().size)
        BuiltInTools.all.forEach { definition -> assertSame(definition, BuiltInTools[definition.name]) }
    }

    @Test
    fun `unknown names cannot silently register a built-in handler`() {
        assertFailsWith<IllegalArgumentException> { BuiltInTools["extension_read_state"] }
    }

    @Test
    fun `bundled catalogue release matches the mod release`() {
        val metadata = assertNotNull(javaClass.getResource("/fabric.mod.json")).readText()
        val modVersion = Json.parseToJsonElement(metadata).jsonObject.getValue("version").jsonPrimitive.content
        assertEquals(modVersion.substringBefore('+'), BuiltInTools.version)
    }
}
