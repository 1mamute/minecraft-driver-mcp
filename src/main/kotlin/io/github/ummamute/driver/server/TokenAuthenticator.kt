package io.github.ummamute.driver.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Decides whether a request may reach the tools. With no token configured every request is allowed; with a token,
 * the `Authorization` header must be `Bearer <token>`. The comparison is constant-time and the token is never logged.
 */
class TokenAuthenticator(token: String?) {
    private val expected: ByteArray? = token?.takeIf { it.isNotEmpty() }?.let(::digest)

    /** True when a token is configured, so clients must send it. */
    val isRequired: Boolean = expected != null

    /** @param authorization the raw `Authorization` header value, or null when absent. */
    fun isAllowed(authorization: String?): Boolean {
        val required = expected ?: return true
        val presented = bearerOf(authorization) ?: return false
        return MessageDigest.isEqual(required, digest(presented))
    }

    private fun bearerOf(authorization: String?): String? {
        if (authorization == null || !authorization.startsWith(SCHEME, ignoreCase = true)) return null
        return authorization.substring(SCHEME.length)
    }

    /** Hashing first gives both sides the same length, so the comparison leaks nothing about the token length. */
    private fun digest(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))

    private companion object {
        const val SCHEME = "Bearer "
    }
}
