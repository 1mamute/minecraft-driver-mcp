package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.PlayerPosition
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ScreenshotResultsTest {
    @Test
    fun `inline capture preserves image bytes first and appends matching metadata`() {
        val png = byteArrayOf(0, 1, -1, 127)
        val metadata = metadata().copy(
            player = PlayerPosition(x = 1.5, y = 64.0, z = -3.0, yaw = 45f, pitch = -10f),
            worldTime = 12345L,
            evidence = ScreenshotEvidencePaths("evidence/capture.png", "evidence/capture.json"),
        )

        val result = ScreenshotResults.inline(png, metadata)

        assertEquals(2, result.content.size)
        val image = assertIs<ImageContent>(result.content[0])
        assertEquals("image/png", image.mimeType)
        assertContentEquals(png, Base64.getDecoder().decode(image.data))
        val text = assertIs<TextContent>(result.content[1])
        assertEquals(metadata, ToolSupport.json.decodeFromString<ScreenshotMetadata>(text.text))
    }

    @Test
    fun `title screen capture serializes absent world state as null`() {
        val metadata = metadata()
        val result = ScreenshotResults.inline(byteArrayOf(), metadata)
        val text = assertIs<TextContent>(result.content[1])

        assertEquals(metadata, ToolSupport.json.decodeFromString<ScreenshotMetadata>(text.text))
        assertNull(metadata.player)
        assertNull(metadata.worldTime)
    }

    private fun metadata() = ScreenshotMetadata(
        framebuffer = ScreenshotDimensions(1001, 701),
        gui = ScreenshotDimensions(334, 234),
        guiScale = 3.0,
        instance = ScreenshotInstance("test-client", 1234L),
        screen = "TitleScreen",
        capturedAt = 1_800_000_000_123L,
        player = null,
        worldTime = null,
    )
}
