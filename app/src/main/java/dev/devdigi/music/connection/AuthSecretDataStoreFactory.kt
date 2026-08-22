package dev.devdigi.music.connection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File

/**
 * Explicit factory for the dedicated auth-secret DataStore.
 *
 * Uses [ReplaceFileCorruptionHandler] so a corrupt Preferences protobuf is replaced
 * with empty preferences (restore reads empty state and a later save works). This
 * policy is scoped to the auth store only; it is NOT used for `server_profile`.
 *
 * Production creation is PATH-BOUND: the canonical DataStore name `auth_secret`
 * resolves to `files/datastore/auth_secret.preferences_pb`, exactly matching the
 * backup/device-transfer exclusions (`datastore/auth_secret.preferences_pb`).
 * Callers supply a [Context], never an arbitrary file callback, so the auth secret
 * can never be persisted outside the excluded path.
 */
object AuthSecretDataStoreFactory {

    internal const val AUTH_SECRET_STORE_NAME = "auth_secret"

    /** Relative path used by backup_rules.xml / data_extraction_rules.xml. */
    internal const val AUTH_SECRET_BACKUP_RELATIVE_PATH =
        "datastore/$AUTH_SECRET_STORE_NAME.preferences_pb"

    fun create(context: Context): DataStore<Preferences> =
        createWithFile { context.preferencesDataStoreFile(AUTH_SECRET_STORE_NAME) }

    /** Test-only seam: allows deterministic temp-file injection. NOT for production. */
    internal fun createForTest(produceFile: () -> File): DataStore<Preferences> =
        createWithFile(produceFile)

    private fun createWithFile(produceFile: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = produceFile,
        )
}