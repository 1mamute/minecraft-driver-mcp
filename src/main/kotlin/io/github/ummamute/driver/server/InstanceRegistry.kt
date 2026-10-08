package io.github.ummamute.driver.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** One running client and where its MCP server listens. */
@Serializable
data class DriverInstance(
    val name: String,
    val pid: Long,
    val url: String,
    val port: Int,
    val minecraftVersion: String,
    val gameDirectory: String,
)

/**
 * Directory of running clients, one `<pid>.json` file each, so an agent (or a person) can find every client
 * without knowing which port it got. Files of crashed clients are ignored by [list] and removed on the next write.
 */
class InstanceRegistry(private val directory: Path) {
    private val logger = LoggerFactory.getLogger(InstanceRegistry::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun register(instance: DriverInstance) {
        Files.createDirectories(directory)
        removeStale()
        fileOf(instance.pid).writeText(json.encodeToString(DriverInstance.serializer(), instance))
    }

    fun unregister(pid: Long) {
        fileOf(pid).deleteIfExists()
    }

    /** Running clients only, ordered by port. */
    fun list(): List<DriverInstance> {
        if (!Files.isDirectory(directory)) return emptyList()
        return files().mapNotNull(::read).filter { isAlive(it.pid) }.sortedBy { it.port }
    }

    private fun removeStale() {
        files().forEach { file ->
            val instance = read(file)
            if (instance == null || !isAlive(instance.pid)) file.deleteIfExists()
        }
    }

    private fun files(): List<Path> = directory.listDirectoryEntries("*.json").filter { it.extension == "json" }

    private fun read(file: Path): DriverInstance? = try {
        json.decodeFromString(DriverInstance.serializer(), file.readText())
    } catch (exception: SerializationException) {
        logger.debug("Ignoring unreadable instance file {}", file, exception)
        null
    }

    private fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    private fun fileOf(pid: Long): Path = directory.resolve("$pid.json")
}
