package io.github.ummamute.driver.tools

import io.github.ummamute.driver.server.InstanceRegistry
import io.github.ummamute.driver.tools.ToolSupport.addGuardedTool
import io.github.ummamute.driver.tools.ToolSupport.jsonResult
import io.modelcontextprotocol.kotlin.sdk.server.Server

/** Tools about the running clients themselves, for setups with more than one client. */
internal object InstanceTools {
    /** Set once at startup, before the first request. */
    lateinit var registry: InstanceRegistry

    fun register(server: Server) {
        server.addGuardedTool(BuiltInTools["mc_list_instances"]) { _ -> jsonResult(registry.list()) }
    }
}
