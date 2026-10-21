package io.github.ummamute.driver.server

import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstanceRegistryTest {
    private val directory = Files.createTempDirectory("driver-registry")
    private val identities = mutableMapOf<Long, ProcessIdentity>()
    private val registry = InstanceRegistry(directory) { pid -> identities[pid] ?: ProcessIdentity(isAlive = true, startEpochSeconds = 100) }

    private fun instance(name: String, pid: Long, port: Int = 25890) =
        DriverInstance(name = name, pid = pid, url = "http://127.0.0.1:$port/mcp", port = port, minecraftVersion = "1.21.1", gameDirectory = "run")

    private fun write(instance: DriverInstance, fileName: String = "${instance.pid}.json") =
        directory.resolve(fileName).also { it.writeText(Json.encodeToString(DriverInstance.serializer(), instance)) }

    @Test
    fun `lists matching running clients ordered by port`() {
        registry.register(instance("two", 2, 25891))
        registry.register(instance("one", 1))

        assertEquals(listOf("one", "two"), registry.list().map { it.name })
        assertEquals(100L, registry.list().first().processStartEpochSeconds)
    }

    @Test
    fun `ignores dead clients and mismatched process starts without deleting on read`() {
        identities[2] = ProcessIdentity(isAlive = false)
        val dead = write(instance("dead", 2))
        val reused = write(instance("reused", 3).copy(processStartEpochSeconds = 99))

        assertEquals(emptyList(), registry.list())
        assertTrue(dead.exists())
        assertTrue(reused.exists())
    }

    @Test
    fun `preserves explicitly supplied process identity when registering`() {
        registry.register(instance("explicit", 1).copy(processStartEpochSeconds = 99))

        val written = Json.decodeFromString(DriverInstance.serializer(), directory.resolve("1.json").readText())
        assertEquals(99L, written.processStartEpochSeconds)
        assertEquals(emptyList(), registry.list())
    }

    @Test
    fun `legacy records remain visible without process start metadata`() {
        write(instance("legacy", 1))

        assertEquals(listOf("legacy"), registry.list().map { it.name })
        assertEquals(null, registry.list().single().processStartEpochSeconds)
    }

    @Test
    fun `unknown process metadata preserves records during listing and cleanup`() {
        identities[2] = ProcessIdentity()
        identities[3] = ProcessIdentity(isAlive = true)
        val unknown = write(instance("unknown", 2).copy(processStartEpochSeconds = 99))
        val unavailableStart = write(instance("no start", 3).copy(processStartEpochSeconds = 99))
        registry.register(instance("alive", 1))

        assertEquals(setOf("alive", "unknown", "no start"), registry.list().map { it.name }.toSet())
        assertTrue(unknown.exists())
        assertTrue(unavailableStart.exists())
    }

    @Test
    fun `registration without available process start remains discoverable`() {
        identities[1] = ProcessIdentity()
        registry.register(instance("unknown", 1))

        assertEquals(instance("unknown", 1), registry.list().single())
    }

    @Test
    fun `null process start metadata is treated as a legacy record`() {
        directory.resolve("1.json").writeText(
            """{"name":"legacy","pid":1,"url":"u","port":25890,"minecraftVersion":"1.21.1","gameDirectory":"g","processStartEpochSeconds":null}""",
        )

        assertEquals(listOf("legacy"), registry.list().map { it.name })
    }

    @Test
    fun `invalid identities names and filenames are ignored but preserved`() {
        val invalid = listOf(
            write(instance("zero", 0)),
            write(instance("negative", -1)),
            write(instance("mismatch", 1), "2.json"),
            write(instance("leading zero", 3), "03.json"),
            write(instance("alternate name", 4), "other.json"),
            write(instance("", 5)),
            write(instance("zero start", 6).copy(processStartEpochSeconds = 0)),
            write(instance("negative start", 7).copy(processStartEpochSeconds = -1)),
        )
        registry.register(instance("alive", 10))

        assertEquals(listOf("alive"), registry.list().map { it.name })
        assertTrue(invalid.all { it.exists() })
    }

    @Test
    fun `files without authRequired read as open and the flag round-trips`() {
        write(instance("old", 1))
        registry.register(instance("locked", 2, 25891).copy(authRequired = true))

        assertEquals(mapOf("old" to false, "locked" to true), registry.list().associate { it.name to it.authRequired })
    }

    @Test
    fun `discovery leaves endpoint safety checks to its callers`() {
        write(instance("remote", 1).copy(url = "http://example.test/mcp"))

        assertEquals("http://example.test/mcp", registry.list().single().url)
    }

    @Test
    fun `unregistered client disappears`() {
        registry.register(instance("gone", 1))
        registry.unregister(1)

        assertEquals(emptyList(), registry.list())
    }

    @Test
    fun `keeps a file it cannot parse`() {
        val partial = directory.resolve("4321.json")
        partial.writeText("""{"name":"half""")
        registry.register(instance("alive", 1))

        assertTrue(partial.exists())
        assertEquals(listOf("alive"), registry.list().map { it.name })
    }

    @Test
    fun `removes confirmed dead and mismatched records on register`() {
        identities[2] = ProcessIdentity(isAlive = false)
        val dead = write(instance("dead", 2))
        val reused = write(instance("reused", 3).copy(processStartEpochSeconds = 99))
        registry.register(instance("alive", 1))

        assertFalse(dead.exists())
        assertFalse(reused.exists())
    }

    @Test
    fun `tolerates a missing directory and a directory named like an instance file`() {
        val missing = InstanceRegistry(directory.resolve("missing"))
        Files.createDirectory(directory.resolve("2.json"))

        assertEquals(emptyList(), missing.list())
        assertEquals(emptyList(), registry.list())
        registry.register(instance("alive", 1))
        assertEquals(listOf("alive"), registry.list().map { it.name })
        assertTrue(Files.isDirectory(directory.resolve("2.json")))
    }

    @Test
    fun `register leaves no temporary file and replaces an earlier registration`() {
        registry.register(instance("first", 1))
        registry.register(instance("second", 1, 25891))

        assertEquals(listOf("second"), registry.list().map { it.name })
        assertEquals(listOf("1.json"), Files.list(directory).use { stream -> stream.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `default inspector round-trips the current process at whole seconds precision`() {
        val process = ProcessHandle.current()
        val registry = InstanceRegistry(directory)
        registry.register(instance("host", process.pid()))

        val registered = registry.list().single()
        assertEquals(process.pid(), registered.pid)
        assertEquals(process.info().startInstant().orElseThrow().epochSecond, registered.processStartEpochSeconds)
    }
}
