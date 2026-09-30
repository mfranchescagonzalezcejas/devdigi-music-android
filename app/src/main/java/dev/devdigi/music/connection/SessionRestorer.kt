package dev.devdigi.music.connection

import kotlinx.coroutines.flow.first

sealed interface SessionRestoreResult {
    data class Restored(
        val identity: ServerAccountIdentity,
        val metadata: ServerMetadata,
    ) : SessionRestoreResult

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

        val storedCredentials =
            secretStore.read(profile.endpoint).getOrNull()
                ?: return SessionRestoreResult.NotRestored

        val credentials =
            AuthCredentials.create(
                username = storedCredentials.username,
                password = storedCredentials.secret,
            )

        return when (val result = pingClient.ping(credentials, profile)) {
            is AuthResult.Authenticated -> {
                SessionRestoreResult.Restored(
                    identity =
                        ServerAccountIdentity(
                            endpoint = profile.endpoint,
                            username = storedCredentials.username,
                        ),
                    metadata = result.metadata,
                )
            }

            AuthResult.InvalidCredentials -> {
                secretStore.clear()
                SessionRestoreResult.NotRestored
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
