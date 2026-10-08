package com.arjun.soul_note.core.error

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Identity Toolkit answers with a machine code in the message text (`CODE: prose`) and the
 * client is the only place that code is still visible, so [authReasonFrom] is what decides
 * whether the user is told they mistyped a password or that they simply need to sign in
 * again. The second case is the one worth pinning: an expired token and a
 * `CREDENTIAL_TOO_OLD_LOGIN_AGAIN` both mean "sign in again", and conflating the latter with
 * an unrecognised code is what made account deletion report `UNKNOWN` for a long time.
 */
class AuthReasonTest {

    @Test
    fun `an expired token reads as a session to sign in again`() {
        assertEquals(
            AuthenticationReason.SESSION_EXPIRED,
            authReasonFrom("TOKEN_EXPIRED : Firebase id token has expired"),
        )
    }

    @Test
    fun `a rejected id token reads as a session to sign in again`() {
        assertEquals(
            AuthenticationReason.SESSION_EXPIRED,
            authReasonFrom("INVALID_ID_TOKEN : Firebase id token is invalid"),
        )
    }

    @Test
    fun `deleting an account from an old sign-in reads as a session to sign in again`() {
        assertEquals(
            AuthenticationReason.SESSION_EXPIRED,
            authReasonFrom(
                "CREDENTIAL_TOO_OLD_LOGIN_AGAIN : Recent sign-in required.",
            ),
        )
    }

    @Test
    fun `a wrong password is not mistaken for a stale session`() {
        assertEquals(
            AuthenticationReason.INVALID_CREDENTIALS,
            authReasonFrom("INVALID_LOGIN_CREDENTIALS : The password is invalid"),
        )
    }

    @Test
    fun `an unrecognised code stays unknown rather than guessing`() {
        assertEquals(
            AuthenticationReason.UNKNOWN,
            authReasonFrom("SOMETHING_NEW : unrecognised"),
        )
    }

    @Test
    fun `a missing detail does not resolve to a reason`() {
        assertEquals(AuthenticationReason.UNKNOWN, authReasonFrom(null))
    }
}
