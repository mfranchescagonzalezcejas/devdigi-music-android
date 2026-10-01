package dev.devdigi.music.features.library.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.library.domain.RecentAlbum
import dev.devdigi.music.features.library.domain.RecentAlbumsLoadResult
import dev.devdigi.music.features.library.domain.RecentAlbumsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface RecentAlbumsUiState {
    data object Idle : RecentAlbumsUiState

    data object Loading : RecentAlbumsUiState

    data class Content(
        val albums: List<RecentAlbum>,
    ) : RecentAlbumsUiState

    data object Empty : RecentAlbumsUiState

    data object AuthenticationRequired : RecentAlbumsUiState

    data object NetworkError : RecentAlbumsUiState

    data object MalformedResponse : RecentAlbumsUiState

    data object ServerError : RecentAlbumsUiState
}

class RecentAlbumsViewModel(
    private val repository: RecentAlbumsRepository,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    var state by mutableStateOf<RecentAlbumsUiState>(
        RecentAlbumsUiState.Idle,
    )
        private set

    private var activeAccount:
        ServerAccountIdentity? = null

    private var generation = 0L
    private var loadJob: Job? = null

    fun load(account: ServerAccountIdentity) {
        generation += 1

        val attempt =
            LoadAttempt(
                generation = generation,
                account = account,
            )

        activeAccount = account

        loadJob?.cancel()

        state =
            RecentAlbumsUiState.Loading

        loadJob =
            coroutineScope.launch {
                val result =
                    repository.loadRecentAlbums(
                        account,
                    )

                if (!isCurrent(attempt)) {
                    return@launch
                }

                state =
                    reduce(
                        expectedAccount = account,
                        result = result,
                    )
            }
    }

    fun retry() {
        activeAccount?.let(::load)
    }

    fun clear() {
        generation += 1
        activeAccount = null
        loadJob?.cancel()
        loadJob = null
        state = RecentAlbumsUiState.Idle
    }

    private fun reduce(
        expectedAccount: ServerAccountIdentity,
        result: RecentAlbumsLoadResult,
    ): RecentAlbumsUiState =
        when (result) {
            is RecentAlbumsLoadResult.Success -> {
                if (
                    result.catalogue.account !=
                    expectedAccount
                ) {
                    RecentAlbumsUiState.MalformedResponse
                } else if (
                    result.catalogue.albums.isEmpty()
                ) {
                    RecentAlbumsUiState.Empty
                } else {
                    RecentAlbumsUiState.Content(
                        result.catalogue.albums,
                    )
                }
            }

            RecentAlbumsLoadResult.AuthenticationRequired -> {
                RecentAlbumsUiState.AuthenticationRequired
            }

            RecentAlbumsLoadResult.NetworkError -> {
                RecentAlbumsUiState.NetworkError
            }

            RecentAlbumsLoadResult.MalformedResponse -> {
                RecentAlbumsUiState.MalformedResponse
            }

            RecentAlbumsLoadResult.ServerError -> {
                RecentAlbumsUiState.ServerError
            }
        }

    private fun isCurrent(attempt: LoadAttempt): Boolean =
        attempt.generation == generation &&
            attempt.account == activeAccount

    private val coroutineScope: CoroutineScope
        get() = scope ?: viewModelScope

    private data class LoadAttempt(
        val generation: Long,
        val account: ServerAccountIdentity,
    )
}
