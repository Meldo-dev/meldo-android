package app.meldeplanner.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.melde_ffi.FfiDate
import uniffi.melde_ffi.FfiTimeUnit
import uniffi.melde_ffi.FfiUntisAbsence
import uniffi.melde_ffi.FfiUntisAuth
import uniffi.melde_ffi.FfiUntisExam
import uniffi.melde_ffi.FfiUntisHoliday
import uniffi.melde_ffi.FfiUntisHomework
import uniffi.melde_ffi.FfiUntisNews
import uniffi.melde_ffi.FfiUntisSchool
import uniffi.melde_ffi.MeldeException
import uniffi.melde_ffi.untisAuthenticateRequest
import uniffi.melde_ffi.untisHolidaysRequest
import uniffi.melde_ffi.untisIsValidHost
import uniffi.melde_ffi.untisLogoutRequest
import uniffi.melde_ffi.untisParseAbsences
import uniffi.melde_ffi.untisParseAppConfig
import uniffi.melde_ffi.untisParseAppData
import uniffi.melde_ffi.untisParseAuth
import uniffi.melde_ffi.untisParseExams
import uniffi.melde_ffi.untisParseTimetableExams
import uniffi.melde_ffi.untisTimetableEntriesQuery
import uniffi.melde_ffi.untisParseHolidays
import uniffi.melde_ffi.untisParseHomework
import uniffi.melde_ffi.untisParseNews
import uniffi.melde_ffi.untisParseSchoolSearch
import uniffi.melde_ffi.untisParseTimegrid
import uniffi.melde_ffi.untisRangeQuery
import uniffi.melde_ffi.untisSchoolSearchRequest
import uniffi.melde_ffi.untisTimegridRequest
import uniffi.melde_ffi.untisTimetableRequest
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/** Why a WebUntis request failed; the UI turns it into a message (UntisMessages). */
sealed class UntisFailure : Exception() {
    data object WrongCredentials : UntisFailure()

    /** The school search matched too many schools; WebUntis then returns none. */
    data object TooManyResults : UntisFailure()

    data class NoAccess(val detail: String) : UntisFailure()

    data class Server(val code: Long, val detail: String) : UntisFailure()

    data object Network : UntisFailure()

    /** A browser (SSO) session that has expired or was never logged in. */
    data object SessionExpired : UntisFailure()

    /** No lessons in the next eight weeks (holidays or an empty timetable). */
    data object NoLessons : UntisFailure()
}

/**
 * Talks to WebUntis, port of meldo-ios/MeldePlanner/Data/UntisClient.swift.
 * Only two hosts are ever contacted: Untis' school search and the student's own
 * school server. Request bodies and response parsing come from the Rust core
 * (melde-untis). Network calls run on [Dispatchers.IO].
 *
 * @param browserCookies `Cookie` header of a session that was logged in in the
 * browser (school SSO).
 */
