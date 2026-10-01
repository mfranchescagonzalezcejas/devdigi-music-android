package dev.devdigi.music.features.library.presentation

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.library.domain.AccountScopedAlbumDetails
import dev.devdigi.music.features.library.domain.AlbumDetails
import dev.devdigi.music.features.library.domain.AlbumDetailsLoadResult
import dev.devdigi.music.features.library.domain.AlbumDetailsRepository
import dev.devdigi.music.features.library.domain.AlbumTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDetailsViewModelTest {
    @Test
    fun successPublishesContentAndRetryCanPublishEmpty() =
        runTest {
            val alice = account("alice")

            val populated =
                album(
                    tracks =
                        listOf(
                            track("track-1"),
                        ),
                )

            val empty =
                album(
                    tracks = emptyList(),
                )

            val repository =
                QueueRepository(
                    AlbumDetailsLoadResult.Success(
                        AccountScopedAlbumDetails(
                            account = alice,
                            album = populated,
                        ),
                    ),
                    AlbumDetailsLoadResult.Success(
                        AccountScopedAlbumDetails(
                            account = alice,
                            album = empty,
                        ),
                    ),
                )

            val viewModel =
                AlbumDetailsViewModel(
                    repository = repository,
                    scope = backgroundScope,
                )

            assertEquals(
                AlbumDetailsUiState.Idle,
                viewModel.state,
            )

            viewModel.load(
                account = alice,
                albumId = "album-1",
            )

            assertEquals(
                AlbumDetailsUiState.Loading(
                    albumId = "album-1",
                ),
                viewModel.state,
            )

            testScheduler.runCurrent()

            assertEquals(
                AlbumDetailsUiState.Content(
                    album = populated,
                ),
                viewModel.state,
            )

            viewModel.retry()
            testScheduler.runCurrent()

            assertEquals(
                AlbumDetailsUiState.Empty(
                    album = empty,
                ),
                viewModel.state,
            )

            assertEquals(
                listOf(
                    Request(alice, "album-1"),
                    Request(alice, "album-1"),
                ),
                repository.requests,
            )
        }

    @Test
    fun mapsRecoverableRepositoryOutcomesToDistinctStates() =
        runTest {
            val alice = account("alice")

            val cases =
                listOf(
                    AlbumDetailsLoadResult.AuthenticationRequired to
                        AlbumDetailsUiState.AuthenticationRequired,
                    AlbumDetailsLoadResult.NetworkError to
                        AlbumDetailsUiState.NetworkError,
                    AlbumDetailsLoadResult.MalformedResponse to
                        AlbumDetailsUiState.MalformedResponse,
                    AlbumDetailsLoadResult.ServerError to
                        AlbumDetailsUiState.ServerError,
                )

            cases.forEach { (result, expected) ->
                val viewModel =
                    AlbumDetailsViewModel(
                        repository =
                            QueueRepository(result),
                        scope = backgroundScope,
                    )

                viewModel.load(
                    account = alice,
                    albumId = "album-1",
                )

                testScheduler.runCurrent()

                assertEquals(
                    expected,
                    viewModel.state,
                )
            }
        }

    private class QueueRepository(
        vararg results: AlbumDetailsLoadResult,
    ) : AlbumDetailsRepository {
        private val pending =
            ArrayDeque<AlbumDetailsLoadResult>().apply {
                results.forEach(::addLast)
            }

        val requests =
            mutableListOf<Request>()

        override suspend fun loadAlbum(
            account: ServerAccountIdentity,
            albumId: String,
        ): AlbumDetailsLoadResult {
            requests +=
                Request(
                    account = account,
                    albumId = albumId,
                )

            return pending.removeFirst()
        }
    }

    private fun album(tracks: List<AlbumTrack>): AlbumDetails =
        AlbumDetails(
            id = "album-1",
            title = "Synthetic Album",
            artist = "Synthetic Artist",
            coverArtId = "cover-1",
            tracks = tracks,
        )

    private fun track(id: String): AlbumTrack =
        AlbumTrack(
            id = id,
            title = "Track $id",
            artist = null,
            trackNumber = null,
            discNumber = null,
            durationSeconds = null,
            coverArtId = null,
        )

    private fun account(username: String): ServerAccountIdentity =
        ServerAccountIdentity(
            endpoint =
                (
                    ServerEndpoint.parse(
                        "https://music.example.com",
                    ) as EndpointParseResult.Valid
                ).endpoint,
            username = username,
        )

    private data class Request(
        val account: ServerAccountIdentity,
        val albumId: String,
    )
}
