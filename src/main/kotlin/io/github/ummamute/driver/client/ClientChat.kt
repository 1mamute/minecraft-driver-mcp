package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft

/** Sends chat and commands as the player. Call on the render thread. */
object ClientChat {
    fun send(text: String) {
        val connection = Minecraft.getInstance().connection ?: error("Not connected to a server")
        if (text.startsWith("/")) connection.sendCommand(text.removePrefix("/")) else connection.sendChat(text)
    }
}
