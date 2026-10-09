package io.github.ummamute.driver.client

import org.apache.logging.log4j.LogManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogCaptureTest {
    @AfterTest
    fun detach() {
        LogCapture.uninstall()
    }

    @Test
    fun `captures a line with its throwable`() {
        LogCapture.install()
        val before = LogCapture.buffer.latest()

        LogManager.getLogger("capture-test").error("Something failed", IllegalStateException("boom"))

        val line = LogCapture.buffer.read(LogQuery(since = before)).lines.single { it.message == "Something failed" }
        assertEquals("ERROR", line.level)
        val throwable = assertNotNull(line.throwable)
        assertTrue(throwable.startsWith("java.lang.IllegalStateException: boom"))
        assertTrue(throwable.contains("\n    at "))
    }

    @Test
    fun `stops capturing after uninstall`() {
        LogCapture.install()
        LogCapture.uninstall()
        val before = LogCapture.buffer.latest()

        LogManager.getLogger("capture-test").error("After uninstall")

        assertEquals(before, LogCapture.buffer.latest())
    }
}
