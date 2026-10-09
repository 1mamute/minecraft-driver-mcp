package io.github.ummamute.driver.server

import java.net.BindException

/** An endpoint together with the port it is bound to. */
class BoundEndpoint<T>(val port: Int, val endpoint: T)

/**
 * Binds an endpoint to a port, closing the gap between [PortSelector] checking that a port is free and the server binding it.
 *
 * Two clients started together can both see the same port as free. Without a fixed port the loser of the race moves to the
 * next port; with a fixed port it fails, because the agent's configuration points at that exact port.
 */
object EndpointBinder {
    private const val MAX_CAUSE_DEPTH = 10

    /**
     * Calls [start] with a candidate port until it binds.
     *
     * @param requested the fixed port from `driver.port`, or null to pick one
     * @param start creates and starts the endpoint on the given port; it throws when the port cannot be bound
     * @throws IllegalStateException when the fixed port is taken or no free port is left
     */
    fun <T> bind(requested: Int?, host: String, start: (Int) -> T): BoundEndpoint<T> {
        var from = PortSelector.DEFAULT_PORT
        while (true) {
            val port = PortSelector.choose(requested, host, from)
            try {
                return BoundEndpoint(port, start(port))
            } catch (e: Exception) {
                if (!isBindFailure(e)) throw e
                check(requested == null) {
                    "Port $requested is already in use. Free it or set -Ddriver.port to another port (omit it to pick a free port automatically)"
                }
                from = port + 1
            }
        }
    }

    private fun isBindFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).any { it is BindException }
}
