package io.github.ummamute.driver.server

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class InstanceRegistryTest {
    private val directory = Files.createTempDirectory("driver-registry")
    private val registry = InstanceRegistry(directory)

    private fun instance(name: String, pid: Long, port: Int) =
        DriverInstance(name = name, pid = pid, url = "http://127.0.0.1:$port/mcp", port = port, minecraftVersion = "1.21.1", gameDirectory = "run")

    @Test
    fun `lists running clients ordered by port`() {
        val self = ProcessHandle.current().pid()
        registry.register(instance("two", self, 25891))
        val other = """{"name":"one","pid":$self,"url":"u","port":25890,"minecraftVersion":"1.21.1","gameDirectory":"g"}"""
        Files.writeString(directory.resolve("other.json"), other)

        assertEquals(listOf("one", "two"), registry.list().map { it.name })
    }

    @Test
    fun `ignores clients whose process is gone`() {
        registry.register(instance("alive", ProcessHandle.current().pid(), 25890))
        directory.resolve("dead.json").writeText("""{"name":"dead","pid":999999999,"url":"u","port":25891,"minecraftVersion":"1.21.1","gameDirectory":"g"}""")

        assertEquals(listOf("alive"), registry.list().map { it.name })
    }

    @Test
    fun `files without authRequired read as open and the flag round-trips`() {
        val self = ProcessHandle.current().pid()
        directory.resolve("old.json").writeText("""{"name":"old","pid":$self,"url":"u","port":25890,"minecraftVersion":"1.21.1","gameDirectory":"g"}""")
        registry.register(instance("locked", self, 25891).copy(authRequired = true))

        assertEquals(mapOf("old" to false, "locked" to true), registry.list().associate { it.name to it.authRequired })
    }

    @Test
    fun `unregistered client disappears`() {
        val self = ProcessHandle.current().pid()
        registry.register(instance("gone", self, 25890))
        registry.unregister(self)

        assertEquals(emptyList(), registry.list())
    }
}
