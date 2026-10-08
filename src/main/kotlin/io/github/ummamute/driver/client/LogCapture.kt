package io.github.ummamute.driver.client

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property

/**
 * Copies everything the game logs into a [LogBuffer] through a Log4j2 appender on the root logger.
 *
 * Each client captures its own process, so two clients never share lines even when they share `logs/latest.log`.
 * Log4j2 comes with Minecraft, so nothing is bundled.
 */
object LogCapture {
    private const val NAME = "MinecraftDriverLog"
    private const val STACK_FRAMES = 8

    val buffer = LogBuffer()
    private var appender: BufferAppender? = null

    /** Attaches the appender once; further calls do nothing. */
    @Synchronized
    fun install() {
        if (appender != null) return
        val created = BufferAppender(buffer)
        created.start()
        (LogManager.getRootLogger() as Logger).addAppender(created)
        appender = created
    }

    /** Detaches the appender. */
    @Synchronized
    fun uninstall() {
        val installed = appender ?: return
        (LogManager.getRootLogger() as Logger).removeAppender(installed)
        installed.stop()
        appender = null
    }

    private class BufferAppender(private val buffer: LogBuffer) : AbstractAppender(NAME, null, null, true, Property.EMPTY_ARRAY) {
        // Guards against an event produced while this thread is already inside append, so the appender never feeds on itself.
        private val appending = ThreadLocal.withInitial { false }

        // ignoreExceptions is true, so Log4j swallows a failure here instead of breaking the caller's logging.
        override fun append(event: LogEvent) {
            if (appending.get()) return
            appending.set(true)
            try {
                buffer.add(event.level.name(), event.loggerName.orEmpty(), event.threadName.orEmpty(), event.message.formattedMessage, describe(event.thrown))
            } finally {
                appending.set(false)
            }
        }

        private fun describe(thrown: Throwable?): String? {
            thrown ?: return null
            val frames = thrown.stackTrace.take(STACK_FRAMES).joinToString(separator = "") { "\n    at $it" }
            return "$thrown$frames"
        }
    }
}
