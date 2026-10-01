package dev.devdigi.music.integration

import dev.devdigi.music.connection.AuthCredentials
import dev.devdigi.music.connection.AuthResult
import dev.devdigi.music.connection.DefaultSubsonicAuthSigner
import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.OkHttpAuthenticatedPingClient
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.connection.ServerProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NavidromeIntegrationTest {
    @Test
    fun syntheticServerAuthenticatesThroughRealApplicationBoundary() {
        assumeTrue(
            System.getProperty("navidrome.integration") == "true",
        )

        val endpointValue =
            requiredEnv("NAVIDROME_INTEGRATION_URL")
        val username =
            requiredEnv("NAVIDROME_INTEGRATION_USERNAME")
        val password =
            requiredEnv("NAVIDROME_INTEGRATION_PASSWORD")

        val parsed =
            ServerEndpoint.parse(endpointValue)

        check(parsed is EndpointParseResult.Valid) {
            "Synthetic integration endpoint was rejected."
        }

        val profile =
            ServerProfile(parsed.endpoint)

        val signer =
            DefaultSubsonicAuthSigner()

        val authenticated =
            runBlocking {
                OkHttpAuthenticatedPingClient(
                    signer = signer,
                ).ping(
                    credentials =
                        AuthCredentials.create(
                            username = username,
                            password = password,
                        ),
                    profile = profile,
                )
            }

        assertTrue(
            authenticated is AuthResult.Authenticated,
        )

        val rejected =
            runBlocking {
                OkHttpAuthenticatedPingClient(
                    signer = signer,
                ).ping(
                    credentials =
                        AuthCredentials.create(
                            username = username,
                            password = "$password-invalid",
                        ),
                    profile = profile,
                )
            }

        assertEquals(
            AuthResult.InvalidCredentials,
            rejected,
        )
    }

    private fun requiredEnv(name: String): String =
        requireNotNull(
            System.getenv(name),
        ) {
            "$name is required by the integration runner."
        }.also {
            require(it.isNotBlank()) {
                "$name must not be blank."
            }
        }
}
