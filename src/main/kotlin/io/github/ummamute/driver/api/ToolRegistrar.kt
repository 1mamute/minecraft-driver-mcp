package io.github.ummamute.driver.api

/**
 * Adds tools on behalf of one mod. Handed to [DriverExtension.registerTools].
 *
 * A registrar stays valid for the client's lifetime and is safe to call from any thread, so a mod can register a tool
 * later, for example once its own state is ready. A tool cannot be removed once registered.
 */
interface ToolRegistrar {
    /** Id of the mod that owns this registrar, as declared in its `fabric.mod.json`. */
    val modId: String

    /**
     * Registers [tool]. The name must be `<modid>_<verb>_<noun>` in snake case, where `<modid>` is the mod id with
     * dashes turned into underscores, and must not collide with a tool already registered, at startup or later.
     *
     * When this returns `true`, every `tools/list` request that starts afterwards includes the tool. The endpoint
     * does not notify connected assistants; an assistant that cached the list sees the tool after it lists tools again.
     *
     * @return `true` when the tool was added; `false` when it was rejected (the reason is logged as a warning).
     */
    fun register(tool: ToolDefinition): Boolean
}
