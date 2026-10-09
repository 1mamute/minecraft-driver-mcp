package io.github.ummamute.driver.api

/** Adds tools on behalf of one mod. Handed to [DriverExtension.registerTools]. */
interface ToolRegistrar {
    /** Id of the mod that owns this registrar, as declared in its `fabric.mod.json`. */
    val modId: String

    /**
     * Registers [tool]. The name must be `<modid>_<verb>_<noun>` in snake case, where `<modid>` is the mod id with
     * dashes turned into underscores, and must not collide with a tool already registered.
     *
     * @return `true` when the tool was added; `false` when it was rejected (the reason is logged as a warning).
     */
    fun register(tool: ToolDefinition): Boolean
}
