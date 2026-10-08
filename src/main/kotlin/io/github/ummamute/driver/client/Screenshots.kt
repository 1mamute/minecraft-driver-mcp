package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import java.io.File

/** Captures the game's framebuffer, never the desktop. Call on the render thread. */
object Screenshots {
    /** Reads the framebuffer as PNG bytes. */
    fun capturePng(): ByteArray {
        val file = File.createTempFile("driver-shot", ".png")
        try {
            Screenshot.takeScreenshot(Minecraft.getInstance().mainRenderTarget).use { it.writeToFile(file) }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }
}
