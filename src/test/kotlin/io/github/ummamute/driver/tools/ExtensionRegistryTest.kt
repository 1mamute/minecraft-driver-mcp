package io.github.ummamute.driver.tools

import io.github.ummamute.driver.api.ToolDefinition
import io.github.ummamute.driver.api.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionRegistryTest {
    private val registry = ExtensionRegistry()

    private fun tool(name: String) = ToolDefinition.builder(name, "Test tool").handler { ToolResult.text("ok") }.build()

    @Test
    fun `keeps valid tools in registration order`() {
        val registrar = registry.registrarFor("mymod")

        assertTrue(registrar.register(tool("mymod_read_score")))
        assertTrue(registrar.register(tool("mymod_list_players")))

        assertEquals(listOf("mymod_read_score", "mymod_list_players"), registry.tools.map { it.name })
    }

    @Test
    fun `rejects an invalid name and keeps the others`() {
        val registrar = registry.registrarFor("mymod")

        assertFalse(registrar.register(tool("score")))
        assertTrue(registrar.register(tool("mymod_read_score")))

        assertEquals(listOf("mymod_read_score"), registry.tools.map { it.name })
    }

    @Test
    fun `rejects a duplicate from the same mod and keeps the first`() {
        val registrar = registry.registrarFor("mymod")
        val first = tool("mymod_read_score")

        assertTrue(registrar.register(first))
        assertFalse(registrar.register(tool("mymod_read_score")))

        assertEquals(listOf(first), registry.tools)
    }

    @Test
    fun `rejects a name that another mod already owns`() {
        // The ids "my-mod" and "my_mod" share the prefix "my_mod", so the second one hits the name the first owns.
        assertTrue(registry.registrarFor("my-mod").register(tool("my_mod_read_score")))
        assertFalse(registry.registrarFor("my_mod").register(tool("my_mod_read_score")))

        assertEquals(1, registry.tools.size)
    }

    @Test
    fun `a mod cannot register under another mod's prefix`() {
        assertFalse(registry.registrarFor("evil").register(tool("mymod_read_score")))
        assertTrue(registry.tools.isEmpty())
    }

    @Test
    fun `the registrar reports its mod id`() {
        assertEquals("mymod", registry.registrarFor("mymod").modId)
    }
}
