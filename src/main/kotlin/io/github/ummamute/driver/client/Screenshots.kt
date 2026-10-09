package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot

/** Captures the game's framebuffer, never the desktop. Call on the render thread. */
object Screenshots {
    /** Reads the framebuffer as PNG bytes. */
    fun capturePng(): ByteArray = Screenshot.takeScreenshot(Minecraft.getInstance().mainRenderTarget).use { it.asByteArray() }
}
