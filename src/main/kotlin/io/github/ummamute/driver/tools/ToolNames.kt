package io.github.ummamute.driver.tools

/**
 * Naming rules for tools that extensions add. The built-in tools use the `mc_` prefix, so an extension name must start
 * with its own mod id instead: `<modid>_<verb>_<noun>`, where dashes in the mod id become underscores.
 */
internal object ToolNames {
    private const val MAX_LENGTH = 64
    private const val RESERVED_PREFIX = "mc"
    private val snakeCase = Regex("[a-z][a-z0-9]*(_[a-z0-9]+)*")

    /** The mod id as it appears in tool names: lowercase with dashes turned into underscores. */
    fun prefixFor(modId: String): String = modId.lowercase().replace('-', '_')

    /** Returns why [name] is not allowed for [modId], or `null` when it is. */
    fun problem(modId: String, name: String): String? {
        val prefix = prefixFor(modId)
        return when {
            prefix == RESERVED_PREFIX -> "the mod id \"$modId\" collides with the built-in mc_ prefix"
            !snakeCase.matches(prefix) -> "the mod id \"$modId\" cannot form a snake case prefix"
            name.length > MAX_LENGTH -> "it is longer than $MAX_LENGTH characters"
            !snakeCase.matches(name) -> "it is not snake case (lowercase letters, digits and single underscores, starting with a letter)"
            !name.startsWith("${prefix}_") -> "it must start with \"${prefix}_\", the mod id"
            !name.removePrefix("${prefix}_").contains('_') -> "it must read \"${prefix}_<verb>_<noun>\""
            else -> null
        }
    }
}
