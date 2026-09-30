package dev.devdigi.music.connection

import kotlinx.coroutines.flow.first

sealed interface SessionRestoreResult {
    data class Restored(
        val identity: ServerAccountIdentity,
        val metadata: ServerMetadata,
    ) : SessionRestoreResult

    data object CredentialRejected : SessionRestoreResult

    data object NotRestored : SessionRestoreResult
}

class SessionRestorer(
    private val repository: ServerProfileRepository,
    private val secretStore: AuthSecretStore,
    private val pingClient: AuthenticatedPingClient,
) {
    suspend fun restore(): SessionRestoreResult {
        val profile =
            repository.profile.first()
                ?: return SessionRestoreResult.NotRestored

        return restore(profile)
    }

    suspend fun restore(profile: ServerProfile): SessionRestoreResult {
        val storedCredentials =
            secretStore.read(profile.endpoint).getOrNull()
                ?: return SessionRestoreResult.NotRestored

        val credentials =
            AuthCredentials.create(
                username = storedCredentials.username,
                password = storedCredentials.secret,
            )

        return when (
            val result =
                pingClient.ping(
                    credentials = credentials,
                    profile = profile,
                )
        ) {
            is AuthResult.Authenticated -> {
                SessionRestoreResult.Restored(
                    identity =
                        ServerAccountIdentity(
                            endpoint = profile.endpoint,
                            username =
                                storedCredentials.username,
                        ),
                    metadata = result.metadata,
                )
            }

            AuthResult.InvalidCredentials -> {
                /*
                 * Durable mutation belongs to the ViewModel's shared
                 * credential-mutation lane so a stale restore cannot
                 * clear a newer account.
                 */
                SessionRestoreResult.CredentialRejected
            }

            AuthResult.UnsupportedAuthentication,
            AuthResult.AuthProtocolError,
            AuthResult.IncompatibleServer,
            AuthResult.NetworkError,
            -> {
                SessionRestoreResult.NotRestored
            }
        }
    }
}
