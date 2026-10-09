package io.github.ummamute.driver.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostAllowListsTest {
    @Test
    fun `keeps the localhost defaults for loopback addresses`() {
        assertNull(HostAllowLists.forBindAddress("127.0.0.1"))
        assertNull(HostAllowLists.forBindAddress("localhost"))
        assertNull(HostAllowLists.forBindAddress("::1"))
    }

    @Test
    fun `keeps the localhost defaults for wildcard addresses`() {
        assertNull(HostAllowLists.forBindAddress("0.0.0.0"))
        assertNull(HostAllowLists.forBindAddress("::"))
    }

    @Test
    fun `adds a specific address to the localhost names`() {
        val allowList = HostAllowLists.forBindAddress("192.168.1.20")

        assertEquals(listOf("localhost", "127.0.0.1", "[::1]", "192.168.1.20"), allowList?.hosts)
        assertEquals("http://192.168.1.20", allowList?.origins?.last())
    }

    @Test
    fun `brackets an IPv6 address`() {
        val allowList = HostAllowLists.forBindAddress("fd00::20")

        assertEquals("[fd00::20]", allowList?.hosts?.last())
        assertEquals("http://[fd00::20]", allowList?.origins?.last())
    }
}
