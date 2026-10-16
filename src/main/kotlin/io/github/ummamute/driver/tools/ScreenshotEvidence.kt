package io.github.ummamute.driver.tools

import kotlinx.serialization.Serializable
import java.io.IOException
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE

/** Paths of the PNG and its JSON capture metadata, returned only after both files are written. */
@Serializable
data class ScreenshotEvidencePaths(val pngPath: String, val metadataPath: String)

/** Opt-in screenshot storage. Call disk operations on an IO dispatcher, outside the render thread. */
class ScreenshotEvidence(
    private val directory: Path?,
    private val openFile: (Path) -> OutputStream = { Files.newOutputStream(it, CREATE_NEW, WRITE) },
) {
    /** Validates the requested filename and configuration before capturing an image. */
    fun validate(saveAs: String) {
        require(directory != null) {
            "Screenshot saving is disabled; set -Ddriver.screenshotDirectory=<directory> when starting the client, then retry save_as."
        }
        require(FILENAME.matches(saveAs)) {
            "save_as must be a simple .png filename with letters/digits/_/- in its stem; choose a filename such as capture-1.png."
        }
    }

    /** Writes a new PNG and matching JSON sidecar. Existing artifacts are never overwritten. */
    @Synchronized
    fun save(png: ByteArray, metadataJson: String, saveAs: String): ScreenshotEvidencePaths {
        validate(saveAs)
        val created = mutableListOf<Path>()
        try {
            val output = prepareDirectory(checkNotNull(directory))
            val image = output.resolve(saveAs)
            val metadata = output.resolve(saveAs.removeSuffix(".png") + ".json")
            checkTarget(image)
            checkTarget(metadata)
            writeNew(image, png, created)
            writeNew(metadata, metadataJson.toByteArray(Charsets.UTF_8), created)
            return ScreenshotEvidencePaths(image.toString(), metadata.toString())
        } catch (exception: IOException) {
            removeCreated(created, exception)
            throw writeFailure(exception)
        } catch (exception: SecurityException) {
            removeCreated(created, exception)
            throw IllegalStateException(
                "Cannot access screenshot evidence; grant directory access or choose another driver.screenshotDirectory, then retry.",
                exception,
            )
        }
    }

    private fun writeFailure(exception: IOException): IllegalStateException {
        val message = if (exception is FileAlreadyExistsException) {
            "Screenshot evidence already exists; choose another save_as filename and retry."
        } else {
            "Cannot save screenshot evidence; check driver.screenshotDirectory permissions and available disk space, then retry with a new filename."
        }
        return IllegalStateException(message, exception)
    }

    private fun prepareDirectory(configured: Path): Path {
        val output = configured.toAbsolutePath().normalize()
        var current = output.root
        for (part in output) {
            current = current.resolve(part)
            require(!Files.isSymbolicLink(current)) {
                "Screenshot directory contains a symbolic link; configure a directory without symbolic links and retry."
            }
            if (!Files.exists(current, NOFOLLOW_LINKS)) Files.createDirectory(current)
            require(Files.isDirectory(current, NOFOLLOW_LINKS)) {
                "Screenshot directory is not a directory; choose another driver.screenshotDirectory and retry."
            }
        }
        return output
    }

    private fun checkTarget(path: Path) {
        require(!Files.isSymbolicLink(path)) { "Screenshot target is a symbolic link; choose another save_as filename." }
        require(!Files.exists(path, NOFOLLOW_LINKS)) { "Screenshot evidence already exists; choose another save_as filename and retry." }
    }

    private fun writeNew(path: Path, bytes: ByteArray, created: MutableList<Path>) {
        openFile(path).use { output ->
            created.add(path)
            output.write(bytes)
        }
    }

    private fun removeCreated(created: List<Path>, failure: Exception) {
        for (path in created.asReversed()) {
            try {
                Files.deleteIfExists(path)
            } catch (exception: IOException) {
                failure.addSuppressed(exception)
            } catch (exception: SecurityException) {
                failure.addSuppressed(exception)
            }
        }
    }

    private companion object {
        val FILENAME = Regex("[A-Za-z0-9][A-Za-z0-9_-]*\\.png")
    }
}
