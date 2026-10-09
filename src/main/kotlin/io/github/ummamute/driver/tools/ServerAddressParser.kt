package io.github.ummamute.driver.tools

/** A server host with its port. */
internal data class HostPort(val host: String, val port: Int)

/** Splits `host`, `host:port`, `[ipv6]` and `[ipv6]:port` into a host and a port. */
internal object ServerAddressParser {
    const val DEFAULT_PORT = 25565
    private const val MAX_PORT = 65535

    /** @throws IllegalStateException with a message the agent can act on when the address is not usable. */
    fun parse(address: String): HostPort {
        val text = address.trim()
        check(text.isNotEmpty() && text.none(Char::isWhitespace)) { "Give an address like \"localhost\" or \"example.com:25566\"" }
        if (text.startsWith("[")) return parseBracketed(text)
        if (text.count { it == ':' } > 1) return HostPort(text, DEFAULT_PORT)
        val host = text.substringBefore(':')
        val port = if (text.contains(':')) text.substringAfter(':') else null
        return build(host, port)
    }

    private fun parseBracketed(text: String): HostPort {
        val end = text.indexOf(']')
        check(end > 1) { "The IPv6 address \"$text\" is missing its closing bracket; use [::1] or [::1]:25565" }
        val rest = text.substring(end + 1)
        check(rest.isEmpty() || rest.startsWith(":")) { "Unexpected text after the IPv6 address in \"$text\"; use [::1]:25565" }
        return build(text.substring(1, end), if (rest.isEmpty()) null else rest.drop(1))
    }

    private fun build(host: String, port: String?): HostPort {
        check(host.isNotEmpty()) { "The address has no host; give an address like \"localhost:25565\"" }
        if (port == null) return HostPort(host, DEFAULT_PORT)
        val number = port.toIntOrNull()
        check(number != null && number in 1..MAX_PORT) { "The port \"$port\" must be a number from 1 to $MAX_PORT" }
        return HostPort(host, number)
    }
}
