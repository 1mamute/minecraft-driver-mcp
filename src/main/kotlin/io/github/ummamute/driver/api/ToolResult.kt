package io.github.ummamute.driver.api

/** What a tool returns to the agent: text (plain or a JSON string) or an error message. */
class ToolResult private constructor(val text: String, val isError: Boolean) {
    companion object {
        /** A successful result. Prefer a JSON string so the agent can parse it. */
        @JvmStatic
        fun text(text: String): ToolResult = ToolResult(text, isError = false)

        /** A failed result. Say what the agent should do next, not only what went wrong. */
        @JvmStatic
        fun error(message: String): ToolResult = ToolResult(message, isError = true)
    }
}
