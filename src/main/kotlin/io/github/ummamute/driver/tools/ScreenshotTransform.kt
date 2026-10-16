package io.github.ummamute.driver.tools

import kotlinx.serialization.Serializable
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

/** Optional crop in framebuffer pixels, followed by an optional maximum output width. */
internal data class ScreenshotOptions(
    val x: Int? = null,
    val y: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val maxWidth: Int? = null,
) {
    init {
        val cropValues = listOf(x, y, width, height)
        require(cropValues.all { it == null } || cropValues.all { it != null }) {
            "Give x, y, width and height together to crop, or omit all four for the full framebuffer."
        }
        require(x == null || x >= 0) { "Use x >= 0 in framebuffer pixels from the left edge." }
        require(y == null || y >= 0) { "Use y >= 0 in framebuffer pixels from the top edge." }
        require(width == null || width > 0) { "Use a positive crop width in framebuffer pixels." }
        require(height == null || height > 0) { "Use a positive crop height in framebuffer pixels." }
        require(maxWidth == null || maxWidth > 0) { "Use a positive max_width, or omit it to keep the crop's native size." }
    }

    fun region(framebufferWidth: Int, framebufferHeight: Int): ScreenshotRegion {
        val region = ScreenshotRegion(x = x ?: 0, y = y ?: 0, width = width ?: framebufferWidth, height = height ?: framebufferHeight)
        require(region.x <= framebufferWidth && region.width <= framebufferWidth - region.x) {
            "Crop exceeds framebuffer width $framebufferWidth; use mc_screenshot metadata to choose an in-bounds crop."
        }
        require(region.y <= framebufferHeight && region.height <= framebufferHeight - region.y) {
            "Crop exceeds framebuffer height $framebufferHeight; use mc_screenshot metadata to choose an in-bounds crop."
        }
        return region
    }
}

/** Source rectangle used to map returned image pixels back to framebuffer coordinates. */
@Serializable
data class ScreenshotRegion(val x: Int, val y: Int, val width: Int, val height: Int)

/** Converts returned image coordinates with GUI coordinate = offset + image coordinate * scale. */
@Serializable
data class ScreenshotGuiTransform(val offsetX: Double, val offsetY: Double, val scaleX: Double, val scaleY: Double)

/** PNG and its actual dimensions after cropping and resizing. */
internal data class TransformedScreenshot(val png: ByteArray, val width: Int, val height: Int, val region: ScreenshotRegion) {
    fun metadata(original: ScreenshotMetadata): ScreenshotMetadata {
        val guiPerPixelX = original.gui.width.toDouble() / original.framebuffer.width
        val guiPerPixelY = original.gui.height.toDouble() / original.framebuffer.height
        return original.copy(
            image = ScreenshotDimensions(width, height),
            crop = region,
            imageToGui = ScreenshotGuiTransform(
                offsetX = region.x * guiPerPixelX,
                offsetY = region.y * guiPerPixelY,
                scaleX = region.width.toDouble() / width * guiPerPixelX,
                scaleY = region.height.toDouble() / height * guiPerPixelY,
            ),
        )
    }
}

/** Processes captured PNG bytes without touching Minecraft or holding the render thread. */
internal object ScreenshotTransform {
    fun apply(png: ByteArray, framebufferWidth: Int, framebufferHeight: Int, options: ScreenshotOptions): TransformedScreenshot {
        val region = options.region(framebufferWidth, framebufferHeight)
        val outputWidth = minOf(region.width, options.maxWidth ?: region.width)
        val outputHeight = (region.height.toLong() * outputWidth / region.width).toInt().coerceAtLeast(1)
        if (region == ScreenshotRegion(x = 0, y = 0, width = framebufferWidth, height = framebufferHeight) && outputWidth == framebufferWidth) {
            return TransformedScreenshot(png, framebufferWidth, framebufferHeight, region)
        }
        val source = decode(png)
        val crop = source.getSubimage(region.x, region.y, region.width, region.height)
        val output = if (outputWidth == region.width) crop else resize(crop, outputWidth, outputHeight)
        return TransformedScreenshot(encode(output), outputWidth, outputHeight, region)
    }

    private fun decode(png: ByteArray): BufferedImage = MemoryCacheImageInputStream(ByteArrayInputStream(png)).use { input ->
        val readers = ImageIO.getImageReaders(input)
        check(readers.hasNext()) { "Could not decode captured PNG; try mc_screenshot again." }
        val reader = readers.next()
        try {
            reader.input = input
            reader.read(0)
        } finally {
            reader.dispose()
        }
    }

    private fun encode(image: BufferedImage): ByteArray = ByteArrayOutputStream().use { bytes ->
        MemoryCacheImageOutputStream(bytes).use {
            check(ImageIO.write(image, "png", it)) { "Could not encode captured PNG; try mc_screenshot again." }
        }
        bytes.toByteArray()
    }

    private fun resize(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = output.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return output
    }
}
