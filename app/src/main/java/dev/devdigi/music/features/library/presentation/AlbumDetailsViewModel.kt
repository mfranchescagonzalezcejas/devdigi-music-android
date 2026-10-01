package dev.devdigi.music.features.library.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.features.library.domain.AlbumDetails
import dev.devdigi.music.features.library.domain.AlbumDetailsLoadResult
import dev.devdigi.music.features.library.domain.AlbumDetailsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface AlbumDetailsUiState {
    data object Idle : AlbumDetailsUiState

    data class Loading(
        val albumId: String,
    ) : AlbumDetailsUiState

    data class Content(
        val album: AlbumDetails,
    ) : AlbumDetailsUiState

    data class Empty(
        val album: AlbumDetails,
    ) : AlbumDetailsUiState

    data object AuthenticationRequired :
        AlbumDetailsUiState

    data object NetworkError :
        AlbumDetailsUiState

    data object MalformedResponse :
        AlbumDetailsUiState

    data object ServerError :
        AlbumDetailsUiState
}

class AlbumDetailsViewModel(
    private val repository: AlbumDetailsRepository,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    var state by mutableStateOf<AlbumDetailsUiState>(
        AlbumDetailsUiState.Idle,
    )
        private set

    private var activeTarget:
        LoadTarget? = null

    private var generation = 0L
    private var loadJob: Job? = null

    fun load(
        account: ServerAccountIdentity,
        albumId: String,
    ) {
        generation += 1

        val target =
            LoadTarget(
                account = account,
                albumId = albumId,
            )

        val attempt =
            LoadAttempt(
                generation = generation,
                target = target,
            )

        activeTarget = target

        loadJob?.cancel()

        state =
            AlbumDetailsUiState.Loading(
                albumId = albumId,
            )

        loadJob =
            coroutineScope.launch {
                val result =
                    repository.loadAlbum(
                        account = account,
                        albumId = albumId,
                    )

                if (!isCurrent(attempt)) {
                    return@launch
                }

                state =
                    reduce(
                        expectedTarget = target,
                        result = result,
                    )
            }
    }

    fun retry() {
        activeTarget?.let { target ->
            load(
                account = target.account,
                albumId = target.albumId,
            )
        }
    }

    fun clear() {
        generation += 1
        activeTarget = null
        loadJob?.cancel()
        loadJob = null
        state = AlbumDetailsUiState.Idle
    }

    private fun reduce(
        expectedTarget: LoadTarget,
        result: AlbumDetailsLoadResult,
    ): AlbumDetailsUiState =
        when (result) {
            is AlbumDetailsLoadResult.Success -> {
                val details =
                    result.details

                if (
                    details.account !=
                    expectedTarget.account ||
                    details.album.id !=
                    expectedTarget.albumId
                ) {
                    AlbumDetailsUiState
                        .MalformedResponse
                } else if (
                    details.album.tracks.isEmpty()
                ) {
                    AlbumDetailsUiState.Empty(
                        album = details.album,
                    )
                } else {
                    AlbumDetailsUiState.Content(
                        album = details.album,
                    )
                }
            }

            AlbumDetailsLoadResult
                .AuthenticationRequired,
            -> {
                AlbumDetailsUiState
                    .AuthenticationRequired
            }

            AlbumDetailsLoadResult.NetworkError -> {
                AlbumDetailsUiState.NetworkError
            }

            AlbumDetailsLoadResult
                .MalformedResponse,
            -> {
                AlbumDetailsUiState.MalformedResponse
            }

            AlbumDetailsLoadResult.ServerError -> {
                AlbumDetailsUiState.ServerError
            }
        }

    private fun isCurrent(attempt: LoadAttempt): Boolean =
        attempt.generation == generation &&
            attempt.target == activeTarget

    private val coroutineScope: CoroutineScope
        get() = scope ?: viewModelScope

    private data class LoadTarget(
        val account: ServerAccountIdentity,
        val albumId: String,
    )

    private data class LoadAttempt(
        val generation: Long,
        val target: LoadTarget,
    )

    companion object {
        fun factory(repository: AlbumDetailsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    check(
                        modelClass.isAssignableFrom(
                            AlbumDetailsViewModel::class.java,
                        ),
                    )

                    @Suppress("UNCHECKED_CAST")
                    return AlbumDetailsViewModel(
                        repository = repository,
                    ) as T
                }
            }
    }
}
