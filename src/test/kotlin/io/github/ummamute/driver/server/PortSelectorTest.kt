package io.github.ummamute.driver.server

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PortSelectorTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Test
    fun `explicit port is used even when it is taken`() {
        ServerSocket(0, 0, loopback).use { taken ->
            assertEquals(taken.localPort, PortSelector.choose(taken.localPort, "127.0.0.1"))
        }
    }

    @Test
    fun `second client without a port skips the port the first client holds`() {
        val first = PortSelector.choose(null, "127.0.0.1")
        ServerSocket(first, 0, loopback).use {
            val second = PortSelector.choose(null, "127.0.0.1")
            assertNotEquals(first, second)
        }
    }
}
