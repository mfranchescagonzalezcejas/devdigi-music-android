package dev.devdigi.music.connection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.serverProfileDataStore by preferencesDataStore(name = "server_profile")
private val serverEndpointKey = stringPreferencesKey("server_endpoint")

interface ServerProfileRepository {
    val profile: Flow<ServerProfile?>

    suspend fun save(profile: ServerProfile)

    suspend fun delete()
}

class DataStoreServerProfileRepository(
    private val dataStore: DataStore<Preferences>,
    private val endpointPolicy: EndpointPolicy = BuildVariantEndpointPolicy,
) : ServerProfileRepository {
    override val profile: Flow<ServerProfile?> = dataStore.data.map { preferences ->
        (preferences[serverEndpointKey] as? String)?.let { value ->
            (ServerEndpoint.parse(value, endpointPolicy) as? EndpointParseResult.Valid)?.endpoint?.let(::ServerProfile)
        }
    }

    override suspend fun save(profile: ServerProfile) {
        dataStore.edit { preferences -> preferences[serverEndpointKey] = profile.endpoint.value }
    }

    override suspend fun delete() {
        dataStore.edit { preferences -> preferences.remove(serverEndpointKey) }
    }
}

fun serverProfileRepository(context: Context): ServerProfileRepository =
    DataStoreServerProfileRepository(context.applicationContext.serverProfileDataStore)
