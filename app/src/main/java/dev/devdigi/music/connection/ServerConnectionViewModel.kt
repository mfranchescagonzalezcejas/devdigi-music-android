package dev.devdigi.music.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

enum class SessionStatus {
    SIGNED_OUT,
    RESTORING,
    SIGNING_IN,
    AUTHENTICATED,
    SIGN_OUT_FAILED,
}

data class ServerConnectionUiState(
    val endpointInput: String = "",
    val usernameInput: String = "",
    val passwordInput: String = "",
    val urlValidity: UrlValidity = UrlValidity.UNCHECKED,
    val profile: ServerProfile? = null,
    val connectionFacts: ConnectionFacts = ConnectionFacts(),
    val identity: ServerAccountIdentity? = null,
    val metadata: ServerMetadata? = null,
    val sessionStatus: SessionStatus = SessionStatus.SIGNED_OUT,
    val statusMessage: String =
        "Sign in is required before this server can be verified.",
)

class ServerConnectionViewModel(
    private val repository: ServerProfileRepository,
    private val scope: CoroutineScope? = null,
    private val secretStore: AuthSecretStore? = null,
    private val pingClient: AuthenticatedPingClient? = null,
    private val sessionRestorer: SessionRestorer? = null,
) : ViewModel() {
    var state by mutableStateOf(ServerConnectionUiState())
        private set

    private var hasUserEditedDraft = false
    private var hasObservedRepositoryProfile = false

    private val orchestrationMutex = Mutex()
    private val credentialMutationMutex = Mutex()
    private var authGeneration = 0L
    private var signInJob: Job? = null
    private var restoreJob: Job? = null
    private var profileMutationsInProgress = 0

    init {
        coroutineScope.launch {
            repository.profile.collect { profile ->
                val shouldRestore =
                    orchestrationMutex.withLock {
                        val firstEmission =
                            !hasObservedRepositoryProfile

                        val profileChanged =
                            hasObservedRepositoryProfile &&
                                state.profile != profile

                        hasObservedRepositoryProfile = true

                        if (profileChanged) {
                            authGeneration += 1
                            signInJob?.cancel()
                            restoreJob?.cancel()
                        }

                        state =
                            state.copy(
                                endpointInput =
                                    if (hasUserEditedDraft) {
                                        state.endpointInput
                                    } else {
                                        profile
                                            ?.endpoint
                                            ?.value
                                            .orEmpty()
                                    },
                                profile = profile,
                                urlValidity =
                                    if (hasUserEditedDraft) {
                                        state.urlValidity
                                    } else {
                                        profile
                                            ?.let(UrlValidity::Valid)
                                            ?: UrlValidity.UNCHECKED
                                    },
                                connectionFacts =
                                    if (profileChanged) {
                                        ConnectionFacts()
                                    } else {
                                        state.connectionFacts
                                    },
                                identity =
                                    if (profileChanged) {
                                        null
                                    } else {
                                        state.identity
                                    },
                                metadata =
                                    if (profileChanged) {
                                        null
                                    } else {
                                        state.metadata
                                    },
                                sessionStatus =
                                    if (profileChanged) {
                                        SessionStatus.SIGNED_OUT
                                    } else {
                                        state.sessionStatus
                                    },
                            )

                        firstEmission &&
                            profile != null &&
                            sessionRestorer != null
                    }

                if (shouldRestore) {
                    restoreSession()
                }
            }
        }
    }

    fun onEndpointChanged(endpoint: String) {
        hasUserEditedDraft = true
        state =
            state.copy(
                endpointInput = endpoint,
                urlValidity = UrlValidity.UNCHECKED,
                connectionFacts = ConnectionFacts(),
            )
    }

    fun onUsernameChanged(username: String) {
        state =
            state.copy(
                usernameInput = username,
                connectionFacts = ConnectionFacts(),
            )
    }

    fun onPasswordChanged(password: String) {
        state =
            state.copy(
                passwordInput = password,
                connectionFacts = ConnectionFacts(),
            )
    }

    fun confirm() {
        when (
            val result =
                ServerEndpoint.parse(state.endpointInput)
        ) {
            is EndpointParseResult.Valid -> {
                val profile = ServerProfile(result.endpoint)

                coroutineScope.launch {
                    beginProfileMutation()

                    try {
                        credentialMutationMutex.withLock profileMutation@{
                            val cleared =
                                clearCredentialsBeforeProfileMutation(
                                    failureMessage =
                                        "Unable to clear saved credentials. Server was not changed.",
                                )

                            if (!cleared) {
                                return@profileMutation
                            }

                            try {
                                repository.save(profile)

                                orchestrationMutex.withLock {
                                    hasUserEditedDraft = false
                                    state =
                                        state.copy(
                                            endpointInput =
                                                profile.endpoint.value,
                                            profile = profile,
                                            urlValidity =
                                                UrlValidity.Valid(
                                                    profile,
                                                ),
                                            connectionFacts =
                                                ConnectionFacts(),
                                            identity = null,
                                            metadata = null,
                                            sessionStatus =
                                                SessionStatus
                                                    .SIGNED_OUT,
                                            statusMessage = "",
                                        )
                                }
                            } catch (
                                error: CancellationException,
                            ) {
                                throw error
                            } catch (_: Throwable) {
                                orchestrationMutex.withLock {
                                    state =
                                        state.copy(
                                            statusMessage =
                                                "Unable to save server.",
                                        )
                                }
                            }
                        }
                    } finally {
                        withContext(NonCancellable) {
                            finishProfileMutation()
                        }
                    }
                }
            }

            EndpointParseResult.Invalid -> {
                state =
                    state.copy(
                        urlValidity = UrlValidity.Invalid(),
                    )
            }
        }
    }

    fun delete() {
        coroutineScope.launch {
            beginProfileMutation()

            try {
                credentialMutationMutex.withLock profileMutation@{
                    val cleared =
                        clearCredentialsBeforeProfileMutation(
                            failureMessage =
                                "Unable to clear saved credentials. Server was not deleted.",
                        )

                    if (!cleared) {
                        return@profileMutation
                    }

                    try {
                        repository.delete()

                        orchestrationMutex.withLock {
                            state =
                                state.copy(
                                    identity = null,
                                    metadata = null,
                                    connectionFacts =
                                        ConnectionFacts(),
                                    sessionStatus =
                                        SessionStatus.SIGNED_OUT,
                                    statusMessage = "",
                                )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        orchestrationMutex.withLock {
                            state =
                                state.copy(
                                    statusMessage =
                                        "Unable to delete server.",
                                )
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    finishProfileMutation()
                }
            }
        }
    }

    fun signIn() {
        val store = secretStore ?: return
        val client = pingClient ?: return

        restoreJob?.cancel()
        signInJob?.cancel()

        signInJob =
            coroutineScope.launch {
                val attempt =
                    orchestrationMutex.withLock {
                        if (profileMutationsInProgress > 0) {
                            state =
                                state.copy(
                                    statusMessage =
                                        "Wait for the server change to finish.",
                                )

                            return@withLock null
                        }

                        val profile = state.profile

                        if (profile == null) {
                            state =
                                state.copy(
                                    statusMessage =
                                        "Save a server before signing in.",
                                )
                            return@withLock null
                        }

                        authGeneration += 1

                        SignInAttempt(
                            generation = authGeneration,
                            profile = profile,
                            username = state.usernameInput,
                            password = state.passwordInput,
                        ).also {
                            state =
                                state.copy(
                                    identity = null,
                                    metadata = null,
                                    sessionStatus =
                                        SessionStatus.SIGNING_IN,
                                    connectionFacts =
                                        ConnectionFacts(),
                                    statusMessage =
                                        "Signing in…",
                                )
                        }
                    } ?: return@launch

                val credentials =
                    AuthCredentials.create(
                        username = attempt.username,
                        password = attempt.password,
                    )

                val result =
                    client.ping(
                        credentials = credentials,
                        profile = attempt.profile,
                    )

                if (result !is AuthResult.Authenticated) {
                    withContext(NonCancellable) {
                        orchestrationMutex.withLock {
                            if (!isCurrent(attempt)) {
                                return@withLock
                            }

                            state =
                                state.copy(
                                    identity = null,
                                    metadata = null,
                                    sessionStatus =
                                        SessionStatus.SIGNED_OUT,
                                    connectionFacts =
                                        reduceAuthResult(result),
                                    statusMessage =
                                        statusMessageFor(result),
                                )
                        }
                    }
                    return@launch
                }

                withContext(NonCancellable) {
                    credentialMutationMutex.withLock credentialMutation@{
                        val currentBeforePersistence =
                            orchestrationMutex.withLock {
                                isCurrent(attempt)
                            }

                        if (!currentBeforePersistence) {
                            return@credentialMutation
                        }

                        val identity =
                            ServerAccountIdentity(
                                endpoint =
                                    attempt.profile.endpoint,
                                username =
                                    attempt.username,
                            )

                        val saveResult =
                            try {
                                store.save(
                                    identity = identity,
                                    secret = attempt.password,
                                )
                            } catch (
                                error: CancellationException,
                            ) {
                                throw error
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }

                        if (saveResult.isFailure) {
                            orchestrationMutex.withLock {
                                if (isCurrent(attempt)) {
                                    state =
                                        state.copy(
                                            identity = null,
                                            metadata = null,
                                            sessionStatus =
                                                SessionStatus
                                                    .SIGNED_OUT,
                                            connectionFacts =
                                                ConnectionFacts(),
                                            statusMessage =
                                                "Unable to save credentials securely.",
                                        )
                                }
                            }

                            return@credentialMutation
                        }

                        val currentAfterPersistence =
                            orchestrationMutex.withLock {
                                isCurrent(attempt)
                            }

                        if (!currentAfterPersistence) {
                            try {
                                store.clear()
                            } catch (_: Throwable) {
                                // Never publish stale authentication.
                            }

                            return@credentialMutation
                        }

                        var published = false

                        orchestrationMutex.withLock {
                            if (isCurrent(attempt)) {
                                state =
                                    state.copy(
                                        passwordInput = "",
                                        identity = identity,
                                        metadata =
                                            result.metadata,
                                        sessionStatus =
                                            SessionStatus
                                                .AUTHENTICATED,
                                        connectionFacts =
                                            reduceAuthResult(
                                                result,
                                            ),
                                        statusMessage = "",
                                    )

                                published = true
                            }
                        }

                        if (!published) {
                            try {
                                store.clear()
                            } catch (_: Throwable) {
                                // Never publish stale authentication.
                            }
                        }
                    }
                }
            }
    }

    fun restoreSession() {
        val restorer = sessionRestorer ?: return

        signInJob?.cancel()
        restoreJob?.cancel()

        restoreJob =
            coroutineScope.launch {
                val attempt =
                    orchestrationMutex.withLock {
                        if (profileMutationsInProgress > 0) {
                            state =
                                state.copy(
                                    identity = null,
                                    metadata = null,
                                    sessionStatus =
                                        SessionStatus.SIGNED_OUT,
                                    connectionFacts =
                                        ConnectionFacts(),
                                    statusMessage =
                                        "Wait for the server change to finish.",
                                )

                            return@withLock null
                        }

                        val profile = state.profile

                        if (profile == null) {
                            state =
                                state.copy(
                                    identity = null,
                                    metadata = null,
                                    sessionStatus =
                                        SessionStatus.SIGNED_OUT,
                                    connectionFacts =
                                        ConnectionFacts(),
                                    statusMessage =
                                        "Sign in is required.",
                                )

                            return@withLock null
                        }

                        authGeneration += 1

                        RestoreAttempt(
                            generation = authGeneration,
                            profile = profile,
                        ).also {
                            state =
                                state.copy(
                                    identity = null,
                                    metadata = null,
                                    sessionStatus =
                                        SessionStatus.RESTORING,
                                    connectionFacts =
                                        ConnectionFacts(),
                                    statusMessage =
                                        "Verifying saved session…",
                                )
                        }
                    } ?: return@launch

                when (
                    val result =
                        restorer.restore(attempt.profile)
                ) {
                    is SessionRestoreResult.Restored -> {
                        withContext(NonCancellable) {
                            orchestrationMutex.withLock {
                                if (!isCurrent(attempt)) {
                                    return@withLock
                                }

                                state =
                                    state.copy(
                                        usernameInput =
                                            result.identity
                                                .username,
                                        passwordInput = "",
                                        identity =
                                            result.identity,
                                        metadata =
                                            result.metadata,
                                        sessionStatus =
                                            SessionStatus
                                                .AUTHENTICATED,
                                        connectionFacts =
                                            reduceAuthResult(
                                                AuthResult
                                                    .Authenticated(
                                                        result
                                                            .metadata,
                                                    ),
                                            ),
                                        statusMessage = "",
                                    )
                            }
                        }
                    }

                    SessionRestoreResult.CredentialRejected -> {
                        clearRejectedRestoreCredential(
                            attempt = attempt,
                        )
                    }

                    SessionRestoreResult.NotRestored -> {
                        withContext(NonCancellable) {
                            orchestrationMutex.withLock {
                                if (!isCurrent(attempt)) {
                                    return@withLock
                                }

                                state =
                                    state.copy(
                                        identity = null,
                                        metadata = null,
                                        sessionStatus =
                                            SessionStatus
                                                .SIGNED_OUT,
                                        connectionFacts =
                                            ConnectionFacts(),
                                        statusMessage =
                                            "Sign in is required.",
                                    )
                            }
                        }
                    }
                }
            }
    }

    fun signOut() {
        val store = secretStore ?: return

        coroutineScope.launch {
            val generation =
                orchestrationMutex.withLock {
                    authGeneration += 1
                    signInJob?.cancel()
                    restoreJob?.cancel()
                    authGeneration
                }

            try {
                credentialMutationMutex.withLock {
                    store.clear()
                }

                orchestrationMutex.withLock {
                    if (authGeneration != generation) {
                        return@withLock
                    }

                    state =
                        state.copy(
                            passwordInput = "",
                            identity = null,
                            metadata = null,
                            sessionStatus =
                                SessionStatus.SIGNED_OUT,
                            connectionFacts =
                                ConnectionFacts(),
                            statusMessage = "",
                        )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                orchestrationMutex.withLock {
                    if (authGeneration != generation) {
                        return@withLock
                    }

                    state =
                        state.copy(
                            sessionStatus =
                                SessionStatus.SIGN_OUT_FAILED,
                            statusMessage =
                                "Unable to sign out. Try again.",
                        )
                }
            }
        }
    }

    private suspend fun clearCredentialsBeforeProfileMutation(failureMessage: String): Boolean {
        val store = secretStore ?: return true

        return try {
            store.clear()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            orchestrationMutex.withLock {
                state =
                    state.copy(
                        statusMessage = failureMessage,
                    )
            }

            false
        }
    }

    private suspend fun beginProfileMutation() {
        orchestrationMutex.withLock {
            profileMutationsInProgress += 1
            authGeneration += 1
            signInJob?.cancel()
            restoreJob?.cancel()

            state =
                state.copy(
                    identity = null,
                    metadata = null,
                    sessionStatus =
                        SessionStatus.SIGNED_OUT,
                    connectionFacts =
                        ConnectionFacts(),
                )
        }
    }

    private suspend fun finishProfileMutation() {
        orchestrationMutex.withLock {
            check(profileMutationsInProgress > 0) {
                "Profile mutation counter underflow"
            }

            profileMutationsInProgress -= 1
        }
    }

    private suspend fun clearRejectedRestoreCredential(attempt: RestoreAttempt) {
        val store = secretStore

        if (store == null) {
            withContext(NonCancellable) {
                orchestrationMutex.withLock {
                    if (!isCurrent(attempt)) {
                        return@withLock
                    }

                    state =
                        state.copy(
                            identity = null,
                            metadata = null,
                            sessionStatus =
                                SessionStatus.SIGNED_OUT,
                            connectionFacts =
                                ConnectionFacts(),
                            statusMessage =
                                "Sign in is required.",
                        )
                }
            }

            return
        }

        withContext(NonCancellable) {
            credentialMutationMutex.withLock restoreCredential@{
                val currentBeforeClear =
                    orchestrationMutex.withLock {
                        isCurrent(attempt)
                    }

                if (!currentBeforeClear) {
                    return@restoreCredential
                }

                val clearSucceeded =
                    try {
                        store.clear()
                        true
                    } catch (_: Throwable) {
                        false
                    }

                orchestrationMutex.withLock {
                    if (!isCurrent(attempt)) {
                        return@withLock
                    }

                    state =
                        state.copy(
                            identity = null,
                            metadata = null,
                            sessionStatus =
                                SessionStatus.SIGNED_OUT,
                            connectionFacts =
                                ConnectionFacts(),
                            statusMessage =
                                if (clearSucceeded) {
                                    "Stored credentials are no longer valid. Sign in again."
                                } else {
                                    "Unable to clear invalid stored credentials. Try again."
                                },
                        )
                }
            }
        }
    }

    private fun isCurrent(attempt: RestoreAttempt): Boolean =
        attempt.generation == authGeneration &&
            state.profile == attempt.profile

    private fun isCurrent(attempt: SignInAttempt): Boolean =
        attempt.generation == authGeneration &&
            state.profile == attempt.profile

    private data class RestoreAttempt(
        val generation: Long,
        val profile: ServerProfile,
    )

    private data class SignInAttempt(
        val generation: Long,
        val profile: ServerProfile,
        val username: String,
        val password: String,
    )

    private fun statusMessageFor(result: AuthResult): String =
        when (result) {
            AuthResult.InvalidCredentials -> {
                "Invalid username or password."
            }

            AuthResult.NetworkError -> {
                "Unable to reach the server."
            }

            AuthResult.UnsupportedAuthentication -> {
                "This authentication method is not supported."
            }

            AuthResult.IncompatibleServer -> {
                "This server is not compatible."
            }

            AuthResult.AuthProtocolError -> {
                "The server returned an invalid authentication response."
            }

            is AuthResult.Authenticated -> {
                ""
            }
        }

    private val coroutineScope: CoroutineScope
        get() = scope ?: viewModelScope

    companion object {
        fun factory(
            repository: ServerProfileRepository,
            secretStore: AuthSecretStore,
            pingClient: AuthenticatedPingClient,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    check(
                        modelClass.isAssignableFrom(
                            ServerConnectionViewModel::class.java,
                        ),
                    )

                    @Suppress("UNCHECKED_CAST")
                    return ServerConnectionViewModel(
                        repository = repository,
                        secretStore = secretStore,
                        pingClient = pingClient,
                        sessionRestorer =
                            SessionRestorer(
                                repository = repository,
                                secretStore = secretStore,
                                pingClient = pingClient,
                            ),
                    ) as T
                }
            }
    }
}
