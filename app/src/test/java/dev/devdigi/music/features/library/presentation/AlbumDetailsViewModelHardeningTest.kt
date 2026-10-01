package dev.devdigi.music.features.library.presentation

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.library.domain.AccountScopedAlbumDetails
import dev.devdigi.music.features.library.domain.AlbumDetails
import dev.devdigi.music.features.library.domain.AlbumDetailsLoadResult
import dev.devdigi.music.features.library.domain.AlbumDetailsRepository
import dev.devdigi.music.features.library.domain.AlbumTrack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class AlbumDetailsViewModelHardeningTest {
    @Test
    fun rejectsSuccessOwnedByUnexpectedAccountOrAlbum() =
        runTest {
            val alice = account("alice")
            val bob = account("bob")

            val foreignResults =
                listOf(
                    AccountScopedAlbumDetails(
                        account = bob,
                        album = album("album-1"),
                    ),
                    AccountScopedAlbumDetails(
                        account = alice,
                        album = album("album-2"),
                    ),
                )

            foreignResults.forEach { details ->
                val viewModel =
                    AlbumDetailsViewModel(
                        repository =
                            StaticRepository(
                                AlbumDetailsLoadResult.Success(
                                    details,
                                ),
                            ),
                        scope = backgroundScope,
                    )

                viewModel.load(
                    account = alice,
                    albumId = "album-1",
                )

                testScheduler.runCurrent()

                assertEquals(
                    AlbumDetailsUiState.MalformedResponse,
                    viewModel.state,
                )
            }
        }

    @Test
    fun lateResultFromPreviousAlbumCannotReplaceCurrentAlbum() =
        runTest {
            val alice = account("alice")

            val albumA =
                CompletableDeferred<
                    AlbumDetailsLoadResult,
                >()

            val albumB =
                CompletableDeferred<
                    AlbumDetailsLoadResult,
                >()

            val repository =
                DeferredRepository(
                    mapOf(
                        Request(alice, "album-a") to
                            albumA,
                        Request(alice, "album-b") to
                            albumB,
                    ),
                )

            val viewModel =
                AlbumDetailsViewModel(
                    repository = repository,
                    scope = backgroundScope,
                )

            viewModel.load(
                account = alice,
                albumId = "album-a",
            )
            testScheduler.runCurrent()

            viewModel.load(
                account = alice,
                albumId = "album-b",
            )
            testScheduler.runCurrent()

            val current =
                album("album-b")

            albumB.complete(
                success(
                    account = alice,
                    album = current,
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                AlbumDetailsUiState.Content(current),
                viewModel.state,
            )

            albumA.complete(
                success(
                    account = alice,
                    album = album("album-a"),
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                AlbumDetailsUiState.Content(current),
                viewModel.state,
            )
        }

    @Test
    fun lateResultFromPreviousAccountCannotReplaceCurrentAccount() =
        runTest {
            val alice = account("alice")
            val bob = account("bob")

            val aliceResult =
                CompletableDeferred<
                    AlbumDetailsLoadResult,
                >()

            val bobResult =
                CompletableDeferred<
                    AlbumDetailsLoadResult,
                >()

            val repository =
                DeferredRepository(
                    mapOf(
                        Request(alice, "album-1") to
                            aliceResult,
                        Request(bob, "album-1") to
                            bobResult,
                    ),
                )

            val viewModel =
                AlbumDetailsViewModel(
                    repository = repository,
                    scope = backgroundScope,
                )

            viewModel.load(
                account = alice,
                albumId = "album-1",
            )
            testScheduler.runCurrent()

            viewModel.load(
                account = bob,
                albumId = "album-1",
            )
            testScheduler.runCurrent()

            val current =
                album("album-1")

            bobResult.complete(
                success(
                    account = bob,
                    album = current,
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                AlbumDetailsUiState.Content(current),
                viewModel.state,
            )

            aliceResult.complete(
                success(
                    account = alice,
                    album = album("album-1"),
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                AlbumDetailsUiState.Content(current),
                viewModel.state,
            )
        }

    @Test
    fun clearRejectsPendingResultAndReturnsToIdle() =
        runTest {
            val alice = account("alice")

            val pending =
                CompletableDeferred<
                    AlbumDetailsLoadResult,
                >()

            val viewModel =
                AlbumDetailsViewModel(
                    repository =
                        DeferredRepository(
                            mapOf(
                                Request(
                                    alice,
                                    "album-1",
                                ) to pending,
                            ),
                        ),
                    scope = backgroundScope,
                )

            viewModel.load(
                account = alice,
                albumId = "album-1",
            )
            testScheduler.runCurrent()

            assertEquals(
                AlbumDetailsUiState.Loading(
                    albumId = "album-1",
                ),
                viewModel.state,
            )

            viewModel.clear()

            assertEquals(
                AlbumDetailsUiState.Idle,
                viewModel.state,
            )

            pending.complete(
                success(
                    account = alice,
                    album = album("album-1"),
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                AlbumDetailsUiState.Idle,
                viewModel.state,
            )
        }

    private class StaticRepository(
        private val result: AlbumDetailsLoadResult,
    ) : AlbumDetailsRepository {
        override suspend fun loadAlbum(
            account: ServerAccountIdentity,
            albumId: String,
        ): AlbumDetailsLoadResult = result
    }

    private class DeferredRepository(
        private val results: Map<
            Request,
            CompletableDeferred<
                AlbumDetailsLoadResult,
            >,
        >,
    ) : AlbumDetailsRepository {
        override suspend fun loadAlbum(
            account: ServerAccountIdentity,
            albumId: String,
        ): AlbumDetailsLoadResult =
            try {
                withContext(NonCancellable) {
                    requireNotNull(
                        results[
                            Request(
                                account = account,
                                albumId = albumId,
                            ),
                        ],
                    ).await()
                }
            } catch (error: CancellationException) {
                throw error
            }
    }

    private fun success(
        account: ServerAccountIdentity,
        album: AlbumDetails,
    ): AlbumDetailsLoadResult =
        AlbumDetailsLoadResult.Success(
            AccountScopedAlbumDetails(
                account = account,
                album = album,
            ),
        )

    private fun album(id: String): AlbumDetails =
        AlbumDetails(
            id = id,
            title = "Album $id",
            artist = "Synthetic Artist",
            coverArtId = "cover-$id",
            tracks =
                listOf(
                    AlbumTrack(
                        id = "track-$id",
                        title = "Track $id",
                        artist = null,
                        trackNumber = null,
                        discNumber = null,
                        durationSeconds = null,
                        coverArtId = null,
                    ),
                ),
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
