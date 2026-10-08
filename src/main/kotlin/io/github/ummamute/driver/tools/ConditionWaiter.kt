package io.github.ummamute.driver.tools

import io.github.ummamute.driver.client.ClientStateSnapshot
import io.github.ummamute.driver.client.LoggedMessage
import io.github.ummamute.driver.client.MessagePage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** What to wait for. The wait ends when any given condition holds. */
data class WaitCondition(
    /** Text a message received after [sinceSeq] must contain, ignoring case. */
    val messageContains: String? = null,
    /** Text the open screen's class name must contain, ignoring case, or [NO_SCREEN] for no open screen. */
    val screenContains: String? = null,
    /** Messages with a sequence number above this count. */
    val sinceSeq: Long = 0L,
) {
    companion object {
        const val NO_SCREEN = "none"
    }
}

/** Outcome of a wait. [reason] is `message` or `screen` when [matched], and `timeout` otherwise. */
@Serializable
data class WaitResult(
    val matched: Boolean,
    val reason: String,
    val message: LoggedMessage? = null,
    /** Sequence number to pass as `since` to read only messages after this wait. */
    val latest: Long = 0L,
    val state: ClientStateSnapshot? = null,
)

/** Polls the client until a [WaitCondition] holds or the time runs out. Has no Minecraft dependency, so it can be tested with fakes. */
internal class ConditionWaiter(
    private val readState: suspend () -> ClientStateSnapshot,
    private val readMessages: (Long) -> MessagePage,
    private val pollMillis: Long = POLL_MILLIS,
) {
    suspend fun await(condition: WaitCondition, timeoutMillis: Long): WaitResult {
        val matched = withTimeoutOrNull(timeoutMillis) { pollUntilMatch(condition) }
        return matched ?: WaitResult(matched = false, reason = REASON_TIMEOUT, latest = readMessages(condition.sinceSeq).latest, state = readState())
    }

    private suspend fun pollUntilMatch(condition: WaitCondition): WaitResult {
        while (true) {
            val result = check(condition)
            if (result != null) return result
            delay(pollMillis)
        }
    }

    private suspend fun check(condition: WaitCondition): WaitResult? {
        val page = readMessages(condition.sinceSeq)
        val message = condition.messageContains?.let { text -> page.messages.firstOrNull { it.text.contains(text, ignoreCase = true) } }
        if (message != null) return WaitResult(matched = true, reason = REASON_MESSAGE, message = message, latest = page.latest)
        val state = readState()
        if (!screenMatches(condition.screenContains, state.screen)) return null
        return WaitResult(matched = true, reason = REASON_SCREEN, latest = page.latest, state = state)
    }

    private fun screenMatches(expected: String?, actual: String?): Boolean = when {
        expected == null -> false
        expected == WaitCondition.NO_SCREEN -> actual == null
        else -> actual?.contains(expected, ignoreCase = true) == true
    }

    private companion object {
        const val POLL_MILLIS = 50L
        const val REASON_MESSAGE = "message"
        const val REASON_SCREEN = "screen"
        const val REASON_TIMEOUT = "timeout"
    }
}
