package io.github.ummamute.driver.client

import net.minecraft.client.Minecraft
import net.minecraft.util.StringUtil

/** Sends chat and commands as the player. Call on the render thread. */
object ClientChat {
    /** Returns the text that was sent, after the same cleanup the chat screen applies. */
    fun send(text: String): String {
        val message = normalize(text)
        require(message.isNotEmpty()) { "Chat text is blank; pass a message or a /command with its arguments" }
        require(message != "/") { "The command is empty; pass /<command> with its arguments" }
        val illegal = message.firstOrNull { !StringUtil.isAllowedChatCharacter(it) }
        require(illegal == null) { "Chat text contains a character the server rejects (code ${illegal?.code}); remove it and send again" }
        val minecraft = Minecraft.getInstance()
        val connection = minecraft.connection ?: error("Not connected to a world or server. Join one with mc_join_world or mc_join_server, then send again")
        minecraft.gui.chat.addRecentChat(message)
        if (message.startsWith("/")) connection.sendCommand(message.removePrefix("/")) else connection.sendChat(message)
        return message
    }

    private fun normalize(text: String): String {
        val collapsed = text.trim().replace(WHITESPACE, " ")
        return StringUtil.trimChatMessage(collapsed)
    }

    private val WHITESPACE = Regex("""\s+""")
}
