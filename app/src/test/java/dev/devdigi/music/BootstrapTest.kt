package dev.devdigi.music

import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapTest {
    @Test
    fun applicationIdIsStable() {
        assertEquals("dev.devdigi.music", BuildConfig.APPLICATION_ID)
    }
}
