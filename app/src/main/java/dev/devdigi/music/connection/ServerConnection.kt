package dev.devdigi.music.connection

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
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
    /**
     * Defensive input bound for an authenticated-ping response (64 KiB, power of two).
     * A normal OpenSubsonic ping response is well under 1 KiB; 64 KiB comfortably
     * accommodates legitimate server metadata while bounding the heap/stack cost of
     * materializing the JsonElement tree. WU3 will additionally enforce a byte bound
     * at the network boundary before a String is created (bytes != characters for
     * arbitrary UTF-8, so the two limits are separate defense layers).
     */
    internal const val MAX_AUTH_RESPONSE_CHARS = 65_536

    /**
     * Independent structural guard: pathological object/array nesting must never
     * reach [parseToJsonElement] (kotlinx-serialization materializes the tree
     * recursively and deep nesting caused an unhandled StackOverflowError in a
     * controlled probe, even below the 64 KiB size bound). OpenSubsonic auth
     * envelopes are extremely shallow; 128 is generous compatibility headroom and
     * deliberately not derived from any JVM crash threshold.
     */
    internal const val MAX_AUTH_RESPONSE_DEPTH = 128

    private val strictJson = Json { isLenient = false }

    fun parse(json: String): AuthResult {
        if (json.length > MAX_AUTH_RESPONSE_CHARS) return AuthResult.AuthProtocolError
        if (exceedsJsonNestingDepth(json, MAX_AUTH_RESPONSE_DEPTH)) return AuthResult.AuthProtocolError
        if (hasDuplicateJsonObjectKeys(json)) return AuthResult.AuthProtocolError
        val root = try {
            val element = strictJson.parseToJsonElement(json)
            (element as? JsonObject)?.get("subsonic-response") as? JsonObject
                ?: return AuthResult.AuthProtocolError
        } catch (_: SerializationException) {
            return AuthResult.AuthProtocolError
        }

        // Common envelope: status and version must be actual non-blank JSON strings.
        val status = root.stringField("status")
        if (status.isNullOrBlank()) return AuthResult.AuthProtocolError
        val version = root.stringField("version")
        if (version.isNullOrBlank()) return AuthResult.AuthProtocolError

        return when (status) {
            "ok" -> {
                if (root.containsKey("error")) return AuthResult.AuthProtocolError
                // Distinguish field PRESENCE from Boolean value: absent/false -> IncompatibleServer,
                // present-but-wrong-typed (string/number/object/array/null) -> AuthProtocolError.
                if (!root.containsKey("openSubsonic")) return AuthResult.IncompatibleServer
                val openSubsonic = root.booleanField("openSubsonic") ?: return AuthResult.AuthProtocolError
                if (!openSubsonic) return AuthResult.IncompatibleServer
                val serverType = root.stringField("type")
                val serverVersion = root.stringField("serverVersion")
                when {
                    serverType.isNullOrBlank() -> AuthResult.AuthProtocolError
                    serverVersion.isNullOrBlank() -> AuthResult.AuthProtocolError
                    else -> AuthResult.Authenticated(ServerMetadata(serverType, serverVersion, true))
                }
            }
            "failed" -> {
                // Present-but-malformed OpenSubsonic metadata fails closed. Absence stays
                // compatible with legacy failed responses; wrong types/blank strings do not.
                if (!root.hasValidOptionalFailedMetadata()) return AuthResult.AuthProtocolError
                val error = root["error"] as? JsonObject ?: return AuthResult.AuthProtocolError
                // error.message is OPTIONAL, but when present must be an actual JSON String.
                if (error.containsKey("message") && error.stringField("message") == null) {
                    return AuthResult.AuthProtocolError
                }
                val code = error.intField("code")
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
    }

    private fun JsonObject.stringField(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.booleanField(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.intField(key: String): Int? =
        (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.hasValidOptionalFailedMetadata(): Boolean {
        if (containsKey("openSubsonic") && booleanField("openSubsonic") == null) return false
        if (containsKey("type") && stringField("type").isNullOrBlank()) return false
        if (containsKey("serverVersion") && stringField("serverVersion").isNullOrBlank()) return false
        return true
    }

    /**
     * Structural pre-scan: O(n) time, O(1) auxiliary memory. Counts `{`/`[` nesting,
     * decrements on `}`/`]`, ignores structural characters inside JSON strings, and
     * tracks escaped quotes so a string does not end early. It is NOT a JSON parser:
     * grammar/type/escape validation remains with [strictJson].
     */
    private fun exceedsJsonNestingDepth(json: String, maxDepth: Int): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in json) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    if (depth > maxDepth) return true
                }
                '}', ']' -> if (depth > 0) depth--
            }
        }
        return false
    }

    /**
     * Security lexical guard: rejects duplicate JSON object member names within the
     * SAME object BEFORE [parseToJsonElement] collapses them. Scans once, tracks
     * per-object seen-key sets on a small frame stack (object frame -> MutableSet,
     * array frame -> null); string/escape handling mirrors the depth scanner; a
     * quoted token followed (ignoring whitespace) by `:` inside an object scope is
     * a member name. Keys are decoded through [strictJson] so escape-equivalent
     * spellings (e.g. `status` vs `sta\u0074us`) collide. It is NOT a JSON parser:
     * grammar/type validation remains with [strictJson]. On any un-decodable key it
     * fails closed (treated as a duplicate).
     */
    private fun hasDuplicateJsonObjectKeys(json: String): Boolean {
        val frames = ArrayDeque<MutableSet<String>?>()
        var i = 0
        val n = json.length
        var inString = false
        var escaped = false
        while (i < n) {
            val c = json[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when (c) {
                '"' -> {
                    val start = i
                    i++
                    var esc = false
                    while (i < n) {
                        val sc = json[i]
                        when {
                            esc -> esc = false
                            sc == '\\' -> esc = true
                            sc == '"' -> {
                                i++
                                break
                            }
                            else -> {}
                        }
                        i++
                    }
                    // Peek after the quoted token: member name iff followed by ':'.
                    var j = i
                    while (j < n && json[j].isWhitespace()) j++
                    if (j < n && json[j] == ':' && frames.isNotEmpty() && frames.last() != null) {
                        val rawToken = json.substring(start, i)
                        val decoded = decodeKeyToken(rawToken)
                        if (decoded == null || !frames.last()!!.add(decoded)) return true
                        i = j + 1
                        continue
                    }
                    // Not a key: value string already consumed; resume at the next char.
                    i = j
                }
                '{' -> {
                    frames.addLast(mutableSetOf())
                    i++
                }
                '[' -> {
                    frames.addLast(null)
                    i++
                }
                '}' -> {
                    if (frames.isNotEmpty()) frames.removeLast()
                    i++
                }
                ']' -> {
                    if (frames.isNotEmpty()) frames.removeLast()
                    i++
                }
                else -> i++
            }
        }
        return false
    }

    private fun decodeKeyToken(rawQuotedToken: String): String? =
        try {
            (strictJson.parseToJsonElement(rawQuotedToken) as? JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (_: SerializationException) {
            null
        }
}
