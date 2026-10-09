package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component

/** What a join or disconnect started. The connection finishes later; poll mc_get_state or use mc_wait_for. */
@Serializable
data class ConnectionResult(val action: String, val target: String?, val wasConnected: Boolean)

/** Joins and leaves servers and worlds the way the title screen buttons do. Call on the render thread. */
object ClientConnection {
    private val mc: Minecraft get() = Minecraft.getInstance()

    /** Opens the connect screen for this server; the login completes on later ticks. */
    fun joinServer(host: String, port: Int): ConnectionResult {
        check(!isConnected()) { "Already in a world or server; call mc_disconnect first" }
        val address = ServerAddress(host, port)
        val data = ServerData(address.toString(), address.toString(), ServerData.Type.OTHER)
        ConnectScreen.startConnecting(TitleScreen(), mc, address, data, false, null)
        return ConnectionResult("join_server", address.toString(), wasConnected = false)
    }

    /** Starts loading the singleplayer save in this folder. */
    fun joinWorld(folder: String): ConnectionResult {
        check(!isConnected()) { "Already in a world or server; call mc_disconnect first" }
        check(mc.levelSource.levelExists(folder)) { "No save folder \"$folder\" in the saves directory; create the world first or check the folder name" }
        mc.createWorldOpenFlows().openWorld(folder) { mc.setScreen(TitleScreen()) }
        return ConnectionResult("join_world", folder, wasConnected = false)
    }

    /** Leaves the world or server and returns to the title screen; harmless on the title screen. */
    fun disconnect(): ConnectionResult {
        val wasConnected = isConnected()
        if (wasConnected) leaveLikePauseScreen()
        return ConnectionResult("disconnect", null, wasConnected)
    }

    /**
     * Mirrors the pause screen's Save and Quit button. The level must close its connection first: without that an integrated
     * server never stops and Minecraft.disconnect waits for it forever on the render thread.
     */
    private fun leaveLikePauseScreen() {
        val isLocal = mc.isLocalServer
        mc.level?.disconnect()
        if (isLocal) {
            mc.disconnect(GenericMessageScreen(Component.translatable("menu.savingLevel")))
        } else {
            mc.disconnect()
        }
        mc.setScreen(TitleScreen())
    }

    private fun isConnected(): Boolean = mc.level != null || mc.connection != null
}
