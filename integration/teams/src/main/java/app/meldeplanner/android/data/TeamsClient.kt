package app.meldeplanner.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException

/**
 * Microsoft Teams assignments through Microsoft Graph, port of
 * meldo-ios/MeldePlanner/Data/TeamsClient.swift.
 *
 * Sign-in uses Microsoft's own page (a Custom Tab) with OAuth 2.0 + PKCE: Meldo
 * never sees the school password, only tokens. Meldo is a public client, so there
 * is no secret; the app (client) ID comes from the school's or the developer's
 * Azure app registration and is not confidential.
 */
object TeamsConfig {
    /**
     * Application (client) ID from the Azure app registration. Not in the repo: the app
     * passes it to [app.meldeplanner.android.ui.features.teams.TeamsFeature] from the
     * MELDO_TEAMS_CLIENT_ID Gradle property (~/.gradle/gradle.properties, a CI secret).
     * Empty when missing; Teams then shows how to set it up.
     */
    var clientID: String = ""
        internal set
    const val redirectURI = "meldeplanner://auth"
    const val callbackScheme = "meldeplanner"
    const val scopes = "offline_access User.Read EduAssignments.ReadBasic EduRoster.ReadBasic"
    val isConfigured: Boolean get() = clientID.isNotEmpty()
}

/** Why a Teams request failed; the Teams screen turns it into a message. */
sealed class TeamsFailure : Exception() {
    data object NotConfigured : TeamsFailure()
    data object Cancelled : TeamsFailure()

    /** Microsoft refused, e.g. the school's IT hasn't approved Meldo. */
    data class Denied(val detail: String) : TeamsFailure()
    data object Expired : TeamsFailure()
    data object Network : TeamsFailure()
}

class TeamsClient {
    data class Tokens(val access: String, val refresh: String)

    data class Assignment(
        val id: String,
        val title: String,
        val className: String,
        val due: Instant?,
        val instructions: String,
        val webURL: String?,
    )

    /** PKCE verifier and the sign-in URL to open in the browser. */
    data class Authorization(val verifier: String, val url: String, val state: String)

    suspend fun redeem(code: String, verifier: String): Tokens = token(
        mapOf(
            "grant_type" to "authorization_code", "code" to code,
            "redirect_uri" to TeamsConfig.redirectURI, "code_verifier" to verifier,
        ),
    )

