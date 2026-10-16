package io.github.ummamute.driver.tools

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.util.Base64

/** Keeps the inline PNG first and appends JSON metadata for coordinate conversion and capture identity. */
internal object ScreenshotResults {
    fun inline(png: ByteArray, metadata: ScreenshotMetadata): CallToolResult = CallToolResult(
        content = listOf(
            ImageContent(data = Base64.getEncoder().encodeToString(png), mimeType = "image/png"),
            TextContent(text = ToolSupport.json.encodeToString(metadata)),
        ),
    )
}
