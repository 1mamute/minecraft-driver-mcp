package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.EntitySummary
import io.github.ummamute.driver.tools.ToolSupport.double
import io.github.ummamute.driver.tools.ToolSupport.int
import io.github.ummamute.driver.tools.ToolSupport.string
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Filters and bounds for client-visible entities, measured from the player's feet. */
data class EntityQuery(
    val radius: Double = DEFAULT_RADIUS,
    val limit: Int = DEFAULT_LIMIT,
    val type: String? = null,
    val typePrefix: String? = null,
    val nameContains: String? = null,
) {
    init {
        require(radius.isFinite() && radius > 0 && radius <= MAX_RADIUS) {
            "Argument \"radius\" must be greater than 0 and at most $MAX_RADIUS blocks. Pass a finite number"
        }
        require(limit in 1..MAX_LIMIT) { "Argument \"limit\" must be between 1 and $MAX_LIMIT. Pass an integer" }
    }

    /** Filters before sorting and limiting; the radius boundary is excluded. */
    fun select(entities: Sequence<EntitySummary>): EntityQueryResult {
        val matches = entities.filter(::matches).sortedBy(EntitySummary::distance).toList()
        val metadata = EntityQueryMetadata(total = matches.size, truncated = matches.size > limit, radius = radius, limit = limit)
        return EntityQueryResult(matches.take(limit), metadata)
    }

    private fun matches(entity: EntitySummary): Boolean {
        return entity.distance < radius &&
            (type == null || entity.type == type) &&
            (typePrefix == null || entity.type.startsWith(typePrefix)) &&
            (nameContains == null || entity.name.contains(nameContains, ignoreCase = true))
    }

    companion object {
        const val DEFAULT_RADIUS = 64.0
        const val MAX_RADIUS = 256.0
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 1000

        internal fun from(args: JsonObject): EntityQuery {
            val radiusValue = args["radius"]
            require(radiusValue !is JsonPrimitive || !radiusValue.isString) { "Argument \"radius\" must be a number. Pass a radius in blocks" }
            return EntityQuery(
                radius = args.double("radius") ?: DEFAULT_RADIUS,
                limit = args.int("limit") ?: DEFAULT_LIMIT,
                type = args.string("type"),
                typePrefix = args.string("type_prefix"),
                nameContains = args.string("name_contains"),
            )
        }
    }
}

/** Counts matching client-visible entities before applying the result limit. */
@Serializable
data class EntityQueryMetadata(val total: Int, val truncated: Boolean, val radius: Double, val limit: Int)

/** Preserves the entity array in the first text block and appends query metadata in the second. */
data class EntityQueryResult(val entities: List<EntitySummary>, val metadata: EntityQueryMetadata) {
    internal fun toToolResult(): CallToolResult = CallToolResult(
        content = listOf(
            TextContent(text = ToolSupport.json.encodeToString(entities)),
            TextContent(text = ToolSupport.json.encodeToString(metadata)),
        ),
    )
}
