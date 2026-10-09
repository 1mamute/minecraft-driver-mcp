package io.github.ummamute.driver.api

/**
 * The arguments of one tool call. Values are the plain JSON types: strings, numbers, booleans, lists and maps.
 * The getters return `null` when the argument is missing or has another type; the `require` functions throw
 * [IllegalArgumentException], which the endpoint reports to the agent as an error result.
 */
class ToolArguments(private val values: Map<String, Any?>) {
    /** Whether the agent sent a non-null value for [name]. */
    fun has(name: String): Boolean = values[name] != null

    /** The raw value, for lists and objects. */
    fun get(name: String): Any? = values[name]

    fun getString(name: String): String? = values[name] as? String

    /** The value as an int, or `null` when it is missing, fractional or out of range. */
    fun getInt(name: String): Int? {
        val number = getDouble(name) ?: return null
        val isWhole = number % 1.0 == 0.0
        val inRange = number >= Int.MIN_VALUE && number <= Int.MAX_VALUE
        return if (isWhole && inRange) number.toInt() else null
    }

    fun getDouble(name: String): Double? = (values[name] as? Number)?.toDouble()

    fun getBoolean(name: String): Boolean? = values[name] as? Boolean

    fun requireString(name: String): String = getString(name) ?: throw missing(name, "a string")

    fun requireInt(name: String): Int = getInt(name) ?: throw missing(name, "an integer")

    fun requireDouble(name: String): Double = getDouble(name) ?: throw missing(name, "a number")

    fun requireBoolean(name: String): Boolean = getBoolean(name) ?: throw missing(name, "a boolean")

    private fun missing(name: String, expected: String) = IllegalArgumentException("Argument \"$name\" is required and must be $expected")
}
