package dev.devdigi.music.connection

import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthCredentialsBoundaryTest {

    @Test
    fun authCredentialsToStringNeverExposesPasswordOrDerivedToken() {
        val password = "sesame"
        val salt = "c19b2d"
        val credentials = AuthCredentials.create("alice", password)
        val derivedToken = md5(password + salt)

        val text = credentials.toString()

        assertFalse("toString leaked the password", text.contains(password))
        assertFalse("toString leaked the derived token", text.contains(derivedToken))
    }

    @Test
    fun storedCredentialsToStringNeverExposesSecret() {
        val secret = "secret-password"
        val stored = StoredCredentials("alice", secret)

        val text = stored.toString()

        assertFalse("StoredCredentials.toString leaked the secret", text.contains(secret))
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
