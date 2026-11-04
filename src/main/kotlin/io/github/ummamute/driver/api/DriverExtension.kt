package io.github.ummamute.driver.api

/**
 * Entrypoint another Fabric mod implements to add its own MCP tools to the running endpoint.
 *
 * Declare it in the mod's `fabric.mod.json` under the `minecraft-driver-mcp` entrypoint key:
 *
 * ```json
 * "entrypoints": { "minecraft-driver-mcp": ["com.example.mymod.MyDriverExtension"] }
 * ```
 *
 * The API in this package is Minecraft-free and may change between 0.x releases.
 * See `docs/extension-api.md` for the naming rules and an example.
 */
fun interface DriverExtension {
    /**
     * Called once while the driver starts. Register the tools that are ready now through [registrar].
     * An exception thrown here is logged and does not affect the endpoint or other extensions.
     *
     * [registrar] stays valid for the client's lifetime: keep it to register more tools later, from any thread.
     */
    fun registerTools(registrar: ToolRegistrar)
}
