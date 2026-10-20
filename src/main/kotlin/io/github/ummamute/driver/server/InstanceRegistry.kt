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
    /** Process start time in positive whole Unix epoch seconds, when the operating system provides it. */
    val processStartEpochSeconds: Long? = null,
)

/**
 * Directory of running clients, one `<pid>.json` file each, so an agent (or a person) can find every client
 * without knowing which port it got. Files of crashed clients are ignored by [list] and removed on the next write.
 * A file that cannot be parsed may be another client's write in progress, so it is left alone.
 */
class InstanceRegistry(
    private val directory: Path,
    private val inspectProcess: (Long) -> ProcessIdentity = ::inspectProcessIdentity,
) {
    private val logger = LoggerFactory.getLogger(InstanceRegistry::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun register(instance: DriverInstance) {
        Files.createDirectories(directory)
        removeStale()
        val registered = if (instance.processStartEpochSeconds == null) {
            instance.copy(processStartEpochSeconds = inspectProcess(instance.pid).startEpochSeconds)
        } else {
            instance
        }
        write(registered)
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

    /** Clients without confirmed stale process identities, ordered by port. */
    fun list(): List<DriverInstance> {
        if (!Files.isDirectory(directory)) return emptyList()
        return files().mapNotNull(::read).filterNot(::isStale).sortedBy { it.port }
    }

    private fun removeStale() {
        files().forEach { file ->
            val instance = read(file)
            if (instance != null && isStale(instance)) file.deleteIfExists()
        }
    }

    private fun files(): List<Path> = try {
        directory.listDirectoryEntries("*.json").filter { it.extension == "json" }
    } catch (exception: IOException) {
        logger.debug("Cannot list instance directory {}", directory, exception)
        emptyList()
    } catch (exception: SecurityException) {
        logger.debug("Cannot access instance directory {}", directory, exception)
        emptyList()
    }

    private fun read(file: Path): DriverInstance? = try {
        json.decodeFromString(DriverInstance.serializer(), file.readText()).takeIf { isValid(it, file) }
    } catch (exception: SerializationException) {
        logger.debug("Ignoring unreadable instance file {}", file, exception)
        null
    } catch (exception: IOException) {
        logger.debug("Ignoring instance file {} that vanished or cannot be read", file, exception)
        null
    } catch (exception: SecurityException) {
        logger.debug("Ignoring instance file {} that cannot be accessed", file, exception)
        null
    }

    private fun isValid(instance: DriverInstance, file: Path): Boolean {
        if (instance.pid <= 0 || instance.name.isBlank()) return false
        if (file.fileName.toString() != "${instance.pid}.json") return false
        return instance.processStartEpochSeconds == null || instance.processStartEpochSeconds > 0
    }

    private fun isStale(instance: DriverInstance): Boolean {
        val process = inspectProcess(instance.pid)
        if (process.isAlive == false) return true
        val recordedStart = instance.processStartEpochSeconds ?: return false
        val actualStart = process.startEpochSeconds ?: return false
        return recordedStart != actualStart
    }

    private fun fileOf(pid: Long): Path = directory.resolve("$pid.json")
}
