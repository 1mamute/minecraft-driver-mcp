package io.github.ummamute.driver.client

import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

/** One chat or system message the client received. */
@Serializable
data class LoggedMessage(val seq: Long, val text: String)

/** Messages after a sequence number, and the sequence number to pass next time. */
@Serializable
data class MessagePage(val messages: List<LoggedMessage>, val latest: Long)

/** Bounded buffer of the chat and system messages this client received. */
object MessageLog {
    private const val CAPACITY = 200
    private val entries = ArrayDeque<LoggedMessage>()
    private var lastSeq = 0L

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> add(message.string) }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> add(message.string) }
    }

    @Synchronized
    private fun add(text: String) {
        lastSeq += 1
        entries.addLast(LoggedMessage(lastSeq, text))
        if (entries.size > CAPACITY) entries.removeFirst()
    }

    @Synchronized
    fun since(seq: Long): MessagePage = MessagePage(entries.filter { it.seq > seq }, lastSeq)

    /** Sequence number of the newest message, or 0 when none arrived yet. */
    @Synchronized
    fun latest(): Long = lastSeq
}
