package dev.devdigi.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.devdigi.music.connection.ServerConnectionScreen
import dev.devdigi.music.connection.ServerConnectionViewModel
import dev.devdigi.music.connection.serverProfileRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: ServerConnectionViewModel = viewModel(
                        factory = ServerConnectionViewModel.factory(serverProfileRepository(applicationContext)),
                    )
                    ServerConnectionScreen(
                        state = viewModel.state,
                        onEndpointChanged = viewModel::onEndpointChanged,
                        onConfirm = viewModel::confirm,
                        onDelete = viewModel::delete,
                    )
                }
            }
        }
    }
}
