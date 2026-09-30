package dev.devdigi.music.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class ConnectionLayoutSpec(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val maxFormWidth: Dp,
)

internal fun connectionLayoutSpec(availableWidth: Dp): ConnectionLayoutSpec =
    when {
        availableWidth < 400.dp -> {
            ConnectionLayoutSpec(
                horizontalPadding = 16.dp,
                verticalPadding = 20.dp,
                maxFormWidth = 560.dp,
            )
        }

        availableWidth < 600.dp -> {
            ConnectionLayoutSpec(
                horizontalPadding = 24.dp,
                verticalPadding = 24.dp,
                maxFormWidth = 560.dp,
            )
        }

        else -> {
            ConnectionLayoutSpec(
                horizontalPadding = 32.dp,
                verticalPadding = 32.dp,
                maxFormWidth = 560.dp,
            )
        }
    }

internal data class ConnectionUiPolicy(
    val serverDraftSaved: Boolean,
    val busy: Boolean,
    val credentialsEditable: Boolean,
    val canSaveServer: Boolean,
    val canSignIn: Boolean,
    val canSignOut: Boolean,
    val canDeleteServer: Boolean,
)

internal fun connectionUiPolicy(state: ServerConnectionUiState): ConnectionUiPolicy {
    val busy =
        state.sessionStatus == SessionStatus.RESTORING ||
            state.sessionStatus == SessionStatus.SIGNING_IN

    val savedEndpoint =
        state.profile?.endpoint?.value

    val serverDraftSaved =
        savedEndpoint != null &&
            savedEndpoint == state.endpointInput &&
            state.urlValidity is UrlValidity.Valid

    val authenticated =
        state.sessionStatus == SessionStatus.AUTHENTICATED

    val signOutRetry =
        state.sessionStatus == SessionStatus.SIGN_OUT_FAILED

    return ConnectionUiPolicy(
        serverDraftSaved = serverDraftSaved,
        busy = busy,
        credentialsEditable =
            !busy &&
                !authenticated &&
                !signOutRetry,
        canSaveServer =
            !busy &&
                state.endpointInput.isNotBlank() &&
                (
                    savedEndpoint == null ||
                        savedEndpoint != state.endpointInput
                ),
        canSignIn =
            !busy &&
                state.sessionStatus == SessionStatus.SIGNED_OUT &&
                serverDraftSaved &&
                state.usernameInput.isNotBlank() &&
                state.passwordInput.isNotBlank(),
        canSignOut =
            !busy &&
                (authenticated || signOutRetry),
        canDeleteServer =
            !busy &&
                state.profile != null,
    )
}

@Composable
fun ServerConnectionScreen(
    state: ServerConnectionUiState,
    onEndpointChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .imePadding(),
    ) {
        val layout =
            connectionLayoutSpec(maxWidth)

        val policy =
            connectionUiPolicy(state)

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = layout.maxFormWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal =
                            layout.horizontalPadding,
                        vertical =
                            layout.verticalPadding,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Connect your music server",
                style =
                    MaterialTheme.typography.headlineSmall,
            )

            Text(
                text =
                    "Connect to your own Navidrome or compatible Subsonic server.",
                style =
                    MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = state.endpointInput,
                onValueChange = onEndpointChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server URL") },
                placeholder = {
                    Text("https://music.example.com")
                },
                singleLine = true,
                isError =
                    state.urlValidity is UrlValidity.Invalid,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                supportingText = {
                    when {
                        state.urlValidity
                            is UrlValidity.Invalid -> {
                            Text(
                                "Enter a safe HTTPS server URL.",
                            )
                        }

                        state.profile != null &&
                            !policy.serverDraftSaved -> {
                            Text(
                                "Save server changes before signing in.",
                            )
                        }
                    }
                },
            )

            Button(
                onClick = onConfirm,
                enabled = policy.canSaveServer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.profile == null) {
                        "Save server"
                    } else {
                        "Save server changes"
                    },
                )
            }

            if (state.profile != null) {
                Text(
                    text =
                        "Saved server: ${state.profile.endpoint.value}",
                    style =
                        MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = state.usernameInput,
                onValueChange = onUsernameChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    policy.credentialsEditable,
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
            )

            OutlinedTextField(
                value = state.passwordInput,
                onValueChange = onPasswordChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    policy.credentialsEditable,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation =
                    PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (policy.canSignIn) {
                                onSignIn()
                            }
                        },
                    ),
            )

            when (state.sessionStatus) {
                SessionStatus.AUTHENTICATED -> {
                    val identity =
                        state.identity

                    Text(
                        text =
                            if (identity != null) {
                                "Connected as ${identity.username}"
                            } else {
                                "Connected"
                            },
                        style =
                            MaterialTheme.typography.titleMedium,
                    )

                    state.metadata?.let { metadata ->
                        Text(
                            text =
                                buildString {
                                    append(metadata.serverType)
                                    append(" · ")
                                    append(metadata.serverVersion)

                                    if (metadata.openSubsonic) {
                                        append(" · OpenSubsonic")
                                    }
                                },
                            style =
                                MaterialTheme.typography.bodySmall,
                        )
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        enabled = policy.canSignOut,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sign out")
                    }
                }

                SessionStatus.SIGN_OUT_FAILED -> {
                    OutlinedButton(
                        onClick = onSignOut,
                        enabled = policy.canSignOut,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry sign out")
                    }
                }

                else -> {
                    Button(
                        onClick = onSignIn,
                        enabled = policy.canSignIn,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sign in")
                    }
                }
            }

            if (policy.busy) {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )

                    Text(
                        text =
                            when (state.sessionStatus) {
                                SessionStatus.RESTORING -> {
                                    "Restoring session…"
                                }

                                SessionStatus.SIGNING_IN -> {
                                    "Signing in…"
                                }

                                else -> {
                                    state.statusMessage
                                }
                            },
                    )
                }
            } else if (
                state.statusMessage.isNotBlank()
            ) {
                Text(
                    text = state.statusMessage,
                    style =
                        MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.profile != null) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = policy.canDeleteServer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete server")
                }
            }
        }
    }
}
