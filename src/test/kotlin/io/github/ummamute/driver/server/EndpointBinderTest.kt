package io.github.ummamute.driver.server

import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EndpointBinderTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Test
    fun `moves to the next port when another client wins the race for a port`() {
        val tried = mutableListOf<Int>()
        val bound = EndpointBinder.bind(null, "127.0.0.1") { port ->
            tried += port
            if (tried.size == 1) throw BindException("Address already in use")
            "endpoint"
        }
        assertEquals(2, tried.size)
        assertEquals(tried[0] + 1, tried[1])
        assertEquals(tried[1], bound.port)
    }

    @Test
    fun `recognizes a bind failure wrapped in another exception`() {
        var attempts = 0
        EndpointBinder.bind(null, "127.0.0.1") {
            attempts++
            if (attempts == 1) throw IllegalStateException("start failed", BindException("in use"))
        }
        assertEquals(2, attempts)
    }

    @Test
    fun `a fixed port that fails to bind is not replaced`() {
        val free = ServerSocket(0, 0, loopback).use { it.localPort }
        var attempts = 0
        val error = assertFailsWith<IllegalStateException> {
            EndpointBinder.bind(free, "127.0.0.1") {
                attempts++
                throw BindException("in use")
            }
        }
        assertEquals(1, attempts)
        assertTrue(error.message.orEmpty().contains("$free") && error.message.orEmpty().contains("driver.port"))
    }

    @Test
    fun `other failures are passed on without retrying`() {
        var attempts = 0
        assertFailsWith<IllegalArgumentException> {
            EndpointBinder.bind(null, "127.0.0.1") {
                attempts++
                throw IllegalArgumentException("bad")
            }
        }
        assertEquals(1, attempts)
    }
}
