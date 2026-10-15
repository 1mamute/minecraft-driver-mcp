package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientInput
import io.github.ummamute.driver.client.ClientKeyboard
import io.github.ummamute.driver.client.LogBuffer
import io.github.ummamute.driver.client.SlotAction
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        assertEquals(argumentValues("mc_click_slot", "action"), SlotAction.entries.map { it.label })
    }

    @Test
    fun `screen key handlers implement the catalogue keys in order`() {
        assertEquals(argumentValues("mc_press_key", "name"), ClientKeyboard.keyNames)
    }

    @Test
    fun `movement handlers implement the gateway catalogue keys`() {
        assertEquals(argumentValues("mc_set_key", "name"), ClientInput.keyNames)
    }

    @Test
    fun `log handlers implement the gateway catalogue levels`() {
        assertEquals(argumentValues("mc_read_log", "min_level"), LogBuffer.LEVELS)
    }

    private fun argumentValues(tool: String, argument: String): List<String> {
        val property = requireNotNull(BuiltInTools[tool].inputSchema.properties)[argument]
        return requireNotNull(property).jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }
    }
}
