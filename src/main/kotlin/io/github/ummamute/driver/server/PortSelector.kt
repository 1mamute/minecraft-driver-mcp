package io.github.ummamute.driver.server

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Chooses the port for one client's MCP server so several clients can run side by side.
 *
 * An explicit port (`-Ddriver.port`) is used as given: the agent's MCP config points at it, so silently moving
 * would break the connection. Without one, the first free port from [DEFAULT_PORT] is taken.
 */
object PortSelector {
    const val DEFAULT_PORT = 25890
    private const val SCAN_RANGE = 100

    fun choose(requested: Int?, host: String): Int {
        if (requested != null) return requested
        val address = InetAddress.getByName(host)
        return (DEFAULT_PORT until DEFAULT_PORT + SCAN_RANGE).firstOrNull { isFree(address, it) }
            ?: error("No free port in $DEFAULT_PORT..${DEFAULT_PORT + SCAN_RANGE - 1}. Set -Ddriver.port to a free port")
    }

    private fun isFree(address: InetAddress, port: Int): Boolean = try {
        ServerSocket(port, 0, address).use { true }
    } catch (_: IOException) {
        false
    }
}