    suspend fun refresh(refreshToken: String): Tokens =
        token(mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken))

    private suspend fun token(fields: Map<String, String>): Tokens {
        if (!TeamsConfig.isConfigured) throw TeamsFailure.NotConfigured
        val all = fields + mapOf("client_id" to TeamsConfig.clientID, "scope" to TeamsConfig.scopes)
        val form = all.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val response = send(
            URL("$AUTHORITY/token"), GraphToken.serializer(),
            method = "POST",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = form,
        )
        val access = response.access_token
        if (access == null) {
            if (response.error == "invalid_grant") throw TeamsFailure.Expired
            throw TeamsFailure.Denied(response.error_description ?: response.error ?: "")
        }
        return Tokens(access, response.refresh_token ?: fields["refresh_token"] ?: "")
    }

    /** The signed-in account's name, for the status screen. */
    suspend fun accountName(access: String): String {
        val me = graph("me", access, GraphMe.serializer())
        return me.displayName ?: me.userPrincipalName ?: ""
    }

    /** Assignments of all the student's classes, with class names. */
    suspend fun assignments(access: String): List<Assignment> {
        val classes = graph("education/me/classes", access, GraphClasses.serializer())
        val names = mutableMapOf<String, String>()
        for (c in classes.value) names.putIfAbsent(c.id, c.displayName ?: "")
        val list = graph("education/me/assignments?\$top=100", access, GraphAssignments.serializer())
        return list.value
            .filter { it.status != "draft" }
            .map { item ->
                Assignment(
                    id = item.id,
                    title = item.displayName ?: "",
                    className = item.classId?.let { names[it] } ?: "",
                    due = item.dueDateTime?.let(::parseDate),
                    instructions = plainText(item.instructions?.content ?: ""),
                    webURL = item.webUrl,
                )
            }
    }

    private suspend fun <T> graph(path: String, access: String, serializer: KSerializer<T>): T {
        val url = runCatching { URL("https://graph.microsoft.com/v1.0/$path") }.getOrElse { throw TeamsFailure.Network }
        return send(url, serializer, headers = mapOf("Authorization" to "Bearer $access"))
    }

    private suspend fun <T> send(
        url: URL,
        serializer: KSerializer<T>,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): T {
        val (status, data) = withContext(Dispatchers.IO) {
            try {
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.requestMethod = method
                    connection.useCaches = false
                    for ((name, value) in headers) connection.setRequestProperty(name, value)
                    body?.let { text ->
                        connection.doOutput = true
                        connection.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    val code = connection.responseCode
                    val stream = if (code >= 400) connection.errorStream else connection.inputStream
                    code to (stream?.use { it.readBytes() } ?: ByteArray(0))
                } finally {
                    connection.disconnect()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TeamsFailure.Network
            }
        }
        val prefix = String(data.copyOf(minOf(data.size, 200)), Charsets.UTF_8)
        if (status == 401) throw TeamsFailure.Expired
        if (status == 403) throw TeamsFailure.Denied(prefix)
        return try {
            json.decodeFromString(serializer, String(data, Charsets.UTF_8))
        } catch (e: Exception) {
            throw TeamsFailure.Denied(prefix)
        }
    }

    companion object {
        private const val AUTHORITY = "https://login.microsoftonline.com/common/oauth2/v2.0"
        private const val TIMEOUT_MS = 30_000
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        /** PKCE verifier, the `state` to expect back and the sign-in URL. */
        fun authorization(): Authorization {
            val random = SecureRandom()
            val verifier = base64URL(ByteArray(32).also { random.nextBytes(it) })
            val state = base64URL(ByteArray(32).also { random.nextBytes(it) })
            val challenge = base64URL(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)))
            val query = listOf(
                "client_id" to TeamsConfig.clientID,
                "response_type" to "code",
                "redirect_uri" to TeamsConfig.redirectURI,
                "scope" to TeamsConfig.scopes,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
                "prompt" to "select_account",
                "state" to state,
            ).joinToString("&") { "${encode(it.first)}=${encode(it.second)}" }
            return Authorization(verifier, "$AUTHORITY/authorize?$query", state)
        }

        /**
         * The `code` (or Microsoft's error) from the redirect back to Meldo. Another app can
         * open meldeplanner://auth too: a callback without this sign-in's `state` didn't come
         * from it and is dropped like a cancel.
         */
        fun code(callback: String, state: String): String {
            val items = queryItems(callback)
            if (items["state"] != state) throw TeamsFailure.Cancelled
            items["code"]?.let { return it }
            throw TeamsFailure.Denied(items["error_description"] ?: items["error"] ?: "")
        }

        private fun queryItems(url: String): Map<String, String> {
            val raw = runCatching { URI(url).rawQuery }.getOrNull() ?: return emptyMap()
            val items = mutableMapOf<String, String>()
            for (part in raw.split("&")) {
                if (part.isEmpty()) continue
                val name = URLDecoder.decode(part.substringBefore("="), "UTF-8")
                val value = URLDecoder.decode(part.substringAfter("=", ""), "UTF-8")
                items.putIfAbsent(name, value)
            }
            return items
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private fun base64URL(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

        internal fun parseDate(text: String): Instant? =
            runCatching { Instant.parse(text) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()

        /** Assignment instructions arrive as HTML. */
        fun plainText(html: String): String {
            var text = html.replace("<br>", "\n").replace("</p>", "\n")
            while (true) {
                val open = text.indexOf('<')
                if (open < 0) break
                val close = text.indexOf('>', open + 1)
                if (close < 0) break
                text = text.removeRange(open, close + 1)
            }
            return text.replace("&nbsp;", " ").replace("&amp;", "&").trim()
        }
    }
}

// Microsoft Graph answers.

@Suppress("PropertyName")
@Serializable
private data class GraphToken(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val error: String? = null,
    val error_description: String? = null,
)

@Serializable
private data class GraphMe(val displayName: String? = null, val userPrincipalName: String? = null)

@Serializable
private data class GraphClasses(val value: List<Class>) {
    @Serializable
    data class Class(val id: String, val displayName: String? = null)
}

@Serializable
private data class GraphAssignments(val value: List<Item>) {
    @Serializable
    data class Body(val content: String? = null)

    @Serializable
    data class Item(
        val id: String,
        val displayName: String? = null,
        val classId: String? = null,
        val dueDateTime: String? = null,
        val status: String? = null,
        val webUrl: String? = null,
        val instructions: Body? = null,
    )
}
