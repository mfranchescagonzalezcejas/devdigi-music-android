package dev.devdigi.music.connection

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerProfileRepositoryTest {
    @Test
    fun restoresRevalidatedEndpointAndReplacesItUsingOnlyTheEndpointKey() = runBlocking {
        val store = PreferenceDataStoreFactory.create { temporaryFile() }
        val repository = DataStoreServerProfileRepository(store, EndpointPolicy { false })
        val first = ServerProfile(endpoint("https://music.example.com/navidrome"))
        val replacement = ServerProfile(endpoint("https://music.example.com/replacement"))

        repository.save(first)
        assertEquals(first, repository.profile.first())
        repository.save(replacement)

        assertEquals(replacement, repository.profile.first())
        assertEquals(setOf("server_endpoint"), store.data.first().asMap().keys.map { it.name }.toSet())
    }

    @Test
    fun deletesAndDiscardsMalformedStoredEndpoints() = runBlocking {
        val store = PreferenceDataStoreFactory.create { temporaryFile() }
        val repository = DataStoreServerProfileRepository(store, EndpointPolicy { false })
        store.edit { it[stringPreferencesKey("server_endpoint")] = "http://music.example.com" }

        assertNull(repository.profile.first())
        repository.save(ServerProfile(endpoint("https://music.example.com")))
        repository.delete()

        assertNull(repository.profile.first())
        assertNull(store.data.first()[stringPreferencesKey("server_endpoint")])
    }

    @Test
    fun saveRevalidatesTheProfileBeforeWriting() = runBlocking {
        val store = PreferenceDataStoreFactory.create { temporaryFile() }
        val repository = DataStoreServerProfileRepository(store, HttpsOnlyEndpointPolicy)
        val debugLocalEndpoint = (ServerEndpoint.parse(
            "http://localhost:4533",
            EndpointPolicy { it == "localhost" },
        ) as EndpointParseResult.Valid).endpoint

        repository.save(ServerProfile(debugLocalEndpoint))

        assertNull(store.data.first()[stringPreferencesKey("server_endpoint")])
        assertNull(repository.profile.first())
    }

    private fun temporaryFile(): File = File.createTempFile("server-profile", ".preferences_pb").apply { delete() }

    private fun endpoint(value: String): ServerEndpoint =
        (ServerEndpoint.parse(value) as EndpointParseResult.Valid).endpoint
}
