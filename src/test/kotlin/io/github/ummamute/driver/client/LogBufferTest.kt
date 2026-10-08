package io.github.ummamute.driver.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogBufferTest {
    private fun bufferWith(vararg levels: String): LogBuffer {
        val buffer = LogBuffer()
        levels.forEachIndexed { index, level -> buffer.add(level, "logger", "main", "line $index") }
        return buffer
    }

    @Test
    fun `lines get increasing sequence numbers and since skips seen ones`() {
        val buffer = bufferWith("INFO", "INFO", "INFO")

        val page = buffer.read(LogQuery(since = 2))

        assertEquals(listOf(3L), page.lines.map { it.seq })
        assertEquals(3L, page.latest)
    }

    @Test
    fun `minimum level hides less severe lines`() {
        val buffer = bufferWith("DEBUG", "INFO", "WARN", "ERROR")

        val page = buffer.read(LogQuery(minLevel = "warn"))

        assertEquals(listOf("WARN", "ERROR"), page.lines.map { it.level })
    }

    @Test
    fun `contains ignores case and looks in message throwable and logger`() {
        val buffer = LogBuffer()
        buffer.add("INFO", "a.B", "main", "Hello World")
        buffer.add("ERROR", "a.C", "main", "boom", "java.lang.IllegalStateException: Mixin apply failed")
        buffer.add("INFO", "net.Mixin", "main", "other")
        buffer.add("INFO", "a.D", "main", "nothing")

        assertEquals(1, buffer.read(LogQuery(contains = "hello world")).lines.size)
        assertEquals(2, buffer.read(LogQuery(contains = "MIXIN")).lines.size)
    }

    @Test
    fun `max lines keeps the newest matches and reports truncation`() {
        val buffer = bufferWith("INFO", "INFO", "INFO", "INFO")

        val page = buffer.read(LogQuery(limit = 2))

        assertEquals(listOf(3L, 4L), page.lines.map { it.seq })
        assertTrue(page.truncated)
        assertFalse(buffer.read(LogQuery(limit = 4)).truncated)
    }

    @Test
    fun `the buffer drops the oldest lines past its capacity`() {
        val buffer = LogBuffer(capacity = 3)
        repeat(10) { buffer.add("INFO", "l", "t", "m$it") }

        val page = buffer.read(LogQuery())

        assertEquals(listOf(8L, 9L, 10L), page.lines.map { it.seq })
    }

    @Test
    fun `long messages and throwables are cut`() {
        val buffer = LogBuffer()
        buffer.add("ERROR", "l", "t", "x".repeat(LogBuffer.MAX_MESSAGE * 2), "y".repeat(LogBuffer.MAX_THROWABLE * 2))

        val line = buffer.read(LogQuery()).lines.single()

        assertTrue(line.message.length < LogBuffer.MAX_MESSAGE + 20)
        assertTrue(checkNotNull(line.throwable).length < LogBuffer.MAX_THROWABLE + 20)
    }

    @Test
    fun `a line without a throwable has none`() {
        val buffer = bufferWith("INFO")

        assertNull(buffer.read(LogQuery()).lines.single().throwable)
    }

    @Test
    fun `level names are recognised ignoring case`() {
        assertTrue(LogBuffer.isLevel("warn"))
        assertFalse(LogBuffer.isLevel("loud"))
    }
}
