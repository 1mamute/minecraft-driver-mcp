package io.github.ummamute.driver.catalogue

import io.modelcontextprotocol.kotlin.sdk.types.Tool

/** Built-in MCP tool definitions, usable without Minecraft, Fabric or an MCP server. */
object BuiltInTools {
    /** All built-in tools in the order registered by the driver. Runtime extension tools are separate. */
    val all: List<Tool> = InstanceDefinitions.tools + ObservationDefinitions.tools + ActionDefinitions.tools + ConnectionDefinitions.tools

    private val byName = all.associateBy(Tool::name)

    /** Returns a built-in definition, failing immediately if a handler names an unknown tool. */
    operator fun get(name: String): Tool = requireNotNull(byName[name]) { "Unknown built-in tool: $name" }
}
