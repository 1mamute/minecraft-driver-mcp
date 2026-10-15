package io.github.ummamute.driver.catalogue

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class BuiltInToolsTest {
    @Test
    fun `definitions preserve the original built-in catalogue`() {
        // Captured from dce6e59's original registrations before extraction, including resolved client constants.
        val fixture = assertNotNull(javaClass.getResource("/built-in-tools.json")).readText()
        val original = Json.decodeFromString<List<Tool>>(fixture)
        assertEquals(original, BuiltInTools.all)
    }

    @Test
    fun `every definition can be looked up by its unique name`() {
        val definitions = BuiltInTools.all
        assertEquals(definitions.size, definitions.map(Tool::name).toSet().size)
        definitions.forEach { definition -> assertSame(definition, BuiltInTools[definition.name]) }
    }

    @Test
    fun `unknown names cannot silently register a built-in handler`() {
        assertFailsWith<IllegalArgumentException> { BuiltInTools["extension_read_state"] }
    }

    @Test
    fun `catalogue loads with no game or server on its classpath`() {
        val loader = javaClass.classLoader
        listOf(
            "net.minecraft.client.Minecraft",
            "net.fabricmc.loader.api.FabricLoader",
            "io.modelcontextprotocol.kotlin.sdk.server.Server",
            "io.github.ummamute.driver.DriverBootstrap",
            "io.github.ummamute.driver.tools.ObservationTools",
        ).forEach { name -> assertFailsWith<ClassNotFoundException>(name) { loader.loadClass(name) } }
        assertEquals(23, BuiltInTools.all.size)
    }
}
