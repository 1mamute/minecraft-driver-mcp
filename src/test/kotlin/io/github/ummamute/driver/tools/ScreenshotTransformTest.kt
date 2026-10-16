package io.github.ummamute.driver.tools

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScreenshotTransformTest {
    @Test
    fun `default and oversized width preserve original PNG bytes`() {
        val png = png(width = 8, height = 4)
        for (options in listOf(ScreenshotOptions(), ScreenshotOptions(maxWidth = 20))) {
            val result = ScreenshotTransform.apply(png, 8, 4, options)
            assertSame(png, result.png)
            assertEquals(8, result.width)
            assertEquals(4, result.height)
            assertEquals(ScreenshotRegion(0, 0, 8, 4), result.region)
        }
    }

    @Test
    fun `crop preserves every native pixel and uses the top left origin`() {
        val png = png(width = 8, height = 4)
        val result = ScreenshotTransform.apply(png, 8, 4, ScreenshotOptions(x = 2, y = 1, width = 6, height = 3))
        val source = ImageIO.read(ByteArrayInputStream(png))
        val actual = ImageIO.read(ByteArrayInputStream(result.png))
        assertEquals(6, actual.width)
        assertEquals(3, actual.height)
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                assertEquals(source.getRGB(x + 2, y + 1), actual.getRGB(x, y))
            }
        }
        assertEquals(ScreenshotRegion(2, 1, 6, 3), result.region)
    }

    @Test
    fun `resizes after crop and reports actual rounded output dimensions`() {
        val options = ScreenshotOptions(x = 1, y = 1, width = 7, height = 3, maxWidth = 4)
        val result = ScreenshotTransform.apply(png(width = 8, height = 4), 8, 4, options)
        val actual = ImageIO.read(ByteArrayInputStream(result.png))
        assertEquals(4, result.width)
        assertEquals(1, result.height)
        assertEquals(result.width, actual.width)
        assertEquals(result.height, actual.height)
        assertEquals(ScreenshotRegion(1, 1, 7, 3), result.region)
    }

    @Test
    fun `resizing samples only the cropped region`() {
        val image = BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB)
        for (y in 1..2) {
            for (x in 2..5) {
                image.setRGB(x, y, Color.GREEN.rgb)
            }
        }
        val png = ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            it.toByteArray()
        }
        val result = ScreenshotTransform.apply(png, 8, 4, ScreenshotOptions(x = 2, y = 1, width = 4, height = 2, maxWidth = 2))
        val actual = ImageIO.read(ByteArrayInputStream(result.png))
        assertEquals(Color.GREEN.rgb, actual.getRGB(0, 0))
        assertEquals(Color.GREEN.rgb, actual.getRGB(1, 0))
    }

    @Test
    fun `downscales the full framebuffer with a minimum height of one`() {
        val result = ScreenshotTransform.apply(png(width = 8, height = 1), 8, 1, ScreenshotOptions(maxWidth = 1))
        assertEquals(1, result.width)
        assertEquals(1, result.height)
    }

    @Test
    fun `downscales full framebuffer proportionally without a crop`() {
        val result = ScreenshotTransform.apply(png(width = 8, height = 4), 8, 4, ScreenshotOptions(maxWidth = 4))
        val actual = ImageIO.read(ByteArrayInputStream(result.png))
        assertEquals(4, actual.width)
        assertEquals(2, actual.height)
        assertEquals(ScreenshotRegion(0, 0, 8, 4), result.region)
    }

    @Test
    fun `maximum width never enlarges a crop`() {
        val result = ScreenshotTransform.apply(
            png(width = 8, height = 4),
            8,
            4,
            ScreenshotOptions(x = 3, y = 2, width = 2, height = 1, maxWidth = 8),
        )
        assertEquals(2, result.width)
        assertEquals(1, result.height)
    }

    @Test
    fun `GUI mapping includes crop origin and separate scales after height rounding`() {
        val original = metadata()
        val transformed = TransformedScreenshot(
            png = byteArrayOf(),
            width = 400,
            height = 171,
            region = ScreenshotRegion(x = 100, y = 99, width = 701, height = 301),
        )
        val result = transformed.metadata(original)
        val mapping = result.imageToGui
        assertEquals(100.0 * 334 / 1001, mapping.offsetX, 0.000000001)
        assertEquals(99.0 * 234 / 701, mapping.offsetY, 0.000000001)
        assertEquals(801.0 * 334 / 1001, mapping.offsetX + 400 * mapping.scaleX, 0.000000001)
        assertEquals(400.0 * 234 / 701, mapping.offsetY + 171 * mapping.scaleY, 0.000000001)
        assertEquals(ScreenshotDimensions(400, 171), result.image)
        assertEquals(transformed.region, result.crop)
        assertEquals(original, result.copy(image = original.image, crop = original.crop, imageToGui = original.imageToGui))
        assertEquals(result, ToolSupport.json.decodeFromString<ScreenshotMetadata>(ToolSupport.json.encodeToString(result)))
    }

    @Test
    fun `native metadata defaults to the original framebuffer and GUI ratios`() {
        val result = metadata()
        assertEquals(result.framebuffer, result.image)
        assertEquals(ScreenshotRegion(x = 0, y = 0, width = 1001, height = 701), result.crop)
        assertEquals(0.0, result.imageToGui.offsetX)
        assertEquals(0.0, result.imageToGui.offsetY)
        assertEquals(334.0 / 1001, result.imageToGui.scaleX)
        assertEquals(234.0 / 701, result.imageToGui.scaleY)
    }

    @Test
    fun `rejects each incomplete crop before capturing an image`() {
        for (mask in 1 until 15) {
            val failure = assertFailsWith<IllegalArgumentException> {
                cropFromMask(mask)
            }
            assertTrue(failure.message.orEmpty().contains("together"))
        }
    }

    @Test
    fun `rejects negative coordinates and nonpositive dimensions`() {
        assertFailsWith<IllegalArgumentException> { ScreenshotOptions(x = -1, y = 0, width = 1, height = 1) }
        assertFailsWith<IllegalArgumentException> { ScreenshotOptions(x = 0, y = -1, width = 1, height = 1) }
        for (value in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> { ScreenshotOptions(x = 0, y = 0, width = value, height = 1) }
            assertFailsWith<IllegalArgumentException> { ScreenshotOptions(x = 0, y = 0, width = 1, height = value) }
            assertFailsWith<IllegalArgumentException> { ScreenshotOptions(maxWidth = value) }
        }
    }

    @Test
    fun `rejects out of bounds and overflowing crops without decoding PNG`() {
        val crops = listOf(
            ScreenshotOptions(x = 8, y = 0, width = 1, height = 1),
            ScreenshotOptions(x = 0, y = 4, width = 1, height = 1),
            ScreenshotOptions(x = 1, y = 0, width = Int.MAX_VALUE, height = 1),
            ScreenshotOptions(x = 0, y = Int.MAX_VALUE, width = 1, height = 1),
            ScreenshotOptions(x = 0, y = 1, width = 1, height = Int.MAX_VALUE),
        )
        for (crop in crops) {
            assertFailsWith<IllegalArgumentException> { ScreenshotTransform.apply(byteArrayOf(), 8, 4, crop) }
        }
    }

    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, (0x80 shl 24) or (x shl 16) or (y shl 8) or (x + y))
            }
        }
        return ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            it.toByteArray()
        }
    }

    private fun metadata() = ScreenshotMetadata(
        framebuffer = ScreenshotDimensions(1001, 701),
        gui = ScreenshotDimensions(334, 234),
        guiScale = 3.0,
        instance = ScreenshotInstance("test-client", 1234L),
        screen = "TitleScreen",
        capturedAt = 1_800_000_000_123L,
        player = null,
        worldTime = null,
    )

    private fun cropFromMask(mask: Int) = ScreenshotOptions(
        x = if (mask and 1 != 0) 0 else null,
        y = if (mask and 2 != 0) 0 else null,
        width = if (mask and 4 != 0) 1 else null,
        height = if (mask and 8 != 0) 1 else null,
    )
}
