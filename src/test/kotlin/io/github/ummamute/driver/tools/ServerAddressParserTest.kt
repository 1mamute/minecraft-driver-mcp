package io.github.ummamute.driver.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerAddressParserTest {
    @Test
    fun `host alone gets the default port`() {
        assertEquals(HostPort("example.com", 25565), ServerAddressParser.parse(" example.com "))
    }

    @Test
    fun `host with port is split`() {
        assertEquals(HostPort("localhost", 25566), ServerAddressParser.parse("localhost:25566"))
    }

    @Test
    fun `bracketed ipv6 with and without port`() {
        assertEquals(HostPort("::1", 25565), ServerAddressParser.parse("[::1]"))
        assertEquals(HostPort("::1", 1234), ServerAddressParser.parse("[::1]:1234"))
    }

    @Test
    fun `bare ipv6 is a host with the default port`() {
        assertEquals(HostPort("fe80::1", 25565), ServerAddressParser.parse("fe80::1"))
    }

    @Test
    fun `rejects blank addresses, bad ports and broken brackets with a message`() {
        listOf("", "  ", ":25565", "host:abc", "host:0", "host:70000", "host:", "[::1", "[::1]x", "a b").forEach {
            assertFailsWith<IllegalStateException>(it) { ServerAddressParser.parse(it) }
        }
    }
}
