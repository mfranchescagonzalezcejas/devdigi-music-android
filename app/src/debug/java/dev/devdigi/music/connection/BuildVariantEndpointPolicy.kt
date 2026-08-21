package dev.devdigi.music.connection

object BuildVariantEndpointPolicy : EndpointPolicy {
    private val localHttpHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

    override fun allowsHttp(host: String): Boolean = host in localHttpHosts
}
