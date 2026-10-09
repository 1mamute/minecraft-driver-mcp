package io.github.ummamute.driver.api

/** JSON type of a tool input property. */
enum class PropertyType(val json: String) {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
}

/**
 * One input property of a tool.
 *
 * @property allowed when set, the agent must send one of these values (a string property only)
 */
class ToolProperty @JvmOverloads constructor(
    val name: String,
    val type: PropertyType,
    val description: String,
    val required: Boolean = false,
    val allowed: List<String>? = null,
)

/**
 * A tool an extension adds. Build one with [builder]:
 *
 * ```java
 * ToolDefinition.builder("mymod_read_score", "Reads the score of a player.")
 *     .property("player", PropertyType.STRING, "Player name", true)
 *     .readOnly(true).destructive(false).idempotent(true).openWorld(false)
 *     .handler(args -> ToolResult.text("{\"score\": 3}"))
 *     .build();
 * ```
 */
class ToolDefinition private constructor(builder: Builder) {
    val name: String = builder.name
    val description: String = builder.description
    val properties: List<ToolProperty> = builder.properties.toList()
    val readOnly: Boolean = builder.readOnly
    val destructive: Boolean = builder.destructive
    val idempotent: Boolean = builder.idempotent
    val openWorld: Boolean = builder.openWorld

    /** Whether the handler runs on the render thread (the default), the only thread allowed to touch the game. */
    val onRenderThread: Boolean = builder.onRenderThread
    val handler: ToolHandler = requireNotNull(builder.handler) { "Tool \"$name\" has no handler" }

    /** Collects the parts of a [ToolDefinition]. The annotation defaults are the MCP defaults: not read-only, destructive, open world. */
    class Builder internal constructor(internal val name: String, internal val description: String) {
        internal val properties = mutableListOf<ToolProperty>()
        internal var readOnly = false
        internal var destructive = true
        internal var idempotent = false
        internal var openWorld = true
        internal var onRenderThread = true
        internal var handler: ToolHandler? = null

        /** Adds an input property. */
        @JvmOverloads
        fun property(name: String, type: PropertyType, description: String, required: Boolean = false, allowed: List<String>? = null) = apply {
            properties += ToolProperty(name, type, description, required, allowed)
        }

        /** The tool only reads state. */
        fun readOnly(value: Boolean) = apply { readOnly = value }

        /** The tool may destroy or overwrite something. Meaningful only when not read-only. */
        fun destructive(value: Boolean) = apply { destructive = value }

        /** Calling the tool again with the same arguments changes nothing more. */
        fun idempotent(value: Boolean) = apply { idempotent = value }

        /** The tool reaches outside the game (network, files). */
        fun openWorld(value: Boolean) = apply { openWorld = value }

        /**
         * Whether [handler] runs on the render thread. Leave it `true` when the handler touches screens, the player
         * or the level. Pass `false` for work that does not touch the game; the handler then runs on an endpoint worker
         * thread and must return quickly.
         */
        fun onRenderThread(value: Boolean) = apply { onRenderThread = value }

        /** Sets what runs when the agent calls the tool. */
        fun handler(value: ToolHandler) = apply { handler = value }

        /** @throws IllegalArgumentException when the description, a property or the handler is invalid */
        fun build(): ToolDefinition {
            require(description.isNotBlank()) { "Tool \"$name\" needs a description" }
            val duplicate = properties.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }
            require(duplicate == null) { "Tool \"$name\" declares property \"${duplicate?.key}\" twice" }
            require(properties.none { it.name.isBlank() }) { "Tool \"$name\" has a property without a name" }
            return ToolDefinition(this)
        }
    }

    companion object {
        /** Starts a definition. [name] must follow the rules in [ToolRegistrar.register]. */
        @JvmStatic
        fun builder(name: String, description: String): Builder = Builder(name, description)
    }
}
