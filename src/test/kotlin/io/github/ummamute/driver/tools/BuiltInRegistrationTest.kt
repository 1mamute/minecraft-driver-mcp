package io.github.ummamute.driver.tools

import io.github.ummamute.driver.catalogue.BuiltInTools
import io.github.ummamute.driver.catalogue.ToolContract
import io.github.ummamute.driver.client.ClientKeyboard
import io.github.ummamute.driver.client.SlotAction
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals

class BuiltInRegistrationTest {
    @Test
    fun `every built-in definition has exactly one registered handler`() {
        val server = Server(
            serverInfo = Implementation(name = "catalogue-test", version = "1"),
            options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        )
        InstanceTools.register(server)
        ObservationTools.register(server)
        ActionTools.register(server)
        ConnectionTools.register(server)

        assertEquals(BuiltInTools.all, server.tools.values.map { it.tool })
    }

    @Test
    fun `slot action handlers implement the catalogue actions in order`() {
        assertEquals(ToolContract.SLOT_ACTIONS, SlotAction.entries.map { it.label })
    }

    @Test
    fun `screen key handlers implement the catalogue keys in order`() {
        assertEquals(ToolContract.SCREEN_KEYS, ClientKeyboard.keyNames)
    }
}
