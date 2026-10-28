package app.meldeplanner.android.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
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
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Google sign-in for Drive sync. Unlike iOS, Android uses Google's on-device
 * authorization (Google Play services): Google checks the package name and signing key
 * against the "Android" OAuth client, and new Android clients can't use a browser
 * redirect any more. Meldo never sees the Google password, only short-lived access tokens.
 */
object GoogleConfig {
    /**
     * OAuth client ID of an "Android" client from Google Cloud Console
     * ("…apps.googleusercontent.com"). Not in the repo: the app passes it to
     * [app.meldeplanner.android.ui.features.drive.DriveFeature] from the
     * MELDO_GOOGLE_CLIENT_ID Gradle property (~/.gradle/gradle.properties, a CI secret).
     * Empty when missing; Google Drive then shows "noch nicht verfügbar".
     */
    var clientID: String = ""
        internal set

    /**
     * Only Meldo's own hidden folder in Drive (no access to the student's other files),
     * plus the account's email address to show which Google account is connected.
     */
    const val scope = "openid email https://www.googleapis.com/auth/drive.appdata"
    val isConfigured: Boolean get() = clientID.isNotEmpty()
}

/** Google's on-device authorization for Meldo's Drive folder and the account's email. */
object PlayAuthorization {
    /**
     * An access token right away when already granted; otherwise a `pendingIntent` for Google's
     * consent screen. [account]: the connected account's email, so Google answers for that
     * account and not for another one on the device.
     */
    suspend fun authorize(context: Context, account: String? = null): AuthorizationResult = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata"), Scope("email"), Scope("openid")))
            .apply { if (account != null) setAccount(Account(account, "com.google")) }
            .build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(GoogleFailure.Network) }
    }

    /** The result after Google's consent screen. */
    fun result(context: Context, data: Intent?): AuthorizationResult? =
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data) }.getOrNull()
}

/** Why a Google request failed; the Google Drive screen turns it into a message. */
sealed class GoogleFailure : Exception() {
    data object NotConfigured : GoogleFailure()
    data object Cancelled : GoogleFailure()
    data class Denied(val detail: String) : GoogleFailure()

    /** Google doesn't accept the stored sign-in any more (revoked or expired). */
    data object Expired : GoogleFailure()
    data object Network : GoogleFailure()
}

class GoogleClient {
    data class Tokens(val access: String, val refresh: String, val expires: Instant)

    /** The signed-in account's email address, shown on the Google Drive screen. */
    suspend fun accountEmail(access: String): String? = runCatching {
        val (status, data) = GoogleHttp.send(
            "https://openidconnect.googleapis.com/v1/userinfo", headers = mapOf("Authorization" to "Bearer $access"),
        )
        if (status != 200) null else json.decodeFromString(GoogleUserInfo.serializer(), String(data, Charsets.UTF_8)).email
    }.getOrNull()

    /** Ends the sign-in at Google too, so a copied token stops working. Best effort. */
    suspend fun revoke(token: String) {
        runCatching {
            GoogleHttp.send(
                "https://oauth2.googleapis.com/revoke", method = "POST",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = "token=${encode(token)}".toByteArray(Charsets.UTF_8),
            )
        }
    }

    companion object {
        // encodeDefaults: the file metadata must carry `parents: ["appDataFolder"]`.
        internal val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

        /** Form and query encoding; "+" in a token is sent as %2B, spaces as %20. */
        internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    }
}

/** The app-data folder of Google Drive through the Drive v3 REST API. */
class GoogleDriveFiles(private val access: String) : DriveFiles {
    override suspend fun list(): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var page: String? = null
        var pages = 0
        do {
            // Meldo writes a few hundred files at most: stop instead of paging forever.
            pages += 1
            if (pages > 20) throw DriveProblem.TooLarge
            val query = listOf(
                "spaces" to "appDataFolder",
                "fields" to "nextPageToken,files(id,name,createdTime)",
                "pageSize" to "1000",
            ) + (page?.let { listOf("pageToken" to it) } ?: emptyList())
            val url = "$API?" + query.joinToString("&") { "${GoogleClient.encode(it.first)}=${GoogleClient.encode(it.second)}" }
            val data = authorized(url)
            val answer = runCatching { GoogleClient.json.decodeFromString(FileList.serializer(), String(data, Charsets.UTF_8)) }
                .getOrElse { throw GoogleFailure.Network }
            files += answer.files.mapNotNull { file ->
                if (!isID(file.id)) return@mapNotNull null
                DriveFile(file.id, file.name, file.createdTime?.let(::parseDate) ?: Instant.MIN)
            }
            page = answer.nextPageToken
        } while (page != null)
        return files
    }

    override suspend fun read(id: String, limit: Int): ByteArray {
        if (!isID(id)) throw GoogleFailure.Network
        val (status, data) = GoogleHttp.send("$API/$id?alt=media", headers = mapOf("Authorization" to "Bearer $access"), limit = limit)
        if (status == 401) throw GoogleFailure.Expired
        if (status !in 200..299) throw GoogleFailure.Network
        return data
    }

    override suspend fun create(name: String, data: ByteArray): DriveFile {
        val boundary = "meldo-${UUID.randomUUID()}"
        val metadata = GoogleClient.json.encodeToString(FileMetadata.serializer(), FileMetadata(name))
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
            write(metadata.toByteArray(Charsets.UTF_8))
            write("\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray(Charsets.UTF_8))
            write(data)
            write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
        }.toByteArray()
        val answer = authorized(
            "$UPLOAD?uploadType=multipart&fields=id,name,createdTime", method = "POST",
            contentType = "multipart/related; boundary=$boundary", body = body,
        )
        val file = runCatching { GoogleClient.json.decodeFromString(FileList.File.serializer(), String(answer, Charsets.UTF_8)) }.getOrNull()
        if (file == null || !isID(file.id)) throw GoogleFailure.Network
        return DriveFile(file.id, file.name, Instant.now())
    }

    override suspend fun update(id: String, data: ByteArray) {
        if (!isID(id)) throw GoogleFailure.Network
        authorized("$UPLOAD/$id?uploadType=media", method = "PATCH", contentType = "application/octet-stream", body = data)
    }

    override suspend fun delete(id: String) {
        if (!isID(id)) throw GoogleFailure.Network
        authorized("$API/$id", method = "DELETE")
    }

    private suspend fun authorized(url: String, method: String = "GET", contentType: String? = null, body: ByteArray? = null): ByteArray {
        val headers = buildMap {
            put("Authorization", "Bearer $access")
            contentType?.let { put("Content-Type", it) }
        }
        val (status, data) = GoogleHttp.send(url, method, headers, body)
        if (status == 401) throw GoogleFailure.Expired
        if (status == 403) throw GoogleFailure.Denied(String(data.copyOf(minOf(data.size, 200)), Charsets.UTF_8))
        if (status !in 200..299) throw GoogleFailure.Network
        return data
    }

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

        /** Drive IDs are letters, digits, "-" and "_": an ID from Google's answer can't change the request path. */
        fun isID(id: String): Boolean =
            id.isNotEmpty() && id.length <= 200 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

        internal fun parseDate(text: String): Instant? =
            runCatching { Instant.parse(text) }.getOrNull() ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
    }
}

