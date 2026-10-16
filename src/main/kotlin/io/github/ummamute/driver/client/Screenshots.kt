package io.github.ummamute.driver.client

import io.github.ummamute.driver.tools.ScreenshotDimensions
import io.github.ummamute.driver.tools.ScreenshotInstance
import io.github.ummamute.driver.tools.ScreenshotMetadata
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot

/** The PNG and its metadata, captured together on the render thread. */
data class ScreenshotCapture(val png: ByteArray, val metadata: ScreenshotMetadata)

/** Captures the game's framebuffer, never the desktop. Call on the render thread. */
object Screenshots {
    /** Reads the framebuffer and state without yielding to another render-thread task. */
    fun capture(instanceName: String): ScreenshotCapture {
        val mc = Minecraft.getInstance()
        val window = mc.window
        return Screenshot.takeScreenshot(mc.mainRenderTarget).use { image ->
            val metadata = ScreenshotMetadata(
                framebuffer = ScreenshotDimensions(image.width, image.height),
                gui = ScreenshotDimensions(window.guiScaledWidth, window.guiScaledHeight),
                guiScale = window.guiScale,
                instance = ScreenshotInstance(instanceName, ProcessHandle.current().pid()),
                screen = mc.screen?.let(ClassNames::of),
                capturedAt = System.currentTimeMillis(),
                player = mc.player?.let { PlayerPosition(it.x, it.y, it.z, it.yRot, it.xRot) },
                worldTime = mc.level?.dayTime,
            )
            ScreenshotCapture(image.asByteArray(), metadata)
        }
    }
}
