package dev.devdigi.music.connection

object BuildVariantEndpointPolicy : EndpointPolicy {
    override fun allowsHttp(host: String): Boolean = false
}