class UntisClient(private val browserCookies: String? = null) {
    /** Own cookie store per client, so the Untis session never mixes with anything else. */
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)

    /** [reachable] is false when no request got an answer (offline), so it isn't an expired session. */
    data class Probe(val auth: FfiUntisAuth?, val detail: String, val reachable: Boolean = true)

    suspend fun searchSchools(query: String): List<FfiUntisSchool> {
        val body = post(SEARCH_URL, untisSchoolSearchRequest(query))
        return try {
            map { untisParseSchoolSearch(body) }
        } catch (e: UntisFailure.Server) {
            if (e.code == -6003L) throw UntisFailure.TooManyResults
            throw e
        }
    }

    suspend fun login(server: String, loginName: String, user: String, password: String): FfiUntisAuth {
        val body = post(endpoint(server, loginName), untisAuthenticateRequest(user, password))
        return map { untisParseAuth(body) }
    }

    /**
     * Who is logged in, for a session that started in the browser (Microsoft 365 or
     * another school login) instead of `authenticate`.
     */
    suspend fun browserAuth(server: String, sessionID: String): FfiUntisAuth {
        val probe = probeBrowserLogin(server, sessionID)
        return probe.auth ?: throw if (probe.reachable) UntisFailure.SessionExpired else UntisFailure.Network
    }

    /**
     * Asks `/api/app/config`, then the newer `/api/rest/view/v1/app/data`.
     * `detail` says what WebUntis answered, for the login screen when it fails.
     */
    suspend fun probeBrowserLogin(server: String, sessionID: String): Probe {
        val notes = mutableListOf<String>()
        var answered = false
        val host = runCatching { checkedHost(server) }.getOrNull()
        fun url(path: String): URL? = host?.let { runCatching { URL(URI("https", it, path, null).toASCIIString()) }.getOrNull() }
        // The newer REST API wants the session's JWT as well.
        var token: String? = null
        url("/WebUntis/api/token/new")?.let { tokenURL ->
            val response = try { sendWithStatus(Request(tokenURL)) } catch (e: UntisFailure) { null }
            if (response != null && response.status == 200 && response.body.isNotEmpty() && !response.body.startsWith("<")) {
                token = response.body.trim()
            }
        }
        for (path in listOf("/WebUntis/api/app/config", "/WebUntis/api/rest/view/v1/app/data")) {
            val url = url(path) ?: continue
            val headers = mutableMapOf("Accept" to "application/json")
            val jwt = token
            if (jwt != null && path.contains("/rest/")) headers["Authorization"] = "Bearer $jwt"
            val response = try { sendWithStatus(Request(url, headers = headers)) } catch (e: UntisFailure) { null }
            if (response == null) {
                notes.add("$path: keine Verbindung")
                continue
            }
            answered = true
            val auth = runCatching {
                if (path.endsWith("config")) untisParseAppConfig(response.body, sessionID)
                else untisParseAppData(response.body, sessionID)
            }.getOrNull()
            if (auth != null) return Probe(auth, "")
            val start = response.body.take(80).replace("\n", " ")
            notes.add("$path: HTTP ${response.status} $start")
        }
        return Probe(null, (listOf(server) + notes).joinToString("\n"), reachable = answered)
    }

    /** GET on the WebUntis web API with this client's session. */
    private suspend fun webGet(server: String, path: String, query: String): String {
        val host = checkedHost(server)
        val url = runCatching { URL(URI("https", host, path, null).toASCIIString() + "?" + query) }
            .getOrElse { throw UntisFailure.Network }
        return send(Request(url, headers = mapOf("Accept" to "application/json")))
    }

    suspend fun absences(server: String, auth: FfiUntisAuth, first: FfiDate, last: FfiDate): List<FfiUntisAbsence> {
        val range = map { untisRangeQuery(first, last) }
        val body = webGet(
            server, "/WebUntis/api/classreg/absences/students",
            range + "&studentId=${auth.personId}&excuseStatusId=-1",
        )
        return map { untisParseAbsences(body) }
    }

    suspend fun exams(server: String, auth: FfiUntisAuth, first: FfiDate, last: FfiDate): List<FfiUntisExam> {
        val range = map { untisRangeQuery(first, last) }
        val body = webGet(
            server, "/WebUntis/api/exams",
            range + "&studentId=${auth.personId}&klasseId=${auth.klasseId ?: -1}&withGrades=false",
        )
        return map { untisParseExams(body) }
    }

    /**
     * Exams marked in the REST timetable, which the web app and the Untis apps use.
     * For schools that refuse `/api/exams` to students (HTTP 403).
     */
    suspend fun timetableExams(server: String, auth: FfiUntisAuth, first: FfiDate, last: FfiDate): List<FfiUntisExam> {
        val host = checkedHost(server)
        fun url(pathAndQuery: String) = runCatching { URL(URL("https://$host/"), pathAndQuery) }
            .getOrElse { throw UntisFailure.Network }
            .also { if (it.host.lowercase() != host) throw UntisFailure.Network }
        val token = sendWithStatus(Request(url("/WebUntis/api/token/new"))).body.trim()
        if (token.isEmpty() || token.startsWith("<")) throw UntisFailure.SessionExpired
        val query = map { untisTimetableEntriesQuery(auth.personId, first, last) }
        val body = send(Request(url(query), headers = mapOf("Accept" to "application/json", "Authorization" to "Bearer $token")))
        return map { untisParseTimetableExams(body) }
    }

    suspend fun homework(server: String, first: FfiDate, last: FfiDate): List<FfiUntisHomework> {
        val range = map { untisRangeQuery(first, last) }
        val body = webGet(server, "/WebUntis/api/homeworks/lessons", range)
        return map { untisParseHomework(body) }
    }

    suspend fun news(server: String, date: FfiDate): List<FfiUntisNews> {
        val day = "%04d%02d%02d".format(date.year, date.month.toInt(), date.day.toInt())
        val body = webGet(server, "/WebUntis/api/public/news/newsWidgetData", "date=$day")
        return map { untisParseNews(body) }
    }

    /** The school's bell schedule (periods; breaks are the gaps). */
    suspend fun timegrid(server: String, loginName: String): List<FfiTimeUnit> {
        val body = post(endpoint(server, loginName), untisTimegridRequest())
        return map { untisParseTimegrid(body) }
    }

    /** The school year's holidays (Herbstferien, Weihnachtsferien, …). */
    suspend fun holidays(server: String, loginName: String): List<FfiUntisHoliday> {
        val body = post(endpoint(server, loginName), untisHolidaysRequest())
        return map { untisParseHolidays(body) }
    }

    /** Raw `getTimetable` JSON for `first...last`, parsed later by Rust. */
    suspend fun timetable(server: String, loginName: String, auth: FfiUntisAuth, first: FfiDate, last: FfiDate): String {
        val request = map { untisTimetableRequest(auth.personId, auth.personType, first, last) }
        return post(endpoint(server, loginName), request)
    }

    suspend fun logout(server: String, loginName: String) {
        try {
            post(endpoint(server, loginName), untisLogoutRequest())
        } catch (e: UntisFailure) {
            // Logging out is a courtesy; the session also ends on its own.
        }
    }

    /** The school server as a plain lowercase host name, or a network failure (never another host). */
    private fun checkedHost(server: String): String =
        server.lowercase().takeIf { untisIsValidHost(it) } ?: throw UntisFailure.Network

    /**
     * The school's JSON-RPC endpoint. A server that isn't a plain host name is an error,
     * never a fallback to another host: the password would go wherever the URL points.
     */
    private fun endpoint(server: String, loginName: String): URL {
        val host = checkedHost(server)
        return runCatching {
            URL(URI("https", host, "/WebUntis/jsonrpc.do", null).toASCIIString() + "?school=" + queryValue(loginName))
        }.getOrElse { throw UntisFailure.Network }
    }

    private suspend fun post(url: URL, body: String): String = send(
        Request(url, method = "POST", headers = mapOf("Content-Type" to "application/json"), body = body),
    )

    private suspend fun send(request: Request): String = sendWithStatus(request).body

    private class Request(
        val url: URL,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
    )

    private class Response(val status: Int, val body: String)

    /**
     * Sends over HTTPS to a plain host name only and follows redirects by hand: the
     * student's session cookie and `Authorization` header stay on the host they were
     * meant for, and a redirect away from HTTPS is refused.
     */
    private suspend fun sendWithStatus(request: Request): Response = withContext(Dispatchers.IO) {
        try {
            val origin = request.url.host.lowercase()
            val originPort = request.url.port.takeIf { it != -1 } ?: request.url.defaultPort
            var url = request.url
            var method = request.method
            var body = request.body
            repeat(MAX_REDIRECTS + 1) {
                val host = url.host.lowercase()
                if (url.protocol != "https" || !untisIsValidHost(host)) throw UntisFailure.Network
                // Same origin means the same host and port; another port is another server.
                val sameHost = host == origin && (url.port.takeIf { it != -1 } ?: url.defaultPort) == originPort
                val uri = url.toURI()
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.requestMethod = method
                    connection.useCaches = false
                    for ((name, value) in request.headers) {
                        if (sameHost || !name.equals("Authorization", ignoreCase = true)) connection.setRequestProperty(name, value)
                    }
                    val cookieHeader = (if (sameHost) browserCookies else null)
                        ?: cookies.get(uri, emptyMap())["Cookie"]?.joinToString("; ")
                    if (!cookieHeader.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookieHeader)
                    body?.let { text ->
                        connection.doOutput = true
                        connection.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    val status = connection.responseCode
                    cookies.put(uri, connection.headerFields.filterKeys { it != null })
                    val location = connection.getHeaderField("Location")
                    if (status in 300..399 && location != null) {
                        url = URL(url, location)
                        // Another port is another server, and the cookie store would still send it
                        // the school's cookies (cookies ignore ports): never go there.
                        if ((url.port.takeIf { it != -1 } ?: url.defaultPort) != originPort) throw UntisFailure.Network
                        val leavesOrigin = url.host.lowercase() != origin ||
                            (url.port.takeIf { it != -1 } ?: url.defaultPort) != originPort
                        // Like browsers: 303, and 301/302 after a POST, continue as a plain GET.
                        // A 307/308 to another server would resend the body (the password): stop there.
                        if (body != null && leavesOrigin && status in 307..308) throw UntisFailure.Network
                        if (status == 303 || (status in 301..302 && method == "POST")) {
                            method = "GET"
                            body = null
                        }
                        return@repeat
                    }
                    val stream = if (status >= 400) connection.errorStream else connection.inputStream
                    // Cap the body: a reached server (malicious, compromised or a MITM of the HTTPS
                    // host) can't exhaust memory with a huge response. A normal response is tens of KB.
                    val text = stream?.use { readCapped(it, MAX_RESPONSE_BYTES) } ?: ""
                    return@withContext Response(status, text)
                } finally {
                    connection.disconnect()
                }
            }
            throw UntisFailure.Network
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UntisFailure.Network
        }
    }

    private inline fun <T> map(work: () -> T): T = try {
        work()
    } catch (e: MeldeException.UntisServer) {
        throw if (e.code == -8504L) UntisFailure.WrongCredentials else UntisFailure.Server(e.code, e.detail)
    } catch (e: MeldeException.UntisMalformed) {
        throw UntisFailure.NoAccess(e.detail)
    } catch (e: UntisFailure) {
        throw e
    } catch (e: Exception) {
        throw UntisFailure.NoAccess(e.toString())
    }

    companion object {
        private val SEARCH_URL = URL("https://mobile.webuntis.com/ms/schoolquery2")
        private const val MAX_REDIRECTS = 5
        private const val TIMEOUT_MS = 20_000

        /** A WebUntis response never legitimately runs to megabytes; above a real timetable,
         *  below a harmful allocation. */
        private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

        /** Reads at most [limit] bytes, then fails: a reached server can't exhaust memory. */
        private fun readCapped(input: java.io.InputStream, limit: Int): String {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > limit) throw UntisFailure.Network
                out.write(buffer, 0, n)
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }

        /** Percent-encodes a query value like `URLQueryItem` (spaces as %20). */
        internal fun queryValue(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
