package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientStateSnapshot
import io.github.ummamute.driver.client.LoggedMessage
import io.github.ummamute.driver.client.MessagePage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionWaiterTest {
    @Volatile
    private var screen: String? = null
    private val messages = mutableListOf<LoggedMessage>()

    private val waiter = ConditionWaiter(
        readState = { ClientStateSnapshot(screen = screen, connected = true, windowFocused = true, player = null) },
        readMessages = { since -> MessagePage(messages.filter { it.seq > since }, messages.lastOrNull()?.seq ?: 0L) },
        pollMillis = 5L,
    )

    @Test
    fun `matches a message that is already buffered after since`() = runBlocking {
        messages += LoggedMessage(1, "old news")
        messages += LoggedMessage(2, "Set the time to 1000")

        val result = waiter.await(WaitCondition(messageContains = "set the TIME", sinceSeq = 1), timeoutMillis = 500)

        assertTrue(result.matched)
        assertEquals("message", result.reason)
        assertEquals(2L, result.message?.seq)
    }

    @Test
    fun `ignores messages at or before since`() = runBlocking {
        messages += LoggedMessage(1, "ready")

        val result = waiter.await(WaitCondition(messageContains = "ready", sinceSeq = 1), timeoutMillis = 50)

        assertFalse(result.matched)
        assertEquals("timeout", result.reason)
    }

    @Test
    fun `matches a screen class name and reports the state`() = runBlocking {
        screen = "net.minecraft.client.gui.screens.PauseScreen"

        val result = waiter.await(WaitCondition(screenContains = "pausescreen"), timeoutMillis = 500)

        assertTrue(result.matched)
        assertEquals("screen", result.reason)
        assertEquals(screen, result.state?.screen)
    }

    @Test
    fun `waits for the screen to close when asked for none`() = runBlocking {
        screen = "net.minecraft.client.gui.screens.PauseScreen"
        val closer = Thread {
            Thread.sleep(30)
            screen = null
        }
        closer.start()

        val result = waiter.await(WaitCondition(screenContains = WaitCondition.NO_SCREEN), timeoutMillis = 2000)

        assertTrue(result.matched)
        assertNull(result.state?.screen)
    }

    @Test
    fun `times out with the latest sequence number`() = runBlocking {
        messages += LoggedMessage(7, "unrelated")

        val result = waiter.await(WaitCondition(messageContains = "never"), timeoutMillis = 30)

        assertFalse(result.matched)
        assertEquals(7L, result.latest)
    }
}
