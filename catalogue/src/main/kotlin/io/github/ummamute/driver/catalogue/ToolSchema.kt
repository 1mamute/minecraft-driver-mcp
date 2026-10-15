package io.github.ummamute.driver.catalogue

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Builds an object schema. Each property is a name with its JSON type and description. */
internal fun schema(vararg properties: Property, required: List<String> = emptyList()): ToolSchema = schema(properties.toList(), required)

/** Builds an object schema from a list of properties. */
internal fun schema(properties: List<Property>, required: List<String>): ToolSchema {
    val body = buildJsonObject {
        properties.forEach { property ->
            putJsonObject(property.name) {
                put("type", property.type)
                put("description", property.description)
                property.allowed?.let { allowed -> put("enum", buildJsonArray { allowed.forEach { add(JsonPrimitive(it)) } }) }
            }
        }
    }
    return ToolSchema(properties = body, required = required.ifEmpty { null })
}

/** One input property of a built-in tool. */
internal data class Property(val name: String, val type: String, val description: String, val allowed: List<String>? = null)
