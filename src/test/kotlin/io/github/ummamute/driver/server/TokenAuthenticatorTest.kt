package io.github.ummamute.driver.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenAuthenticatorTest {
    private val authenticator = TokenAuthenticator("s3cret")

    @Test
    fun `allows every request when no token is set`() {
        val open = TokenAuthenticator(null)
        assertFalse(open.isRequired)
        assertTrue(open.isAllowed(null))
        assertTrue(open.isAllowed("Bearer anything"))
    }

    @Test
    fun `treats an empty token as unset`() {
        assertFalse(TokenAuthenticator("").isRequired)
    }

    @Test
    fun `allows the correct bearer token`() {
        assertTrue(authenticator.isRequired)
        assertTrue(authenticator.isAllowed("Bearer s3cret"))
    }

    @Test
    fun `accepts the scheme in any case`() {
        assertTrue(authenticator.isAllowed("bearer s3cret"))
    }

    @Test
    fun `rejects a missing header`() {
        assertFalse(authenticator.isAllowed(null))
    }

    @Test
    fun `rejects a wrong token`() {
        assertFalse(authenticator.isAllowed("Bearer s3cre"))
        assertFalse(authenticator.isAllowed("Bearer s3cret2"))
        assertFalse(authenticator.isAllowed("Bearer "))
    }

    @Test
    fun `rejects other schemes and a bare token`() {
        assertFalse(authenticator.isAllowed("s3cret"))
        assertFalse(authenticator.isAllowed("Basic s3cret"))
    }
}
