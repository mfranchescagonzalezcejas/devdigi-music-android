package dev.devdigi.music.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConnectionViewModelTest {
    @Test
    fun editingKeepsTheEnteredEndpointAndLeavesVerificationUnchecked() = runTest {
        val viewModel = ServerConnectionViewModel(FakeRepository(), backgroundScope)

        viewModel.onEndpointChanged("https://music.example.com/navidrome")

        assertEquals("https://music.example.com/navidrome", viewModel.state.endpointInput)
        assertEquals(UrlValidity.UNCHECKED, viewModel.state.urlValidity)
        assertEquals(ConnectionFacts(), viewModel.state.connectionFacts)
    }

    @Test
    fun confirmingAnInvalidEndpointDoesNotCreateAProfile() = runTest {
        val viewModel = ServerConnectionViewModel(FakeRepository(), backgroundScope)
        viewModel.onEndpointChanged("http://music.example.com")

        viewModel.confirm()

        assertTrue(viewModel.state.urlValidity is UrlValidity.Invalid)
        assertNull(viewModel.state.profile)
    }

    @Test
    fun confirmingAValidEndpointCreatesANonSecretProfileAndRequiresSignIn() = runTest {
        val repository = FakeRepository()
        val viewModel = ServerConnectionViewModel(repository, backgroundScope)
        viewModel.onEndpointChanged("HTTPS://music.example.com:443/navidrome")

        viewModel.confirm()
        testScheduler.runCurrent()

        assertEquals(
            ServerProfile(ServerEndpoint("https://music.example.com/navidrome")),
            viewModel.state.profile,
        )
        assertEquals(UrlValidity.Valid(viewModel.state.profile!!), viewModel.state.urlValidity)
        assertEquals(ConnectionFacts(), viewModel.state.connectionFacts)
        assertEquals("Sign in is required before this server can be verified.", viewModel.state.statusMessage)
    }

    @Test
    fun restoresReplacesAndDeletesTheProfileFromTheRepositoryFlow() = runTest {
        val repository = FakeRepository(ServerProfile(ServerEndpoint("https://music.example.com")))
        val viewModel = ServerConnectionViewModel(repository, backgroundScope)
        testScheduler.runCurrent()

        assertEquals("https://music.example.com", viewModel.state.endpointInput)
        repository.profileState.value = ServerProfile(ServerEndpoint("https://other.example.com"))
        testScheduler.runCurrent()
        assertEquals("https://other.example.com", viewModel.state.profile?.endpoint?.value)

        viewModel.delete()
        testScheduler.runCurrent()
        assertNull(viewModel.state.profile)
        assertEquals("", viewModel.state.endpointInput)
    }

    @Test
    fun writeFailuresDoNotClaimThatTheProfileWasSavedOrDeleted() = runTest {
        val repository = FakeRepository(failWrites = true)
        val viewModel = ServerConnectionViewModel(repository, backgroundScope)
        viewModel.onEndpointChanged("https://music.example.com")

        viewModel.confirm()
        testScheduler.runCurrent()

        assertNull(viewModel.state.profile)
        assertEquals("Unable to save server.", viewModel.state.statusMessage)
    }

    private class FakeRepository(
        initial: ServerProfile? = null,
        private val failWrites: Boolean = false,
    ) : ServerProfileRepository {
        val profileState = MutableStateFlow(initial)
        override val profile = profileState

        override suspend fun save(profile: ServerProfile) {
            check(!failWrites)
            profileState.value = profile
        }

        override suspend fun delete() {
            check(!failWrites)
            profileState.value = null
        }
    }
}
