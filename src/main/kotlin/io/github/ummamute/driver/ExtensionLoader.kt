package io.github.ummamute.driver

import io.github.ummamute.driver.api.DriverExtension
import io.github.ummamute.driver.api.ToolDefinition
import io.github.ummamute.driver.tools.ExtensionRegistry
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.entrypoint.EntrypointContainer
import org.slf4j.LoggerFactory

/** Finds the mods that declare a `minecraft-driver-mcp` entrypoint and collects the tools they register. */
internal object ExtensionLoader {
    const val ENTRYPOINT_KEY = "minecraft-driver-mcp"
    private val logger = LoggerFactory.getLogger(ExtensionLoader::class.java)

    /** Calls every extension once. A failing extension is logged and skipped; it never stops the others or the endpoint. */
    fun load(): List<ToolDefinition> {
        val registry = ExtensionRegistry()
        val containers = runCatching { FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT_KEY, DriverExtension::class.java) }
            .getOrElse { failure ->
                logger.warn("Could not read the {} entrypoints; no extension tools are available", ENTRYPOINT_KEY, failure)
                emptyList()
            }
        containers.forEach { container -> loadOne(container, registry) }
        return registry.tools
    }

    private fun loadOne(container: EntrypointContainer<DriverExtension>, registry: ExtensionRegistry) {
        val modId = container.provider.metadata.id
        runCatching { container.entrypoint.registerTools(registry.registrarFor(modId)) }
            .onFailure { failure -> logger.warn("Extension of mod \"{}\" failed while registering tools; its other tools are kept", modId, failure) }
    }
}
