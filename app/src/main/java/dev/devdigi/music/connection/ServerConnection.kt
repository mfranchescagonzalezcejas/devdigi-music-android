package dev.devdigi.music.connection

import java.net.URI

class ServerEndpoint private constructor(val value: String) {
    override fun equals(other: Any?): Boolean = other is ServerEndpoint && value == other.value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        fun parse(
            input: String,
            endpointPolicy: EndpointPolicy = BuildVariantEndpointPolicy,
        ): EndpointParseResult {
            if (input.any(Char::isISOControl)) return EndpointParseResult.Invalid

            val value = input.trim()
            val uri = runCatching { URI(value) }.getOrNull() ?: return EndpointParseResult.Invalid
            val scheme = uri.scheme?.lowercase() ?: return EndpointParseResult.Invalid
            val rawHost = uri.host?.lowercase() ?: return EndpointParseResult.Invalid
            val host = rawHost.trimEnd('.').takeIf(String::isNotEmpty) ?: return EndpointParseResult.Invalid
            val isPermittedLocalHttp = isPermittedLocalHttp(scheme, host, rawHost != host, endpointPolicy)
            if (
                !uri.isAbsolute ||
                hasInvalidScheme(scheme, isPermittedLocalHttp) ||
                hasInvalidAuthority(uri) ||
                hasInvalidHost(host, isPermittedLocalHttp) ||
                hasInvalidPort(uri.port) ||
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

        private fun isPermittedLocalHttp(
            scheme: String,
            host: String,
            hasTerminalDot: Boolean,
            endpointPolicy: EndpointPolicy,
        ): Boolean = scheme == "http" && !hasTerminalDot && endpointPolicy.allowsHttp(host)

        private fun hasInvalidScheme(scheme: String, isPermittedLocalHttp: Boolean): Boolean =
            scheme != "https" && !isPermittedLocalHttp

        private fun hasInvalidAuthority(uri: URI): Boolean =
            uri.userInfo != null || uri.query != null || uri.fragment != null

        private fun hasInvalidHost(host: String, isPermittedLocalHttp: Boolean): Boolean =
            host.startsWith('[') ||
                host.all(Char::isDigit) ||
                host.endsWith(".local") ||
                (!isPermittedLocalHttp && (isLocalHost(host) || isProhibitedIpv4(host)))

        private fun isLocalHost(host: String): Boolean =
            host == "localhost" || host.endsWith(".localhost")

        private fun hasInvalidPort(port: Int): Boolean = port != -1 && port !in 1..65535

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

object HttpsOnlyEndpointPolicy : EndpointPolicy {
    override fun allowsHttp(host: String): Boolean = false
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

data class ServerAccountIdentity(val endpoint: ServerEndpoint, val username: String)

data class ServerMetadata(val serverType: String, val serverVersion: String, val openSubsonic: Boolean)

class AuthCredentials private constructor(val username: String, internal val password: String) {
    override fun toString(): String = "AuthCredentials(username=$username, password=***)"

    companion object {
        fun create(username: String, password: String): AuthCredentials = AuthCredentials(username, password)
    }
}

sealed interface AuthResult {
    data class Authenticated(val metadata: ServerMetadata) : AuthResult
    data object InvalidCredentials : AuthResult
    data object UnsupportedAuthentication : AuthResult
    data object AuthProtocolError : AuthResult
    data object IncompatibleServer : AuthResult
    data object NetworkError : AuthResult
}

fun interface AuthenticatedPingClient {
    suspend fun ping(credentials: AuthCredentials, profile: ServerProfile): AuthResult
}

fun reduceAuthResult(result: AuthResult): ConnectionFacts = when (result) {
    is AuthResult.Authenticated -> ConnectionFacts(
        reachability = Reachability.REACHABLE,
        compatibility = Compatibility.COMPATIBLE,
        authentication = Authentication.AUTHENTICATED,
    )
    AuthResult.InvalidCredentials -> ConnectionFacts(
        reachability = Reachability.REACHABLE,
        compatibility = Compatibility.NOT_CHECKED,
        authentication = Authentication.REJECTED,
    )
    AuthResult.UnsupportedAuthentication -> ConnectionFacts(
        reachability = Reachability.REACHABLE,
        compatibility = Compatibility.NOT_CHECKED,
        authentication = Authentication.NOT_CHECKED,
    )
    AuthResult.AuthProtocolError -> ConnectionFacts(
        reachability = Reachability.REACHABLE,
        compatibility = Compatibility.NOT_CHECKED,
        authentication = Authentication.NOT_CHECKED,
    )
    AuthResult.IncompatibleServer -> ConnectionFacts(
        reachability = Reachability.REACHABLE,
        compatibility = Compatibility.INCOMPATIBLE,
        authentication = Authentication.NOT_CHECKED,
    )
    AuthResult.NetworkError -> ConnectionFacts(
        reachability = Reachability.UNREACHABLE,
        compatibility = Compatibility.NOT_CHECKED,
        authentication = Authentication.NOT_CHECKED,
    )
}

object SubsonicResponseParser {
    fun parse(json: String): AuthResult = try {
        val root = org.json.JSONObject(json).optJSONObject("subsonic-response")
            ?: return AuthResult.AuthProtocolError

        when (root.optString("status", "")) {
            "ok" -> {
                val metadata = ServerMetadata(
                    serverType = root.optString("type", ""),
                    serverVersion = root.optString("serverVersion", ""),
                    openSubsonic = root.optBoolean("openSubsonic", false),
                )
                AuthResult.Authenticated(metadata)
            }
            "failed" -> {
                val code = root.optJSONObject("error")?.optInt("code", -1) ?: -1
                when (code) {
                    40 -> AuthResult.InvalidCredentials
                    41, 42 -> AuthResult.UnsupportedAuthentication
                    43 -> AuthResult.AuthProtocolError
                    20, 30 -> AuthResult.IncompatibleServer
                    else -> AuthResult.AuthProtocolError
                }
            }
            else -> AuthResult.AuthProtocolError
        }
    } catch (_: org.json.JSONException) {
        AuthResult.AuthProtocolError
    }
}
