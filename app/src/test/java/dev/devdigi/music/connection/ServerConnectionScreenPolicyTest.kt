package dev.devdigi.music.connection

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionScreenPolicyTest {
    @Test
    fun compactPhoneUsesCompactPadding() {
        val layout =
            connectionLayoutSpec(360.dp)

        assertEquals(16.dp, layout.horizontalPadding)
        assertEquals(20.dp, layout.verticalPadding)
        assertEquals(560.dp, layout.maxFormWidth)
    }

    @Test
    fun regularPhoneAndSplitWindowUseRegularPadding() {
        for (width in listOf(411.dp, 480.dp, 599.dp)) {
            val layout =
                connectionLayoutSpec(width)

            assertEquals(
                24.dp,
                layout.horizontalPadding,
            )
            assertEquals(
                560.dp,
                layout.maxFormWidth,
            )
        }
    }

    @Test
    fun wideLandscapeAndTabletConstrainFormWidth() {
        for (width in listOf(600.dp, 800.dp, 1200.dp)) {
            val layout =
                connectionLayoutSpec(width)

            assertEquals(
                32.dp,
                layout.horizontalPadding,
            )
            assertEquals(
                560.dp,
                layout.maxFormWidth,
            )
        }
    }

    @Test
    fun unsavedEndpointDraftCannotAuthenticateAgainstOldServer() {
        val savedProfile =
            ServerProfile(
                endpoint(
                    "https://old.example.com",
                ),
            )

        val policy =
            connectionUiPolicy(
                ServerConnectionUiState(
                    endpointInput =
                        "https://new.example.com",
                    usernameInput = "Alice",
                    passwordInput = "secret",
                    profile = savedProfile,
                    urlValidity =
                        UrlValidity.UNCHECKED,
                    sessionStatus =
                        SessionStatus.SIGNED_OUT,
                ),
            )

        assertFalse(policy.serverDraftSaved)
        assertFalse(policy.canSignIn)
        assertTrue(policy.canSaveServer)
    }

    @Test
    fun savedServerAndCredentialsPermitSignIn() {
        val profile =
            ServerProfile(
                endpoint(
                    "https://music.example.com",
                ),
            )

        val policy =
            connectionUiPolicy(
                ServerConnectionUiState(
                    endpointInput =
                        profile.endpoint.value,
                    usernameInput = "Alice",
                    passwordInput = "secret",
                    profile = profile,
                    urlValidity =
                        UrlValidity.Valid(profile),
                    sessionStatus =
                        SessionStatus.SIGNED_OUT,
                ),
            )

        assertTrue(policy.serverDraftSaved)
        assertTrue(policy.canSignIn)
        assertFalse(policy.canSaveServer)
    }

    @Test
    fun authenticatedSessionOffersSignOutNotCredentialEditing() {
        val profile =
            ServerProfile(
                endpoint(
                    "https://music.example.com",
                ),
            )

        val policy =
            connectionUiPolicy(
                ServerConnectionUiState(
                    endpointInput =
                        profile.endpoint.value,
                    profile = profile,
                    urlValidity =
                        UrlValidity.Valid(profile),
                    sessionStatus =
                        SessionStatus.AUTHENTICATED,
                ),
            )

        assertFalse(policy.credentialsEditable)
        assertFalse(policy.canSignIn)
        assertTrue(policy.canSignOut)
    }

    @Test
    fun failedSignOutOffersRetryWithoutCredentialEditing() {
        val policy =
            connectionUiPolicy(
                ServerConnectionUiState(
                    sessionStatus =
                        SessionStatus.SIGN_OUT_FAILED,
                ),
            )

        assertFalse(policy.credentialsEditable)
        assertFalse(policy.canSignIn)
        assertTrue(policy.canSignOut)
    }

    @Test
    fun restoringAndSigningInDisableMutatingActions() {
        for (
        status in
        listOf(
            SessionStatus.RESTORING,
            SessionStatus.SIGNING_IN,
        )
        ) {
            val profile =
                ServerProfile(
                    endpoint(
                        "https://music.example.com",
                    ),
                )

            val policy =
                connectionUiPolicy(
                    ServerConnectionUiState(
                        endpointInput =
                            profile.endpoint.value,
                        usernameInput = "Alice",
                        passwordInput = "secret",
                        profile = profile,
                        urlValidity =
                            UrlValidity.Valid(profile),
                        sessionStatus = status,
                    ),
                )

            assertTrue(policy.busy)
            assertFalse(policy.credentialsEditable)
            assertFalse(policy.canSaveServer)
            assertFalse(policy.canSignIn)
            assertFalse(policy.canSignOut)
            assertFalse(policy.canDeleteServer)
        }
    }

    private fun endpoint(value: String): ServerEndpoint =
        (
            ServerEndpoint.parse(value)
                as EndpointParseResult.Valid
        ).endpoint
}
