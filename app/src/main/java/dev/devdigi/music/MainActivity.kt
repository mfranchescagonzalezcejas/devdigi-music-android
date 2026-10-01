package dev.devdigi.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.devdigi.music.features.library.data.SecureRecentAlbumsRepository
import dev.devdigi.music.features.library.data.remote.OkHttpRecentAlbumsRemoteDataSource
import dev.devdigi.music.features.library.presentation.RecentAlbumsScreen
import dev.devdigi.music.features.library.presentation.RecentAlbumsViewModel

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

                    val connectionState =
                        connectionViewModel.state

                    val identity =
                        connectionState.identity

                    var selectedAlbumId by remember {
                        mutableStateOf<String?>(null)
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
                            selectedAlbumId = null

                            recentAlbumsViewModel.load(
                                identity,
                            )
                        } else {
                            selectedAlbumId = null
                            recentAlbumsViewModel.clear()
                        }
                    }

                    if (
                        connectionState.sessionStatus ==
                        SessionStatus.AUTHENTICATED &&
                        identity != null
                    ) {
                        RecentAlbumsScreen(
                            state =
                                recentAlbumsViewModel.state,
                            username =
                                identity.username,
                            selectedAlbumId =
                            selectedAlbumId,
                            onAlbumSelected = {
                                selectedAlbumId = it
                            },
                            onRetry =
                                recentAlbumsViewModel::retry,
                            onSignOut =
                                connectionViewModel::signOut,
                        )
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
