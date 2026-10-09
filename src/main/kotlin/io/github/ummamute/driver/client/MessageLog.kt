package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

/** One chat or system message the client received. */
@Serializable
data class LoggedMessage(val seq: Long, val text: String)

/**
 * Messages after a sequence number, and the sequence number to pass next time.
 * [truncated] is true when messages after the requested number were already dropped from the buffer.
 */
@Serializable
data class MessagePage(val messages: List<LoggedMessage>, val latest: Long, val truncated: Boolean)

/** Bounded buffer of the chat and system messages this client received. Action-bar text is not recorded. */
object MessageLog {
    private const val CAPACITY = 200
    private val entries = ArrayDeque<LoggedMessage>()
    private var lastSeq = 0L

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) add(message.string) }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> add(message.string) }
    }

    @Synchronized
    private fun add(text: String) {
        lastSeq += 1
        entries.addLast(LoggedMessage(lastSeq, text))
        if (entries.size > CAPACITY) entries.removeFirst()
    }

    @Synchronized
    fun since(seq: Long): MessagePage {
        val oldest = entries.firstOrNull()?.seq ?: lastSeq + 1
        return MessagePage(entries.filter { it.seq > seq }, lastSeq, truncated = seq < oldest - 1)
    }

    /** Sequence number of the newest message, or 0 when none arrived yet. */
    @Synchronized
    fun latest(): Long = lastSeq
}
