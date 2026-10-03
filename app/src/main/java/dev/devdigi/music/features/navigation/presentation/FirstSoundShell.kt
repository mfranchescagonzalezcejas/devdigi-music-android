package dev.devdigi.music.features.navigation.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FirstSoundShell(
    activeDestination: FirstSoundPrimaryDestination,
    onDestinationSelected: (FirstSoundPrimaryDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier =
            Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                FirstSoundPrimaryDestination
                    .entries
                    .forEach { destination ->
                        NavigationBarItem(
                            selected =
                                destination ==
                                    activeDestination,
                            onClick = {
                                onDestinationSelected(
                                    destination,
                                )
                            },
                            icon = {
                                Text(
                                    text =
                                        destination
                                            .label
                                            .take(1),
                                )
                            },
                            label = {
                                Text(
                                    text =
                                        destination.label,
                                )
                            },
                            alwaysShowLabel = true,
                        )
                    }
            }
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            content()
        }
    }
}

@Composable
internal fun FirstSoundPlaceholderScreen(destination: FirstSoundPrimaryDestination) {
    val message =
        firstSoundPlaceholderMessage(
            destination,
        )
            ?: return

    Box(
        modifier =
            Modifier.fillMaxSize(),
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(
                        max = 420.dp,
                    ).padding(24.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            Text(
                text =
                    destination.label,
                style =
                    MaterialTheme.typography
                        .headlineMedium,
            )

            Text(
                text = message,
                style =
                    MaterialTheme.typography
                        .bodyLarge,
            )
        }
    }
}