/**
 * HTTPS to Google without caches or cookies. Redirects are handled here: same host is
 * followed as is; a download Google moves to its content host (*.googleusercontent.com)
 * is followed without the token; anything else is refused (iOS `GoogleRedirectGuard`).
 */
object GoogleHttp {
    private const val TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 5

    suspend fun send(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        limit: Int = 4 * 1024 * 1024,
    ): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
        try {
            var current = URL(url)
            var currentHeaders = headers
            repeat(MAX_REDIRECTS + 1) {
                val connection = current.openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.useCaches = false
                    connection.requestMethod = method
                    for ((name, value) in currentHeaders) connection.setRequestProperty(name, value)
                    body?.let { bytes ->
                        connection.doOutput = true
                        connection.setFixedLengthStreamingMode(bytes.size)
                        connection.outputStream.use { it.write(bytes) }
                    }
                    val code = connection.responseCode
                    if (code in 300..399) {
                        val location = connection.getHeaderField("Location") ?: throw GoogleFailure.Network
                        val next = URL(current, location)
                        currentHeaders = follow(current, next, method, currentHeaders) ?: throw GoogleFailure.Network
                        current = next
                        return@repeat
                    }
                    val length = connection.contentLengthLong
                    // Never more than `limit` bytes in memory, whatever the file's size.
                    if (code in 200..299 && length > limit) throw DriveProblem.TooLarge
                    val stream = if (code >= 400) connection.errorStream else connection.inputStream
                    // Known size: read straight into one array of that size (a growing buffer
                    // plus its final copy would need about three times the file in memory).
                    if (code in 200..299 && length in 0..limit.toLong() && stream != null) {
                        val data = stream.use { input ->
                            val bytes = ByteArray(length.toInt())
                            var read = 0
                            while (read < bytes.size) {
                                val n = input.read(bytes, read, bytes.size - read)
                                if (n < 0) throw GoogleFailure.Network
                                read += n
                            }
                            if (input.read() >= 0) throw DriveProblem.TooLarge
                            bytes
                        }
                        return@withContext code to data
                    }
                    val data = stream?.use { input ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (out.size() + n > limit) {
                                if (code in 200..299) throw DriveProblem.TooLarge
                                out.write(buffer, 0, limit - out.size())
                                break
                            }
                            out.write(buffer, 0, n)
                        }
                        out.toByteArray()
                    } ?: ByteArray(0)
                    return@withContext code to data
                } finally {
                    connection.disconnect()
                }
            }
            throw GoogleFailure.Network
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleFailure) {
            throw e
        } catch (e: DriveProblem) {
            throw e
        } catch (e: Exception) {
            throw GoogleFailure.Network
        }
    }

    /** The headers for following a redirect from [from] to [to], or null to refuse it. */
    fun follow(from: URL, to: URL, method: String, headers: Map<String, String>): Map<String, String>? {
        if (to.protocol != "https") return null
        val host = to.host.lowercase()
        val port = if (to.port == -1) 443 else to.port
        val fromPort = if (from.port == -1) 443 else from.port
        if (from.host.lowercase() == host && fromPort == port) return headers
        if (method != "GET" || !host.endsWith(".googleusercontent.com") || to.port != -1) return null
        return headers.filterKeys { !it.equals("Authorization", true) && !it.equals("Cookie", true) }
    }
}

@Serializable
private data class FileMetadata(val name: String, val parents: List<String> = listOf("appDataFolder"))

@Serializable
private data class GoogleUserInfo(val email: String? = null)

@Serializable
private data class FileList(val files: List<File> = emptyList(), val nextPageToken: String? = null) {
    @Serializable
    data class File(val id: String, val name: String, val createdTime: String? = null)
}
