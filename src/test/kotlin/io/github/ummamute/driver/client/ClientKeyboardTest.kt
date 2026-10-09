package io.github.ummamute.driver.client

import kotlin.test.Test
import kotlin.test.assertTrue

class ClientKeyboardTest {
    @Test
    fun `offers the keys a screen needs`() {
        assertTrue(ClientKeyboard.keyNames.containsAll(listOf("enter", "escape", "tab", "backspace")))
    }
}
