package dev.devdigi.music.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    init {
        coroutineScope.launch {
            repository.profile.collect { profile ->
                val profileChanged =
                    state.profile != null &&
                        state.profile != profile

                state =
                    state.copy(
                        endpointInput =
                            if (hasUserEditedDraft) {
                                state.endpointInput
                            } else {
                                profile?.endpoint?.value.orEmpty()
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
                            if (profileChanged) null else state.identity,
                        metadata =
                            if (profileChanged) null else state.metadata,
                        sessionStatus =
                            if (profileChanged) {
                                SessionStatus.SIGNED_OUT
                            } else {
                                state.sessionStatus
                            },
                    )
            }
        }

        if (sessionRestorer != null) {
            restoreSession()
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
                    runCatching {
                        repository.save(profile)
                    }.onSuccess {
                        hasUserEditedDraft = false
                        state =
                            state.copy(
                                endpointInput =
                                    profile.endpoint.value,
                                profile = profile,
                                urlValidity =
                                    UrlValidity.Valid(profile),
                                connectionFacts =
                                    ConnectionFacts(),
                                identity = null,
                                metadata = null,
                                sessionStatus =
                                    SessionStatus.SIGNED_OUT,
                                statusMessage = "",
                            )
                    }.onFailure {
                        state =
                            state.copy(
                                statusMessage =
                                    "Unable to save server.",
                            )
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
            runCatching {
                repository.delete()
            }.onSuccess {
                state =
                    state.copy(
                        identity = null,
                        metadata = null,
                        connectionFacts = ConnectionFacts(),
                        sessionStatus =
                            SessionStatus.SIGNED_OUT,
                        statusMessage = "",
                    )
            }.onFailure {
                state =
                    state.copy(
                        statusMessage =
                            "Unable to delete server.",
                    )
            }
        }
    }

    fun signIn() {
        val store = secretStore ?: return
        val client = pingClient ?: return
        val profile = state.profile

        if (profile == null) {
            state =
                state.copy(
                    statusMessage =
                        "Save a server before signing in.",
                )
            return
        }

        val username = state.usernameInput
        val password = state.passwordInput

        state =
            state.copy(
                identity = null,
                metadata = null,
                sessionStatus = SessionStatus.SIGNING_IN,
                connectionFacts = ConnectionFacts(),
                statusMessage = "Signing in…",
            )

        coroutineScope.launch {
            val credentials =
                AuthCredentials.create(
                    username = username,
                    password = password,
                )

            val result =
                client.ping(
                    credentials = credentials,
                    profile = profile,
                )

            if (result !is AuthResult.Authenticated) {
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
                return@launch
            }

            val identity =
                ServerAccountIdentity(
                    endpoint = profile.endpoint,
                    username = username,
                )

            val saveResult =
                try {
                    store.save(
                        identity = identity,
                        secret = password,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Result.failure(error)
                }

            if (saveResult.isFailure) {
                state =
                    state.copy(
                        identity = null,
                        metadata = null,
                        sessionStatus =
                            SessionStatus.SIGNED_OUT,
                        connectionFacts =
                            ConnectionFacts(),
                        statusMessage =
                            "Unable to save credentials securely.",
                    )
                return@launch
            }

            state =
                state.copy(
                    passwordInput = "",
                    identity = identity,
                    metadata = result.metadata,
                    sessionStatus =
                        SessionStatus.AUTHENTICATED,
                    connectionFacts =
                        reduceAuthResult(result),
                    statusMessage = "",
                )
        }
    }

    fun restoreSession() {
        val restorer = sessionRestorer ?: return

        state =
            state.copy(
                identity = null,
                metadata = null,
                sessionStatus = SessionStatus.RESTORING,
                connectionFacts = ConnectionFacts(),
                statusMessage = "Verifying saved session…",
            )

        coroutineScope.launch {
            when (val result = restorer.restore()) {
                is SessionRestoreResult.Restored -> {
                    state =
                        state.copy(
                            usernameInput =
                                result.identity.username,
                            passwordInput = "",
                            identity = result.identity,
                            metadata = result.metadata,
                            sessionStatus =
                                SessionStatus.AUTHENTICATED,
                            connectionFacts =
                                reduceAuthResult(
                                    AuthResult.Authenticated(
                                        result.metadata,
                                    ),
                                ),
                            statusMessage = "",
                        )
                }

                SessionRestoreResult.NotRestored -> {
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
        }
    }

    fun signOut() {
        val store = secretStore ?: return

        coroutineScope.launch {
            try {
                store.clear()

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
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
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
        fun factory(repository: ServerProfileRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    check(
                        modelClass.isAssignableFrom(
                            ServerConnectionViewModel::class.java,
                        ),
                    )

                    @Suppress("UNCHECKED_CAST")
                    return ServerConnectionViewModel(
                        repository,
                    ) as T
                }
            }
    }
}
