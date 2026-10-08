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
        val address = InetAddress.getByName(host)
        if (requested != null) {
            check(isFree(address, requested)) {
                "Port $requested is already in use. Free it or set -Ddriver.port to another port (omit it to pick a free port automatically)"
            }
            return requested
        }
        return (DEFAULT_PORT until DEFAULT_PORT + SCAN_RANGE).firstOrNull { isFree(address, it) }
            ?: error("No free port in $DEFAULT_PORT..${DEFAULT_PORT + SCAN_RANGE - 1}. Set -Ddriver.port to a free port")
    }

    private fun isFree(address: InetAddress, port: Int): Boolean = try {
        ServerSocket(port, 0, address).use { true }
    } catch (_: IOException) {
        false
    }
}
