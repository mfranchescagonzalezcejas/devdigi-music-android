package dev.devdigi.music.connection

import java.security.MessageDigest
import java.security.SecureRandom

interface SubsonicAuthSigner {
    fun sign(credentials: AuthCredentials): AuthSignature
}

data class AuthSignature(val salt: String, val token: String)

class DefaultSubsonicAuthSigner(
    private val saltSource: () -> String = { randomHexSalt() },
) : SubsonicAuthSigner {
    override fun sign(credentials: AuthCredentials): AuthSignature {
        val salt = saltSource()
        val token = md5(credentials.password + salt)
        return AuthSignature(salt, token)
    }

    private companion object {
        private const val SALT_BYTES = 8

        private fun md5(input: String): String {
            val digest = MessageDigest.getInstance("MD5")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        private fun randomHexSalt(): String {
            val bytes = ByteArray(SALT_BYTES)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
