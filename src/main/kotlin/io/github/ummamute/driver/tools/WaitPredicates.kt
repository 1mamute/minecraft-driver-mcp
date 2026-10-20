package io.github.ummamute.driver.tools

import io.github.ummamute.driver.tools.ToolSupport.long
import io.github.ummamute.driver.tools.ToolSupport.string
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/** Inclusive item-count bounds across the player's inventory, armor and offhand. */
@Serializable
data class InventoryPredicate(
    val item: String,
    @SerialName("min_count") val minCount: Int = 1,
    @SerialName("max_count") val maxCount: Int? = null,
) {
    init {
        requireRegistryId(item, "inventory.item")
        requireCountBounds(minCount, maxCount, "inventory")
    }

    /** Whether the observed total falls within the requested inclusive bounds. */
    fun matches(count: Int): Boolean = count >= minCount && (maxCount == null || count <= maxCount)
}

/** Inclusive counts of an exact entity type inside an exclusive radius from the player's feet. */
@Serializable
data class EntityPredicate(
    val type: String,
    val radius: Double = EntityQuery.DEFAULT_RADIUS,
    @SerialName("min_count") val minCount: Int = 1,
    @SerialName("max_count") val maxCount: Int? = null,
) {
    init {
        requireRegistryId(type, "entity.type")
        requireCountBounds(minCount, maxCount, "entity")
        require(radius.isFinite() && radius > 0 && radius <= EntityQuery.MAX_RADIUS) {
            "entity.radius must be greater than 0 and at most ${EntityQuery.MAX_RADIUS}. Pass a finite distance in blocks"
        }
    }

    /** Whether the observed nearby count falls within the requested inclusive bounds. */
    fun matches(count: Int): Boolean = count >= minCount && (maxCount == null || count <= maxCount)
}

/** Inclusive health bounds in points (two points per heart), excluding absorption. */
@Serializable
data class HealthPredicate(val min: Double? = null, val max: Double? = null) {
    init {
        require(min != null || max != null) { "health needs min or max. Example: {\"health\":{\"min\":20}}" }
        require(listOfNotNull(min, max).all { it.isFinite() && it >= 0 }) {
            "health.min and health.max must be finite nonnegative numbers. Pass health points, two per heart"
        }
        require(min == null || max == null || min <= max) { "health.min must not exceed health.max. Pass inclusive bounds in ascending order" }
    }

    /** Whether the observed health points fall within the requested inclusive bounds. */
    fun matches(health: Double): Boolean = (min == null || health >= min) && (max == null || health <= max)
}

/** How supplied predicates combine; the default preserves the original first-match behavior. */
enum class WaitMode { ANY, ALL }

/** Parses and validates the public wait arguments before any game access. */
internal object WaitArguments {
    fun condition(args: JsonObject, latest: Long): WaitCondition {
        val mode = when (args.string("mode") ?: "any") {
            "any" -> WaitMode.ANY
            "all" -> WaitMode.ALL
            else -> error("mode must be any or all. Use all to require every supplied condition in the same poll")
        }
        return WaitCondition(
            messageContains = args.string("message"),
            screenContains = args.string("screen"),
            sinceSeq = args.long("since") ?: latest,
            inventory = predicate<InventoryPredicate>(args, "inventory"),
            entity = predicate<EntityPredicate>(args, "entity"),
            health = predicate<HealthPredicate>(args, "health"),
            mode = mode,
        )
    }

    private inline fun <reified T> predicate(args: JsonObject, name: String): T? {
        val value = args[name]?.takeUnless { it is JsonNull } ?: return null
        require(value is JsonObject) { "$name must be an object. See mc_wait_for's input schema for its fields" }
        requireNumberLiterals(value, name)
        return try {
            ToolSupport.json.decodeFromJsonElement<T>(value)
        } catch (problem: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid $name predicate: ${problem.message}. Pass the fields shown in mc_wait_for's input schema", problem)
        }
    }

    private fun requireNumberLiterals(value: JsonObject, name: String) {
        for (field in listOf("min_count", "max_count", "radius", "min", "max")) {
            val number = value[field]
            require(number !is JsonPrimitive || !number.isString) {
                "$name.$field must be a number. Pass a JSON number without quotes"
            }
        }
    }
}

private fun requireRegistryId(value: String, field: String) {
    require(REGISTRY_ID.matches(value)) { "$field must be a full registry ID. Example: minecraft:stone; use the matching observation tool to find IDs" }
}

private fun requireCountBounds(min: Int, max: Int?, field: String) {
    require(min >= 0 && (max == null || max >= min)) {
        "$field needs nonnegative min_count <= max_count. To wait for absence, pass min_count:0 and max_count:0"
    }
}

private val REGISTRY_ID = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")
