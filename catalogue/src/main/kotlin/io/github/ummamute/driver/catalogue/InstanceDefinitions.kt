package io.github.ummamute.driver.catalogue

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

internal object InstanceDefinitions {
    val tools: List<Tool> = listOf(
        listInstances(),
    )

    private fun listInstances(): Tool {
        return Tool(
            name = "mc_list_instances",
            description = "List every running Minecraft client with this mod on this machine: name, MCP url, port, Minecraft version and game directory. " +
                "Use it to find the other clients when testing with more than one.",
            inputSchema = ToolSchema(),
            annotations = ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false),
        )
    }
}
