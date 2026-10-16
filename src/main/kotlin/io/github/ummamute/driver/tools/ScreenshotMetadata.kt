package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.PlayerPosition
import kotlinx.serialization.Serializable

/** Dimensions in screenshot pixels or GUI units, depending on the containing field. */
@Serializable
data class ScreenshotDimensions(val width: Int, val height: Int)

/** Identifies the client that supplied a capture, using the same name and PID as discovery. */
@Serializable
data class ScreenshotInstance(val name: String, val pid: Long)

/** State sampled alongside the framebuffer on the render thread. Both coordinate origins are top left. */
@Serializable
data class ScreenshotMetadata(
    val framebuffer: ScreenshotDimensions,
    val gui: ScreenshotDimensions,
    /** Effective Minecraft GUI scale, including automatic scale and Unicode adjustments. */
    val guiScale: Double,
    val instance: ScreenshotInstance,
    val screen: String?,
    /** Unix epoch milliseconds at capture, before PNG encoding. */
    val capturedAt: Long,
    val player: PlayerPosition?,
    /** Client-observed world day time in ticks; null without a level. */
    val worldTime: Long?,
    /** Absolute saved artifact paths, or null when this capture was not saved. */
    val evidence: ScreenshotEvidencePaths? = null,
    /** Actual returned PNG dimensions, after cropping and optional downscaling. */
    val image: ScreenshotDimensions = framebuffer,
    /** Source rectangle in original framebuffer pixels, with a top-left origin. */
    val crop: ScreenshotRegion = ScreenshotRegion(x = 0, y = 0, width = framebuffer.width, height = framebuffer.height),
    val imageToGui: ScreenshotGuiTransform = ScreenshotGuiTransform(
        offsetX = 0.0,
        offsetY = 0.0,
        scaleX = gui.width.toDouble() / framebuffer.width,
        scaleY = gui.height.toDouble() / framebuffer.height,
    ),
)
