package io.github.ummamute.driver.tools

import io.github.ummamute.driver.api.ToolDefinition
import io.github.ummamute.driver.api.ToolRegistrar
import org.slf4j.LoggerFactory

/** Collects the tools extensions register, rejecting invalid names and duplicates with a logged warning. */
internal class ExtensionRegistry {
    private val logger = LoggerFactory.getLogger(ExtensionRegistry::class.java)
    private val accepted = LinkedHashMap<String, Owned>()

    /** The accepted tools, in registration order. */
    val tools: List<ToolDefinition>
        @Synchronized get() = accepted.values.map { it.tool }

    /** A registrar that adds tools on behalf of [modId]. */
    fun registrarFor(modId: String): ToolRegistrar = object : ToolRegistrar {
        override val modId: String = modId

        override fun register(tool: ToolDefinition): Boolean = accept(modId, tool)
    }

    @Synchronized
    private fun accept(modId: String, tool: ToolDefinition): Boolean {
        val problem = ToolNames.problem(modId, tool.name)
        val owner = accepted[tool.name]?.modId
        return when {
            problem != null -> reject(modId, tool.name, "invalid name: $problem")
            owner != null -> reject(modId, tool.name, "a tool with this name is already registered by \"$owner\"")
            else -> {
                accepted[tool.name] = Owned(modId, tool)
                logger.info("Mod \"{}\" registered MCP tool {}", modId, tool.name)
                true
            }
        }
    }

    private fun reject(modId: String, name: String, reason: String): Boolean {
        logger.warn("Ignoring MCP tool \"{}\" from mod \"{}\": {}", name, modId, reason)
        return false
    }

    private class Owned(val modId: String, val tool: ToolDefinition)
}
