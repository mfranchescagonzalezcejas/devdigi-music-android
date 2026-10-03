package dev.devdigi.music.features.library.presentation

import dev.devdigi.music.connection.EndpointParseResult
import dev.devdigi.music.connection.ServerAccountIdentity
import dev.devdigi.music.connection.ServerEndpoint
import dev.devdigi.music.features.library.domain.AccountScopedRecentAlbums
import dev.devdigi.music.features.library.domain.RecentAlbum
import dev.devdigi.music.features.library.domain.RecentAlbumsLoadResult
import dev.devdigi.music.features.library.domain.RecentAlbumsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class RecentAlbumsViewModelTest {
    @Test
    fun successPublishesContentAndRetryCanPublishEmpty() =
        runTest {
            val alice = account("alice")

            val album =
                RecentAlbum(
                    id = "album-1",
                    title = "Synthetic Album",
                    artist = "Example Artist",
                    coverArtId = "cover-1",
                )

            val repository =
                QueueRepository(
                    RecentAlbumsLoadResult.Success(
                        AccountScopedRecentAlbums(
                            account = alice,
                            albums = listOf(album),
                        ),
                    ),
                    RecentAlbumsLoadResult.Success(
                        AccountScopedRecentAlbums(
                            account = alice,
                            albums = emptyList(),
                        ),
                    ),
                )

            val viewModel =
                RecentAlbumsViewModel(
                    repository = repository,
                    scope = backgroundScope,
                )

            assertEquals(
                RecentAlbumsUiState.Idle,
                viewModel.state,
            )

            viewModel.load(alice)

            assertEquals(
                RecentAlbumsUiState.Loading,
                viewModel.state,
            )

            testScheduler.runCurrent()

            assertEquals(
                RecentAlbumsUiState.Content(
                    albums = listOf(album),
                ),
                viewModel.state,
            )

            viewModel.retry()
            testScheduler.runCurrent()

            assertEquals(
                RecentAlbumsUiState.Empty,
                viewModel.state,
            )

            assertEquals(
                listOf(alice, alice),
                repository.accounts,
            )
        }

    @Test
    fun mapsRecoverableRepositoryOutcomesToDistinctStates() =
        runTest {
            val alice = account("alice")

            val cases =
                listOf(
                    RecentAlbumsLoadResult.AuthenticationRequired to
                        RecentAlbumsUiState.AuthenticationRequired,
                    RecentAlbumsLoadResult.NetworkError to
                        RecentAlbumsUiState.NetworkError,
                    RecentAlbumsLoadResult.MalformedResponse to
                        RecentAlbumsUiState.MalformedResponse,
                    RecentAlbumsLoadResult.ServerError to
                        RecentAlbumsUiState.ServerError,
                )

            cases.forEach { (result, expected) ->
                val viewModel =
                    RecentAlbumsViewModel(
                        repository = QueueRepository(result),
                        scope = backgroundScope,
                    )

                viewModel.load(alice)
                testScheduler.runCurrent()

                assertEquals(expected, viewModel.state)
            }
        }

    @Test
    fun rejectsSuccessOwnedByAnotherAccount() =
        runTest {
            val alice = account("alice")
            val bob = account("bob")

            val repository =
                QueueRepository(
                    RecentAlbumsLoadResult.Success(
                        AccountScopedRecentAlbums(
                            account = bob,
                            albums =
                                listOf(
                                    RecentAlbum(
                                        id = "foreign-album",
                                        title = "Foreign Album",
                                        artist = null,
                                        coverArtId = null,
                                    ),
                                ),
                        ),
                    ),
                )

            val viewModel =
                RecentAlbumsViewModel(
                    repository = repository,
                    scope = backgroundScope,
                )

            viewModel.load(alice)
            testScheduler.runCurrent()

            assertEquals(
                RecentAlbumsUiState.MalformedResponse,
                viewModel.state,
            )
        }

    @Test
    fun lateResultFromPreviousAccountCannotReplaceCurrentAccount() =
        runTest {
            val alice = account("alice")
            val bob = account("bob")

            val aliceResult =
                CompletableDeferred<RecentAlbumsLoadResult>()

            val bobResult =
                CompletableDeferred<RecentAlbumsLoadResult>()

            val repository =
                DeferredRepository(
                    results =
                        mapOf(
                            alice to aliceResult,
                            bob to bobResult,
                        ),
                )

            val viewModel =
                RecentAlbumsViewModel(
                    repository = repository,
                    scope = backgroundScope,
                )

            viewModel.load(alice)
            testScheduler.runCurrent()

            viewModel.load(bob)
            testScheduler.runCurrent()

            val bobAlbum =
                RecentAlbum(
                    id = "bob-album",
                    title = "Bob Album",
                    artist = null,
                    coverArtId = null,
                )

            bobResult.complete(
                RecentAlbumsLoadResult.Success(
                    AccountScopedRecentAlbums(
                        account = bob,
                        albums = listOf(bobAlbum),
                    ),
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                RecentAlbumsUiState.Content(
                    listOf(bobAlbum),
                ),
                viewModel.state,
            )

            aliceResult.complete(
                RecentAlbumsLoadResult.Success(
                    AccountScopedRecentAlbums(
                        account = alice,
                        albums =
                            listOf(
                                RecentAlbum(
                                    id = "alice-album",
                                    title = "Alice Album",
                                    artist = null,
                                    coverArtId = null,
                                ),
                            ),
                    ),
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                RecentAlbumsUiState.Content(
                    listOf(bobAlbum),
                ),
                viewModel.state,
            )
        }

    @Test
    fun lateResultFromPreviousServerWithSameUsernameCannotReplaceCurrentAccount() =
        runTest {
            val first =
                account(
                    username = "alice",
                    endpointValue = "https://one.example.com",
                )

            val second =
                account(
                    username = "alice",
                    endpointValue = "https://two.example.com",
                )

            val firstResult =
                CompletableDeferred<RecentAlbumsLoadResult>()

            val secondResult =
                CompletableDeferred<RecentAlbumsLoadResult>()

            val repository =
                DeferredRepository(
                    results =
                        mapOf(
                            first to firstResult,
                            second to secondResult,
                        ),
                )

            val viewModel =
                RecentAlbumsViewModel(
                    repository = repository,
                    scope = backgroundScope,
                )

            viewModel.load(first)
            testScheduler.runCurrent()

            viewModel.load(second)
            testScheduler.runCurrent()

            val currentAlbum =
                RecentAlbum(
                    id = "current-album",
                    title = "Current Album",
                    artist = null,
                    coverArtId = null,
                )

            secondResult.complete(
                RecentAlbumsLoadResult.Success(
                    AccountScopedRecentAlbums(
                        account = second,
                        albums = listOf(currentAlbum),
                    ),
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                RecentAlbumsUiState.Content(
                    listOf(currentAlbum),
                ),
                viewModel.state,
            )

            firstResult.complete(
                RecentAlbumsLoadResult.Success(
                    AccountScopedRecentAlbums(
                        account = first,
                        albums =
                            listOf(
                                RecentAlbum(
                                    id = "stale-album",
                                    title = "Stale Album",
                                    artist = null,
                                    coverArtId = null,
                                ),
                            ),
                    ),
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                RecentAlbumsUiState.Content(
                    listOf(currentAlbum),
                ),
                viewModel.state,
            )
        }

    @Test
    fun clearRejectsAnyLateResultAndReturnsToIdle() =
        runTest {
            val alice = account("alice")

            val pending =
                CompletableDeferred<RecentAlbumsLoadResult>()

            val viewModel =
                RecentAlbumsViewModel(
                    repository =
                        DeferredRepository(
                            mapOf(alice to pending),
                        ),
                    scope = backgroundScope,
                )

            viewModel.load(alice)
            testScheduler.runCurrent()

            viewModel.clear()

            assertEquals(
                RecentAlbumsUiState.Idle,
                viewModel.state,
            )

            pending.complete(
                RecentAlbumsLoadResult.Success(
                    AccountScopedRecentAlbums(
                        account = alice,
                        albums =
                            listOf(
                                RecentAlbum(
                                    id = "late",
                                    title = "Late Album",
                                    artist = null,
                                    coverArtId = null,
                                ),
                            ),
                    ),
                ),
            )

            testScheduler.runCurrent()

            assertEquals(
                RecentAlbumsUiState.Idle,
                viewModel.state,
            )
        }

    private class QueueRepository(
        vararg results: RecentAlbumsLoadResult,
    ) : RecentAlbumsRepository {
        private val pending =
            ArrayDeque<RecentAlbumsLoadResult>().apply {
                results.forEach(::addLast)
            }

        val accounts =
            mutableListOf<ServerAccountIdentity>()

        override suspend fun loadRecentAlbums(account: ServerAccountIdentity): RecentAlbumsLoadResult {
            accounts += account
            return pending.removeFirst()
        }
    }

    private class DeferredRepository(
        private val results: Map<
            ServerAccountIdentity,
            CompletableDeferred<RecentAlbumsLoadResult>,
        >,
    ) : RecentAlbumsRepository {
        override suspend fun loadRecentAlbums(account: ServerAccountIdentity): RecentAlbumsLoadResult =
            try {
                withContext(NonCancellable) {
                    requireNotNull(results[account]).await()
                }
            } catch (error: CancellationException) {
                throw error
            }
    }

    private fun account(
        username: String,
        endpointValue: String = "https://music.example.com",
    ): ServerAccountIdentity =
        ServerAccountIdentity(
            endpoint =
                (
                    ServerEndpoint.parse(
                        endpointValue,
                    ) as EndpointParseResult.Valid
                ).endpoint,
            username = username,
        )
}
