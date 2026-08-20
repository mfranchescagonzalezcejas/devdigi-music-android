package dev.devdigi.music.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class ServerConnectionUiState(
    val endpointInput: String = "",
    val urlValidity: UrlValidity = UrlValidity.UNCHECKED,
    val profile: ServerProfile? = null,
    val connectionFacts: ConnectionFacts = ConnectionFacts(),
    val statusMessage: String = "Sign in is required before this server can be verified.",
)

class ServerConnectionViewModel(
    private val repository: ServerProfileRepository,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    var state by mutableStateOf(ServerConnectionUiState())
        private set

    init {
        coroutineScope.launch {
            repository.profile.collect { profile ->
                state = state.copy(
                    endpointInput = profile?.endpoint?.value.orEmpty(),
                    profile = profile,
                    urlValidity = profile?.let(UrlValidity::Valid) ?: UrlValidity.UNCHECKED,
                    connectionFacts = ConnectionFacts(),
                )
            }
        }
    }

    fun onEndpointChanged(endpoint: String) {
        state = state.copy(
            endpointInput = endpoint,
            urlValidity = UrlValidity.UNCHECKED,
            profile = null,
            connectionFacts = ConnectionFacts(),
        )
    }

    fun confirm() {
        when (val result = ServerEndpoint.parse(state.endpointInput)) {
            is EndpointParseResult.Valid -> {
                val profile = ServerProfile(result.endpoint)
                coroutineScope.launch {
                    runCatching { repository.save(profile) }
                        .onFailure { state = state.copy(profile = null, statusMessage = "Unable to save server.") }
                }
            }

            EndpointParseResult.Invalid -> state = state.copy(profile = null, urlValidity = UrlValidity.Invalid())
        }
    }

    fun delete() {
        coroutineScope.launch {
            runCatching { repository.delete() }
                .onFailure { state = state.copy(statusMessage = "Unable to delete server.") }
        }
    }

    private val coroutineScope: CoroutineScope
        get() = scope ?: viewModelScope

    companion object {
        fun factory(repository: ServerProfileRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                check(modelClass.isAssignableFrom(ServerConnectionViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return ServerConnectionViewModel(repository) as T
            }
        }
    }
}
