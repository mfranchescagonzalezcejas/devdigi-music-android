package dev.devdigi.music.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ServerConnectionScreen(
    state: ServerConnectionUiState,
    onEndpointChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connect your music server")
        OutlinedTextField(
            value = state.endpointInput,
            onValueChange = onEndpointChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server URL") },
            singleLine = true,
            isError = state.urlValidity is UrlValidity.Invalid,
        )
        if (state.urlValidity is UrlValidity.Invalid) {
            Text("Enter a safe server URL.")
        }
        Button(onClick = onConfirm) {
            Text("Save server")
        }
        if (state.profile != null) {
            Text("Server saved: ${state.profile.endpoint.value}")
            Button(onClick = onDelete) {
                Text("Delete server")
            }
        }
        Text(state.statusMessage)
    }
}
