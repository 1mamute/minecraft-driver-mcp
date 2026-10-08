package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable

/** One line of the game's log output. */
@Serializable
data class LogLine(
    val seq: Long,
    val level: String,
    val logger: String,
    val thread: String,
    val message: String,
    /** Exception class, message and first stack frames, when the event carried a throwable. */
    val throwable: String? = null,
)

/** Log lines after a sequence number, and the sequence number to pass next time. */
@Serializable
data class LogPage(
    val lines: List<LogLine>,
    val latest: Long,
    /** True when more lines matched than `lines` holds; the oldest matches were left out. */
    val truncated: Boolean,
)

/** What to keep when reading the log. A null field does not filter. */
data class LogQuery(val since: Long = 0L, val minLevel: String? = null, val contains: String? = null, val limit: Int = LogBuffer.DEFAULT_LIMIT)

/**
 * Bounded, thread-safe buffer of log lines with sequence numbers. Holds no Minecraft or Log4j types, so it can be tested alone.
 * Long messages and throwable texts are cut, and the oldest lines are dropped once the capacity is reached.
 */
class LogBuffer(private val capacity: Int = CAPACITY) {
    private val entries = ArrayDeque<LogLine>()
    private var lastSeq = 0L

    @Synchronized
    fun add(level: String, logger: String, thread: String, message: String, throwable: String? = null) {
        lastSeq += 1
        entries.addLast(LogLine(lastSeq, level, logger, thread, cut(message, MAX_MESSAGE), throwable?.let { cut(it, MAX_THROWABLE) }))
        if (entries.size > capacity) entries.removeFirst()
    }

    /** Lines matching [query], oldest first, at most the newest `limit` of them. */
    @Synchronized
    fun read(query: LogQuery): LogPage {
        val minRank = query.minLevel?.let(::rank) ?: 0
        val needle = query.contains?.takeIf { it.isNotEmpty() }
        val matches = entries.filter { matches(it, query.since, minRank, needle) }
        val limit = query.limit.coerceIn(1, MAX_LIMIT)
        return LogPage(matches.takeLast(limit), lastSeq, matches.size > limit)
    }

    /** Sequence number of the newest line, or 0 when none arrived yet. */
    @Synchronized
    fun latest(): Long = lastSeq

    private fun matches(line: LogLine, since: Long, minRank: Int, needle: String?): Boolean {
        if (line.seq <= since || rank(line.level) < minRank) return false
        if (needle == null) return true
        return listOfNotNull(line.message, line.throwable, line.logger).any { it.contains(needle, ignoreCase = true) }
    }

    private fun cut(text: String, max: Int): String = if (text.length <= max) text else text.take(max) + "... [cut]"

    companion object {
        const val CAPACITY = 1000
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 500
        const val MAX_MESSAGE = 2000
        const val MAX_THROWABLE = 4000

        /** Log levels from least to most severe. */
        val LEVELS = listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")

        /** Whether [level] names a level in [LEVELS], ignoring case. */
        fun isLevel(level: String): Boolean = LEVELS.contains(level.uppercase())

        private fun rank(level: String): Int = LEVELS.indexOf(level.uppercase())
    }
}
