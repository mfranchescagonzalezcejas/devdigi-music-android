package dev.devdigi.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.devdigi.music.connection.AesGcmSecretCipher
import dev.devdigi.music.connection.AndroidKeystoreAuthKeyProvider
import dev.devdigi.music.connection.AuthSecretDataStoreFactory
import dev.devdigi.music.connection.DataStoreAuthSecretStore
import dev.devdigi.music.connection.DefaultSubsonicAuthSigner
import dev.devdigi.music.connection.OkHttpAuthenticatedPingClient
import dev.devdigi.music.connection.ServerConnectionScreen
import dev.devdigi.music.connection.ServerConnectionViewModel
import dev.devdigi.music.connection.serverProfileRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository =
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

        val pingClient =
            OkHttpAuthenticatedPingClient(
                signer =
                    DefaultSubsonicAuthSigner(),
            )

        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: ServerConnectionViewModel =
                        viewModel(
                            factory =
                                ServerConnectionViewModel.factory(
                                    repository = repository,
                                    secretStore = secretStore,
                                    pingClient = pingClient,
                                ),
                        )

                    ServerConnectionScreen(
                        state = viewModel.state,
                        onEndpointChanged =
                            viewModel::onEndpointChanged,
                        onConfirm = viewModel::confirm,
                        onDelete = viewModel::delete,
                    )
                }
            }
        }
    }
}
