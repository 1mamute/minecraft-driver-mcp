package io.github.ummamute.driver.api

/** Runs a tool call. An exception thrown by [handle] becomes an error result; it never reaches the endpoint. */
fun interface ToolHandler {
    /** Handles one call with the arguments the agent sent. */
    fun handle(arguments: ToolArguments): ToolResult
}
