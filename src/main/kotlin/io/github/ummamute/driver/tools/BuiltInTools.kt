package io.github.ummamute.driver.tools

import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Loads the gateway-owned built-in definitions bundled into the mod at build time. */
internal object BuiltInTools {
    private val catalogue = loadCatalogue()

    val version: String = catalogue.version
    val all: List<Tool> = catalogue.tools
    private val byName = all.associateBy(Tool::name)

    init {
        require(byName.size == all.size) { "Duplicate built-in tool names in the bundled catalogue" }
    }

    /** Returns the definition whose game handler is being registered. */
    operator fun get(name: String): Tool = requireNotNull(byName[name]) { "Unknown built-in tool: $name" }

    private fun loadCatalogue(): Catalogue {
        val resource = requireNotNull(javaClass.getResourceAsStream("/minecraft-driver-mcp/catalogue.json")) {
            "Missing built-in catalogue; rebuild the mod with gateway/catalogue.json"
        }
        return resource.bufferedReader(Charsets.UTF_8).use { Json.decodeFromString<Catalogue>(it.readText()) }
    }

    @Serializable
    private data class Catalogue(val version: String, val tools: List<Tool>)
}
