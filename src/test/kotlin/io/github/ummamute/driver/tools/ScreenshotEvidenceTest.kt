package io.github.ummamute.driver.tools

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.FilterOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenshotEvidenceTest {
    private val directory = Files.createTempDirectory("driver-screenshot-evidence")
    private val image = byteArrayOf(1, 2, 3)
    private val metadata = """{"width":3,"height":2}"""

    @Test
    fun `saving requires an explicitly configured directory`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ScreenshotEvidence(null).save(image, metadata, "capture.png")
        }

        assertTrue(failure.message.orEmpty().contains("driver.screenshotDirectory"))
        assertEquals(emptyList(), entries(directory))
    }

    @Test
    fun `creates the configured directory and returns both complete artifact paths`() {
        val output = directory.resolve("new/evidence")
        val paths = ScreenshotEvidence(output).save(image, metadata, "capture-1.png")

        assertEquals(output.resolve("capture-1.png").toString(), paths.pngPath)
        assertEquals(output.resolve("capture-1.json").toString(), paths.metadataPath)
        assertContentEquals(image, Path.of(paths.pngPath).readBytes())
        assertEquals(metadata, Path.of(paths.metadataPath).readText())
        assertEquals(listOf("capture-1.json", "capture-1.png"), entries(output))
    }

    @Test
    fun `rejects traversal absolute nested and non png filenames before creating output`() {
        val output = directory.resolve("not-created")
        val storage = ScreenshotEvidence(output)
        val invalid = listOf("../outside.png", "..\\outside.png", "/tmp/out.png", "C:\\out.png", "nested/out.png", "capture.json", ".png", "a:b.png")

        for (filename in invalid) {
            assertFailsWith<IllegalArgumentException>(filename) { storage.save(image, metadata, filename) }
        }

        assertFalse(output.exists())
    }

    @Test
    fun `existing png is never overwritten`() {
        val existing = directory.resolve("capture.png")
        existing.writeBytes(byteArrayOf(9))

        val failure = assertFailsWith<IllegalArgumentException> {
            ScreenshotEvidence(directory).save(image, metadata, "capture.png")
        }

        assertTrue(failure.message.orEmpty().contains("another save_as"))
        assertContentEquals(byteArrayOf(9), existing.readBytes())
        assertFalse(directory.resolve("capture.json").exists())
    }

    @Test
    fun `existing sidecar is preserved without leaving a png`() {
        val sidecar = directory.resolve("capture.json")
        sidecar.writeText("old metadata")

        assertFailsWith<IllegalArgumentException> { ScreenshotEvidence(directory).save(image, metadata, "capture.png") }

        assertEquals("old metadata", sidecar.readText())
        assertEquals(listOf("capture.json"), entries(directory))
    }

    @Test
    fun `directory in place of sidecar is rejected without changing the png`() {
        directory.resolve("capture.png").writeBytes(byteArrayOf(9))
        Files.createDirectory(directory.resolve("capture.json"))

        assertFailsWith<IllegalArgumentException> {
            ScreenshotEvidence(directory).save(image, metadata, "capture.png")
        }

        assertContentEquals(byteArrayOf(9), directory.resolve("capture.png").readBytes())
        assertTrue(Files.isDirectory(directory.resolve("capture.json")))
    }

    @Test
    fun `file in place of configured directory returns an actionable error`() {
        val output = directory.resolve("not-a-directory")
        output.writeText("original")

        val failure = assertFailsWith<IllegalArgumentException> {
            ScreenshotEvidence(output).save(image, metadata, "capture.png")
        }

        assertTrue(failure.message.orEmpty().contains("driver.screenshotDirectory"))
        assertEquals("original", output.readText())
    }

    @Test
    fun `failed sidecar creation removes the newly created png`() {
        val storage = ScreenshotEvidence(directory) { path ->
            if (path.fileName.toString().endsWith(".json")) throw IOException("Simulated sidecar creation failure")
            Files.newOutputStream(path, CREATE_NEW, WRITE)
        }

        val failure = assertFailsWith<IllegalStateException> { storage.save(image, metadata, "capture.png") }

        assertTrue(failure.message.orEmpty().contains("permissions"))
        assertEquals(emptyList(), entries(directory))
    }

    @Test
    fun `failed image write removes its partial png`() {
        val storage = ScreenshotEvidence(directory) { path ->
            object : FilterOutputStream(Files.newOutputStream(path, CREATE_NEW, WRITE)) {
                override fun write(bytes: ByteArray) {
                    out.write(bytes[0].toInt())
                    throw IOException("Simulated disk full")
                }
            }
        }

        assertFailsWith<IllegalStateException> {
            storage.save(image, metadata, "capture.png")
        }

        assertEquals(emptyList(), entries(directory))
    }

    @Test
    fun `symbolic link targets are rejected`() {
        val outside = Files.createTempFile("driver-outside", ".png")
        outside.writeBytes(byteArrayOf(9))
        createSymbolicLink(directory.resolve("capture.png"), outside)

        assertFailsWith<IllegalArgumentException> {
            ScreenshotEvidence(directory).save(image, metadata, "capture.png")
        }

        assertContentEquals(byteArrayOf(9), outside.readBytes())
        assertFalse(directory.resolve("capture.json").exists())
    }

    @Test
    fun `symbolic links in configured directory ancestors are rejected`() {
        val outside = Files.createTempDirectory("driver-outside")
        val link = directory.resolve("linked")
        createSymbolicLink(link, outside)

        assertFailsWith<IllegalArgumentException> {
            ScreenshotEvidence(link.resolve("child")).save(image, metadata, "capture.png")
        }

        assertEquals(emptyList(), entries(outside))
    }

    private fun createSymbolicLink(link: Path, target: Path) {
        try {
            Files.createSymbolicLink(link, target)
        } catch (exception: IOException) {
            assumeTrue(false, "Symbolic links are unavailable: ${exception.message}")
        } catch (exception: UnsupportedOperationException) {
            assumeTrue(false, "Symbolic links are unsupported: ${exception.message}")
        }
    }

    private fun entries(path: Path): List<String> =
        Files.list(path).use { files -> files.map { it.fileName.toString() }.sorted().toList() }
}
