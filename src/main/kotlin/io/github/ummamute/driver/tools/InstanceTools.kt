package io.github.ummamute.driver.tools

import io.github.ummamute.driver.server.InstanceRegistry
import io.github.ummamute.driver.tools.ToolSupport.addGuardedTool
import io.github.ummamute.driver.tools.ToolSupport.jsonResult
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations

/** Tools about the running clients themselves, for setups with more than one client. */
internal object InstanceTools {
    /** Set once at startup, before the first request. */
    lateinit var registry: InstanceRegistry

    fun register(server: Server) {
        server.addGuardedTool(
            name = "mc_list_instances",
            description = "List every running Minecraft client with this mod on this machine: name, MCP url, port, Minecraft version and game directory. " +
                "Use it to find the other clients when testing with more than one.",
            toolAnnotations = ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        ) { _ -> jsonResult(registry.list()) }
    }
}
