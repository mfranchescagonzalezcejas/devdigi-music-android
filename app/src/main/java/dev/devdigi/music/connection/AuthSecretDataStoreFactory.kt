package dev.devdigi.music.connection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.File

/**
 * Application-scoped singleton delegate for the dedicated auth-secret DataStore.
 * AndroidX's top-level [preferencesDataStore] delegate returns ONE thread-safe
 * DataStore instance per (name, corruptionHandler) for the process, so every
 * production caller of [AuthSecretDataStoreFactory.create] shares the same
 * instance targeting `files/datastore/auth_secret.preferences_pb`. The canonical
 * path exactly matches the backup/device-transfer exclusions
 * (`datastore/auth_secret.preferences_pb`), so the auth secret can never be
 * persisted outside the excluded path.
 */
private val Context.authSecretDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AuthSecretDataStoreFactory.AUTH_SECRET_STORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

object AuthSecretDataStoreFactory {

    internal const val AUTH_SECRET_STORE_NAME = "auth_secret"

    /** Relative path used by backup_rules.xml / data_extraction_rules.xml. */
    internal const val AUTH_SECRET_BACKUP_RELATIVE_PATH =
        "datastore/$AUTH_SECRET_STORE_NAME.preferences_pb"

    fun create(context: Context): DataStore<Preferences> =
        context.applicationContext.authSecretDataStore

    /** Test-only seam: allows deterministic temp-file injection. NOT for production. */
    internal fun createForTest(produceFile: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = produceFile,
        )
}