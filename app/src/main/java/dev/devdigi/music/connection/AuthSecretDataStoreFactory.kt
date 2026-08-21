package dev.devdigi.music.connection

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File

/**
 * Explicit factory for the dedicated auth-secret DataStore.
 *
 * Uses [ReplaceFileCorruptionHandler] so a corrupt Preferences protobuf is replaced
 * with empty preferences (restore reads empty state and a later save works). This
 * policy is scoped to the auth store only; it is NOT used for `server_profile`.
 */
object AuthSecretDataStoreFactory {
    fun create(produceFile: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = produceFile,
        )
}