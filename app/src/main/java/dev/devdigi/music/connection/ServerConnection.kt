package dev.devdigi.music.connection

import java.net.URI

data class ServerEndpoint(val value: String) {
    companion object {
        fun parse(
            input: String,
            endpointPolicy: EndpointPolicy = BuildVariantEndpointPolicy,
        ): EndpointParseResult {
            if (input.any(Char::isISOControl)) return EndpointParseResult.Invalid

            val value = input.trim()
            val uri = runCatching { URI(value) }.getOrNull() ?: return EndpointParseResult.Invalid
            val host = uri.host?.lowercase() ?: return EndpointParseResult.Invalid
            val hostWithoutTerminalDots = host.trimEnd('.')
            val scheme = uri.scheme?.lowercase() ?: return EndpointParseResult.Invalid
            val isPermittedLocalHttp = scheme == "http" && endpointPolicy.allowsHttp(hostWithoutTerminalDots)
            if (
                !uri.isAbsolute ||
                (scheme != "https" && !isPermittedLocalHttp) ||
                uri.userInfo != null ||
                uri.query != null ||
                uri.fragment != null ||
                (!isPermittedLocalHttp && hostWithoutTerminalDots == "localhost") ||
                (!isPermittedLocalHttp && hostWithoutTerminalDots.endsWith(".localhost")) ||
                hostWithoutTerminalDots.endsWith(".local") ||
                host.startsWith('[') ||
                host.all(Char::isDigit) ||
                (uri.port != -1 && uri.port !in 1..65535) ||
                (!isPermittedLocalHttp && isProhibitedIpv4(host)) ||
                hasAmbiguousPath(uri.rawPath)
            ) {
                return EndpointParseResult.Invalid
            }

            val defaultPort = if (scheme == "https") 443 else 80
            val port = uri.port.takeUnless { it == -1 || it == defaultPort }?.let { ":$it" }.orEmpty()
            val path = uri.rawPath
                ?.takeUnless { it == "/" }
                ?.trimEnd('/')
                .orEmpty()
            return EndpointParseResult.Valid(ServerEndpoint("$scheme://$host$port$path"))
        }

        private fun hasAmbiguousPath(path: String?): Boolean {
            if (path == null) return false
            val lowercasePath = path.lowercase()
            return path.contains('\\') ||
                lowercasePath.contains("%2e") ||
                lowercasePath.contains("%5c") ||
                path.split('/').any { it == "." || it == ".." }
        }

        private fun isProhibitedIpv4(host: String): Boolean {
            val octets = host.split('.').takeIf { it.size == 4 }?.map { it.toIntOrNull() } ?: return false
            if (octets.any { it == null || it !in 0..255 }) return false

            val (first, second) = octets.map { it!! }
            return first == 0 ||
                first == 10 ||
                first == 127 ||
                (first == 169 && second == 254) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168)
        }
    }
}

fun interface EndpointPolicy {
    fun allowsHttp(host: String): Boolean
}

sealed interface EndpointParseResult {
    data class Valid(val endpoint: ServerEndpoint) : EndpointParseResult
    data object Invalid : EndpointParseResult
}

data class ServerProfile(val endpoint: ServerEndpoint)

sealed interface UrlValidity {
    data object UNCHECKED : UrlValidity
    data class Invalid(val reason: EndpointParseResult.Invalid = EndpointParseResult.Invalid) : UrlValidity
    data class Valid(val profile: ServerProfile) : UrlValidity
}

enum class Reachability { NOT_CHECKED, REACHABLE, UNREACHABLE }

enum class Compatibility { NOT_CHECKED, COMPATIBLE, INCOMPATIBLE }

enum class Authentication { NOT_CHECKED, AUTHENTICATED, REJECTED }

data class ConnectionFacts(
    val reachability: Reachability = Reachability.NOT_CHECKED,
    val compatibility: Compatibility = Compatibility.NOT_CHECKED,
    val authentication: Authentication = Authentication.NOT_CHECKED,
)

sealed interface PingObservation {
    data class ProtocolResponse(val protocolVersion: String) : PingObservation
    data object NetworkError : PingObservation
    data object UnexpectedResponse : PingObservation
    data object Unauthenticated : PingObservation
}

fun interface PingClient {
    suspend fun ping(profile: ServerProfile): PingObservation
}

fun reducePingObservation(observation: PingObservation): ConnectionFacts = ConnectionFacts(
    reachability = if (observation == PingObservation.NetworkError) {
        Reachability.UNREACHABLE
    } else {
        Reachability.REACHABLE
    },
)
