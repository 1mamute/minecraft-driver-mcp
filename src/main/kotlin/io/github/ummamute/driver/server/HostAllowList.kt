package io.github.ummamute.driver.server

import java.net.Inet6Address
import java.net.InetAddress

/** The `Host` and `Origin` names the endpoint accepts, which guard against DNS rebinding. */
data class HostAllowList(val hosts: List<String>, val origins: List<String>)

/** Chooses the allow list for a bind address. */
object HostAllowLists {
    private val LOCAL_HOSTS = listOf("localhost", "127.0.0.1", "[::1]")

    /**
     * Returns `null` for a loopback or wildcard bind address, which keeps the SDK's localhost defaults. A specific
     * non-loopback address is added to the localhost names, so a client that connects to that address is not rejected.
     */
    fun forBindAddress(host: String): HostAllowList? {
        val address = InetAddress.getByName(host)
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return null
        val name = if (address is Inet6Address) "[${host.removeSurrounding("[", "]")}]" else host
        val hosts = LOCAL_HOSTS + name
        return HostAllowList(hosts = hosts, origins = hosts.map { "http://$it" })
    }
}
