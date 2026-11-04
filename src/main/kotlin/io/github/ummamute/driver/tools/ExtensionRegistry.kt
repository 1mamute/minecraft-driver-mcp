package io.github.ummamute.driver.tools

import io.github.ummamute.driver.api.ToolDefinition
import io.github.ummamute.driver.api.ToolRegistrar
import org.slf4j.LoggerFactory

/**
 * Collects the tools extensions register, rejecting invalid names and duplicates with a logged warning.
 *
 * Registration stays open for the client's lifetime and may come from any thread. A subscriber, normally the running
 * endpoint, receives each accepted tool before [ToolRegistrar.register] returns, so a `tools/list` that starts after
 * `register` returned `true` includes the tool.
 */
internal class ExtensionRegistry {
    private val logger = LoggerFactory.getLogger(ExtensionRegistry::class.java)
    private val accepted = LinkedHashMap<String, Owned>()
    private val subscribers = mutableListOf<Subscriber>()

    /** The accepted tools, in registration order. */
    val tools: List<ToolDefinition>
        @Synchronized get() = accepted.values.map { it.tool }

    /** A registrar that adds tools on behalf of [modId]. */
    fun registrarFor(modId: String): ToolRegistrar = object : ToolRegistrar {
        override val modId: String = modId

        override fun register(tool: ToolDefinition): Boolean = accept(modId, tool)
    }

    /**
     * Calls [onTool] with every tool accepted so far, then with each tool accepted later, each exactly once, until the
     * returned handle is closed. [onTool] runs on the registering thread while the registry is locked, so keep it short.
     */
    @Synchronized
    fun subscribe(onTool: (ToolDefinition) -> Unit): AutoCloseable {
        val subscriber = Subscriber(onTool)
        accepted.values.forEach { deliver(subscriber, it.tool) }
        subscribers += subscriber
        return AutoCloseable { unsubscribe(subscriber) }
    }

    @Synchronized
    private fun unsubscribe(subscriber: Subscriber) {
        subscribers -= subscriber
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
                subscribers.forEach { deliver(it, tool) }
                true
            }
        }
    }

    /** A failing subscriber must not undo the registration or keep the other subscribers from the tool. */
    private fun deliver(subscriber: Subscriber, tool: ToolDefinition) {
        runCatching { subscriber.onTool(tool) }
            .onFailure { failure -> logger.warn("Could not add MCP tool {} to the endpoint", tool.name, failure) }
    }

    private fun reject(modId: String, name: String, reason: String): Boolean {
        logger.warn("Ignoring MCP tool \"{}\" from mod \"{}\": {}", name, modId, reason)
        return false
    }

    private class Owned(val modId: String, val tool: ToolDefinition)

    // Identity, not equality: the same function subscribed twice is two subscriptions.
    private class Subscriber(val onTool: (ToolDefinition) -> Unit)
}
