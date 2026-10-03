package dev.devdigi.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.devdigi.music.connection.AesGcmSecretCipher
import dev.devdigi.music.connection.AndroidKeystoreAuthKeyProvider
import dev.devdigi.music.connection.AuthSecretDataStoreFactory
import dev.devdigi.music.connection.DataStoreAuthSecretStore
import dev.devdigi.music.connection.DefaultSubsonicAuthSigner
import dev.devdigi.music.connection.OkHttpAuthenticatedPingClient
import dev.devdigi.music.connection.ServerConnectionScreen
import dev.devdigi.music.connection.ServerConnectionViewModel
import dev.devdigi.music.connection.SessionStatus
import dev.devdigi.music.connection.serverProfileRepository
import dev.devdigi.music.features.library.data.SecureAlbumDetailsRepository
import dev.devdigi.music.features.library.data.SecureRecentAlbumsRepository
import dev.devdigi.music.features.library.data.remote.OkHttpAlbumDetailsRemoteDataSource
import dev.devdigi.music.features.library.data.remote.OkHttpRecentAlbumsRemoteDataSource
import dev.devdigi.music.features.library.presentation.AlbumDetailsScreen
import dev.devdigi.music.features.library.presentation.AlbumDetailsViewModel
import dev.devdigi.music.features.library.presentation.LibraryDestination
import dev.devdigi.music.features.library.presentation.RecentAlbumsScreen
import dev.devdigi.music.features.library.presentation.RecentAlbumsViewModel
import dev.devdigi.music.features.library.presentation.libraryDestination
import dev.devdigi.music.features.navigation.presentation.FirstSoundNavigationState
import dev.devdigi.music.features.navigation.presentation.FirstSoundNowPlayingScreen
import dev.devdigi.music.features.navigation.presentation.FirstSoundPlaceholderScreen
import dev.devdigi.music.features.navigation.presentation.FirstSoundPrimaryDestination
import dev.devdigi.music.features.navigation.presentation.FirstSoundShell
import dev.devdigi.music.features.navigation.presentation.backFromFirstSoundSecondary
import dev.devdigi.music.features.navigation.presentation.closeFirstSoundNowPlaying
import dev.devdigi.music.features.navigation.presentation.firstSoundNavigationState
import dev.devdigi.music.features.navigation.presentation.openFirstSoundAlbum
import dev.devdigi.music.features.navigation.presentation.openFirstSoundNowPlaying
import dev.devdigi.music.features.navigation.presentation.selectFirstSoundPrimary
import dev.devdigi.music.features.playback.data.createMedia3ControllerPlaybackEngine
import dev.devdigi.music.features.playback.domain.PlaybackTrack
import dev.devdigi.music.features.playback.presentation.PlaybackViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverRepository =
            serverProfileRepository(
                applicationContext,
            )

        val secretStore =
            DataStoreAuthSecretStore(
                dataStore =
                    AuthSecretDataStoreFactory.create(
                        applicationContext,
                    ),
                cipher =
                    AesGcmSecretCipher(
                        AndroidKeystoreAuthKeyProvider(),
                    ),
            )

        val authSigner =
            DefaultSubsonicAuthSigner()

        val pingClient =
            OkHttpAuthenticatedPingClient(
                signer = authSigner,
            )

        val recentAlbumsRepository =
            SecureRecentAlbumsRepository(
                secretStore = secretStore,
                remote =
                    OkHttpRecentAlbumsRemoteDataSource(
                        signer = authSigner,
                    ),
            )

        val albumDetailsRepository =
            SecureAlbumDetailsRepository(
                secretStore = secretStore,
                remote =
                    OkHttpAlbumDetailsRemoteDataSource(
                        signer = authSigner,
                    ),
            )

        setContent {
            MaterialTheme {
                Surface {
                    val connectionViewModel:
                        ServerConnectionViewModel =
                        viewModel(
                            factory =
                                ServerConnectionViewModel
                                    .factory(
                                        repository =
                                        serverRepository,
                                        secretStore =
                                        secretStore,
                                        pingClient =
                                        pingClient,
                                    ),
                        )

                    val recentAlbumsViewModel:
                        RecentAlbumsViewModel =
                        viewModel(
                            factory =
                                RecentAlbumsViewModel
                                    .factory(
                                        recentAlbumsRepository,
                                    ),
                        )

                    val albumDetailsViewModel:
                        AlbumDetailsViewModel =
                        viewModel(
                            factory =
                                AlbumDetailsViewModel
                                    .factory(
                                        albumDetailsRepository,
                                    ),
                        )

                    val playbackViewModel:
                        PlaybackViewModel =
                        viewModel(
                            factory =
                                PlaybackViewModel
                                    .factory {
                                        createMedia3ControllerPlaybackEngine(
                                            context =
                                            applicationContext,
                                        )
                                    },
                        )

                    val connectionState =
                        connectionViewModel.state

                    val identity =
                        connectionState.identity

                    var primaryDestinationName by
                        rememberSaveable(
                            connectionState.sessionStatus,
                            identity,
                        ) {
                            mutableStateOf(
                                FirstSoundPrimaryDestination
                                    .HOME
                                    .name,
                            )
                        }

                    var selectedAlbumId by
                        rememberSaveable(
                            connectionState.sessionStatus,
                            identity,
                        ) {
                            mutableStateOf<String?>(null)
                        }

                    var nowPlayingVisible by
                        rememberSaveable(
                            connectionState.sessionStatus,
                            identity,
                        ) {
                            mutableStateOf(false)
                        }

                    val navigationState =
                        firstSoundNavigationState(
                            savedPrimaryDestination =
                            primaryDestinationName,
                            selectedAlbumId =
                            selectedAlbumId,
                            nowPlayingVisible =
                            nowPlayingVisible,
                        )

                    fun applyNavigation(next: FirstSoundNavigationState) {
                        primaryDestinationName =
                            next.primaryDestination.name
                        selectedAlbumId =
                            next.selectedAlbumId
                        nowPlayingVisible =
                            next.nowPlayingVisible
                    }

                    LaunchedEffect(
                        connectionState.sessionStatus,
                        identity,
                    ) {
                        playbackViewModel
                            .onAccountChanged(
                                if (
                                    connectionState
                                        .sessionStatus ==
                                    SessionStatus.AUTHENTICATED
                                ) {
                                    identity
                                } else {
                                    null
                                },
                            )
                    }

                    LaunchedEffect(
                        connectionState.sessionStatus,
                        identity,
                    ) {
                        if (
                            connectionState.sessionStatus ==
                            SessionStatus.AUTHENTICATED &&
                            identity != null
                        ) {
                            recentAlbumsViewModel.load(
                                identity,
                            )
                        } else {
                            recentAlbumsViewModel.clear()
                        }
                    }

                    LaunchedEffect(
                        connectionState.sessionStatus,
                        identity,
                        selectedAlbumId,
                    ) {
                        val albumId =
                            selectedAlbumId

                        if (
                            connectionState.sessionStatus ==
                            SessionStatus.AUTHENTICATED &&
                            identity != null &&
                            albumId != null
                        ) {
                            albumDetailsViewModel.load(
                                account = identity,
                                albumId = albumId,
                            )
                        } else {
                            albumDetailsViewModel.clear()
                        }
                    }

                    if (
                        connectionState.sessionStatus ==
                        SessionStatus.AUTHENTICATED &&
                        identity != null
                    ) {
                        FirstSoundShell(
                            activeDestination =
                                navigationState
                                    .primaryDestination,
                            playbackState =
                                playbackViewModel
                                    .state,
                            showMiniPlayer =
                                !navigationState
                                    .nowPlayingVisible,
                            onDestinationSelected = { destination ->
                                applyNavigation(
                                    selectFirstSoundPrimary(
                                        state =
                                        navigationState,
                                        destination =
                                        destination,
                                    ),
                                )
                            },
                            onOpenNowPlaying = {
                                applyNavigation(
                                    openFirstSoundNowPlaying(
                                        navigationState,
                                    ),
                                )
                            },
                            onPausePlayback =
                                playbackViewModel::pause,
                            onResumePlayback =
                                playbackViewModel::resume,
                            onRetryPlayback =
                                playbackViewModel::retry,
                        ) {
                            if (
                                navigationState
                                    .nowPlayingVisible
                            ) {
                                FirstSoundNowPlayingScreen(
                                    state =
                                        playbackViewModel
                                            .state,
                                    onPrevious =
                                        playbackViewModel::previous,
                                    onNext =
                                        playbackViewModel::next,
                                    onPause =
                                        playbackViewModel::pause,
                                    onResume =
                                        playbackViewModel::resume,
                                    onStop =
                                        playbackViewModel::stop,
                                    onRetry =
                                        playbackViewModel::retry,
                                    onBack = {
                                        applyNavigation(
                                            closeFirstSoundNowPlaying(
                                                navigationState,
                                            ),
                                        )
                                    },
                                )
                            } else {
                                when (
                                    navigationState
                                        .primaryDestination
                                ) {
                                    FirstSoundPrimaryDestination
                                        .HOME,
                                    FirstSoundPrimaryDestination
                                        .LIBRARY,
                                    -> {
                                        when (
                                            val destination =
                                                libraryDestination(
                                                    navigationState
                                                        .selectedAlbumId,
                                                )
                                        ) {
                                            LibraryDestination
                                                .RecentAlbums,
                                            -> {
                                                RecentAlbumsScreen(
                                                    state =
                                                        recentAlbumsViewModel
                                                            .state,
                                                    username =
                                                        identity.username,
                                                    selectedAlbumId =
                                                        navigationState
                                                            .selectedAlbumId,
                                                    onAlbumSelected = { albumId ->
                                                        applyNavigation(
                                                            openFirstSoundAlbum(
                                                                state =
                                                                navigationState,
                                                                albumId =
                                                                albumId,
                                                            ),
                                                        )
                                                    },
                                                    onRetry =
                                                        recentAlbumsViewModel::retry,
                                                    onSignOut = {
                                                        playbackViewModel
                                                            .clearPlayback()
                                                        connectionViewModel
                                                            .signOut()
                                                    },
                                                )
                                            }

                                            is LibraryDestination
                                                .AlbumDetails,
                                            -> {
                                                AlbumDetailsScreen(
                                                    state =
                                                        albumDetailsViewModel
                                                            .state,
                                                    playbackState =
                                                        playbackViewModel
                                                            .state,
                                                    onTrackSelected = {
                                                        tracks,
                                                        selectedIndex,
                                                        ->
                                                        playbackViewModel
                                                            .playQueue(
                                                                account =
                                                                identity,
                                                                entries =
                                                                    tracks.map { track ->
                                                                        PlaybackTrack(
                                                                            id =
                                                                                track.id,
                                                                            title =
                                                                                track.title,
                                                                            artist =
                                                                                track.artist,
                                                                        )
                                                                    },
                                                                selectedIndex =
                                                                selectedIndex,
                                                            )
                                                    },
                                                    onBack = {
                                                        applyNavigation(
                                                            backFromFirstSoundSecondary(
                                                                navigationState,
                                                            ),
                                                        )
                                                    },
                                                    onRetry =
                                                        albumDetailsViewModel::retry,
                                                    onSignOut = {
                                                        playbackViewModel
                                                            .clearPlayback()
                                                        connectionViewModel
                                                            .signOut()
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    FirstSoundPrimaryDestination
                                        .SEARCH,
                                    FirstSoundPrimaryDestination
                                        .DISCOVER,
                                    -> {
                                        FirstSoundPlaceholderScreen(
                                            destination =
                                                navigationState
                                                    .primaryDestination,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        ServerConnectionScreen(
                            state = connectionState,
                            onEndpointChanged =
                                connectionViewModel::onEndpointChanged,
                            onUsernameChanged =
                                connectionViewModel::onUsernameChanged,
                            onPasswordChanged =
                                connectionViewModel::onPasswordChanged,
                            onConfirm =
                                connectionViewModel::confirm,
                            onSignIn =
                                connectionViewModel::signIn,
                            onSignOut =
                                connectionViewModel::signOut,
                            onDelete =
                                connectionViewModel::delete,
                        )
                    }
                }
            }
        }
    }
}
