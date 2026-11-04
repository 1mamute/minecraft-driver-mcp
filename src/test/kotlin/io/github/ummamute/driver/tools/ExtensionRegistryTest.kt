package io.github.ummamute.driver.tools

import io.github.ummamute.driver.api.ToolDefinition
import io.github.ummamute.driver.api.ToolResult
import java.util.Collections
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
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

    @Test
    fun `a subscriber receives the tools registered before and after it subscribed`() {
        val registrar = registry.registrarFor("mymod")
        registrar.register(tool("mymod_read_early"))
        val received = mutableListOf<String>()

        registry.subscribe { received += it.name }
        registrar.register(tool("mymod_read_late"))

        assertEquals(listOf("mymod_read_early", "mymod_read_late"), received)
    }

    @Test
    fun `a subscriber does not receive rejected tools`() {
        val registrar = registry.registrarFor("mymod")
        val received = mutableListOf<String>()
        registry.subscribe { received += it.name }

        registrar.register(tool("mymod_read_score"))
        registrar.register(tool("mymod_read_score"))
        registrar.register(tool("score"))

        assertEquals(listOf("mymod_read_score"), received)
    }

    @Test
    fun `a closed subscription receives nothing more`() {
        val registrar = registry.registrarFor("mymod")
        val received = mutableListOf<String>()
        val subscription = registry.subscribe { received += it.name }

        subscription.close()
        registrar.register(tool("mymod_read_late"))

        assertTrue(received.isEmpty())
        assertEquals(listOf("mymod_read_late"), registry.tools.map { it.name })
    }

    @Test
    fun `a failing subscriber keeps the tool registered and the other subscribers informed`() {
        val received = mutableListOf<String>()
        registry.subscribe { error("endpoint refused") }
        registry.subscribe { received += it.name }

        assertTrue(registry.registrarFor("mymod").register(tool("mymod_read_score")))

        assertEquals(listOf("mymod_read_score"), received)
        assertEquals(listOf("mymod_read_score"), registry.tools.map { it.name })
    }

    @Test
    fun `tools registered from many threads while subscribing reach the subscriber exactly once`() {
        val names = (0 until 200).map { "mymod_read_value$it" }
        val received = Collections.synchronizedList(mutableListOf<String>())
        val start = CountDownLatch(1)
        val workers = names.chunked(50).map { chunk ->
            thread {
                val registrar = registry.registrarFor("mymod")
                start.await()
                chunk.forEach { registrar.register(tool(it)) }
            }
        }

        start.countDown()
        registry.subscribe { received += it.name }
        workers.forEach { it.join() }

        assertEquals(names.sorted(), received.sorted())
    }
}
