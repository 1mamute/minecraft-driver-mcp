package io.github.ummamute.driver.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
    /** True when the endpoint needs `Authorization: Bearer <token>`. The token itself is never stored. */
    val authRequired: Boolean = false,
)

/**
 * Directory of running clients, one `<pid>.json` file each, so an agent (or a person) can find every client
 * without knowing which port it got. Files of crashed clients are ignored by [list] and removed on the next write.
 * A file that cannot be parsed may be another client's write in progress, so it is left alone.
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
        write(instance)
    }

    /** Writes to a temporary file first so a reader never sees a half-written `<pid>.json`. */
    private fun write(instance: DriverInstance) {
        val target = fileOf(instance.pid)
        val temporary = directory.resolve("${instance.pid}.json.tmp")
        temporary.writeText(json.encodeToString(DriverInstance.serializer(), instance))
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (exception: AtomicMoveNotSupportedException) {
            logger.debug("Atomic move not supported, replacing {} directly", target, exception)
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
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
            if (instance != null && !isAlive(instance.pid)) file.deleteIfExists()
        }
    }

    private fun files(): List<Path> = try {
        directory.listDirectoryEntries("*.json").filter { it.extension == "json" }
    } catch (exception: IOException) {
        logger.debug("Cannot list instance directory {}", directory, exception)
        emptyList()
    }

    private fun read(file: Path): DriverInstance? = try {
        json.decodeFromString(DriverInstance.serializer(), file.readText())
    } catch (exception: SerializationException) {
        logger.debug("Ignoring unreadable instance file {}", file, exception)
        null
    } catch (exception: IOException) {
        logger.debug("Ignoring instance file {} that vanished or cannot be read", file, exception)
        null
    }

    private fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    private fun fileOf(pid: Long): Path = directory.resolve("$pid.json")
}
