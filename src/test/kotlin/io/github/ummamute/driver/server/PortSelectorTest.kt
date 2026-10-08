package io.github.ummamute.driver.server

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PortSelectorTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Test
    fun `explicit free port is used as given`() {
        val free = ServerSocket(0, 0, loopback).use { it.localPort }
        assertEquals(free, PortSelector.choose(free, "127.0.0.1"))
    }

    @Test
    fun `explicit port that is taken fails with a message naming the port and property`() {
        ServerSocket(0, 0, loopback).use { taken ->
            val error = assertFailsWith<IllegalStateException> { PortSelector.choose(taken.localPort, "127.0.0.1") }
            assertTrue(error.message.orEmpty().contains("${taken.localPort}") && error.message.orEmpty().contains("driver.port"))
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
