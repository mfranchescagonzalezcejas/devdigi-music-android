package dev.devdigi.music.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class ReduceAuthResultTest {
    private val metadata = ServerMetadata("navidrome", "0.54.1", true)

    @Test
    fun authenticatedMapsToReachableCompatibleAuthenticated() {
        val facts = reduceAuthResult(AuthResult.Authenticated(metadata))

        assertEquals(Reachability.REACHABLE, facts.reachability)
        assertEquals(Compatibility.COMPATIBLE, facts.compatibility)
        assertEquals(Authentication.AUTHENTICATED, facts.authentication)
    }

    @Test
    fun invalidCredentialsMapsToReachableNotCheckedRejected() {
        val facts = reduceAuthResult(AuthResult.InvalidCredentials)

        assertEquals(Reachability.REACHABLE, facts.reachability)
        assertEquals(Compatibility.NOT_CHECKED, facts.compatibility)
        assertEquals(Authentication.REJECTED, facts.authentication)
    }

    @Test
    fun unsupportedAuthenticationMapsToReachableWithNoCompatibilityOrAuthClaim() {
        val facts = reduceAuthResult(AuthResult.UnsupportedAuthentication)

        assertEquals(Reachability.REACHABLE, facts.reachability)
        assertEquals(Compatibility.NOT_CHECKED, facts.compatibility)
        assertEquals(Authentication.NOT_CHECKED, facts.authentication)
    }

    @Test
    fun authProtocolErrorMapsToReachableWithNoCompatibilityOrAuthClaim() {
        val facts = reduceAuthResult(AuthResult.AuthProtocolError)

        assertEquals(Reachability.REACHABLE, facts.reachability)
        assertEquals(Compatibility.NOT_CHECKED, facts.compatibility)
        assertEquals(Authentication.NOT_CHECKED, facts.authentication)
    }

    @Test
    fun incompatibleServerMapsToReachableIncompatibleNotChecked() {
        val facts = reduceAuthResult(AuthResult.IncompatibleServer)

        assertEquals(Reachability.REACHABLE, facts.reachability)
        assertEquals(Compatibility.INCOMPATIBLE, facts.compatibility)
        assertEquals(Authentication.NOT_CHECKED, facts.authentication)
    }

    @Test
    fun networkErrorMapsToUnreachable() {
        val facts = reduceAuthResult(AuthResult.NetworkError)

        assertEquals(Reachability.UNREACHABLE, facts.reachability)
        assertEquals(Compatibility.NOT_CHECKED, facts.compatibility)
        assertEquals(Authentication.NOT_CHECKED, facts.authentication)
    }
}
