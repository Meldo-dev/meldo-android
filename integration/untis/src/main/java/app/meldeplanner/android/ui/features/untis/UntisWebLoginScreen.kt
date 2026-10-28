package app.meldeplanner.android.ui.features.untis

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.view.ViewGroup
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.meldeplanner.android.core.L
import app.meldeplanner.android.data.UntisClient
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.sfSymbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uniffi.melde_ffi.FfiUntisSchool

/**
 * The school's own WebUntis login page inside Meldo, for schools that sign in through
 * Microsoft 365, IServ or another login (SSO). The student logs in on those pages;
 * Meldo only reads the resulting WebUntis session cookies of that server and asks
 * WebUntis who is logged in. Passwords typed here never reach Meldo.
 * Port of meldo-ios/MeldePlanner/Features/Untis/UntisWebLoginView.swift.
 *
 * [onLoggedIn] is called once WebUntis confirms the login: the WebUntis host the
 * session belongs to, the `Cookie` header and the session id.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UntisWebLoginScreen(
    school: FfiUntisSchool,
    onDismiss: () -> Unit,
    onLoggedIn: (server: String, cookies: String, sessionID: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnLoggedIn by rememberUpdatedState(onLoggedIn)
    val watcher = remember { UntisLoginWatcher(school) { s, c, id -> currentOnLoggedIn(s, c, id) } }

    DisposableEffect(watcher) {
        onDispose { watcher.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(school.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, L("Abbrechen")) }
                },
                actions = {
                    TextButton(
                        onClick = { scope.launch { watcher.check(manual = true) } },
                        enabled = !watcher.checking,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(L("Ich bin angemeldet"), fontWeight = FontWeight.SemiBold) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            // Status of the login check on a rounded tonal card above the school's page.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = DS.rowShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = DS.spacingM, vertical = DS.spacingS),
            ) {
                Column(
                    Modifier.padding(horizontal = DS.spacingL, vertical = DS.spacingM),
                    verticalArrangement = Arrangement.spacedBy(DS.spacingXS),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(DS.spacingS), verticalAlignment = Alignment.CenterVertically) {
                        if (watcher.checking) {
                            LoadingIndicator(Modifier.size(24.dp))
                        } else {
                            Icon(sfSymbol("person.badge.key"), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(watcher.status, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    watcher.detail?.let { detail ->
                        SelectionContainer {
                            Text(
                                detail,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context -> watcher.makeWebView(context, scope) },
                onRelease = { it.destroy() },
            )
        }
    }
}

/**
 * Watches the web view's cookies and asks WebUntis whether the student is logged in.
 * Checks every WebUntis host the page has visited, because after a school login
 * WebUntis may land on another server than the search said.
 */
private class UntisLoginWatcher(
    private val school: FfiUntisSchool,
    private val onLoggedIn: (String, String, String) -> Unit,
) {
    var status by mutableStateOf(L("Melde dich so an, wie du es von WebUntis kennst, z. B. mit Microsoft 365. Meldo erkennt die Anmeldung automatisch."))
    var detail by mutableStateOf<String?>(null)
    var checking by mutableStateOf(false)

    private var webView: WebView? = null
    private val hosts = mutableListOf<String>()
    private val lastChecked = mutableMapOf<String, String>()
    private var task: Job? = null
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    fun makeWebView(context: android.content.Context, scope: CoroutineScope): WebView {
        // The app's cookie store keeps the Microsoft sign-in, so logging in again after
        // the WebUntis session ran out is usually one tap.
        CookieManager.getInstance().setAcceptCookie(true)
        val view = WebView(context)
        // Fixed size, not WRAP_CONTENT (AndroidView's default): with a wrap-content height the
        // WebView lays pages out at zero height, so 100vh is 0 and WebUntis' login collapses.
        view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        // The login moves between the school's server and webuntis.com (and Microsoft); Android's
        // WebView blocks those cross-site cookies by default, which leaves the login page empty.
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        // Page errors go to logcat (tag MeldoWebLogin) in debug builds only: login URLs and
        // page messages can carry one-time codes, which release builds must not write anywhere.
        val debuggable = (view.context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        view.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                if (debuggable) android.util.Log.w("MeldoWebLogin", "${message.messageLevel()} ${message.sourceId()}:${message.lineNumber()} ${message.message()}")
                return true
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                if (debuggable) android.util.Log.w("MeldoWebLogin", "error ${error.errorCode} ${error.description} ${request.url}")
            }

            override fun onReceivedHttpError(view: WebView, request: android.webkit.WebResourceRequest, response: android.webkit.WebResourceResponse) {
                if (debuggable) android.util.Log.w("MeldoWebLogin", "http ${response.statusCode} ${request.url}")
            }

            override fun onPageFinished(view: WebView, url: String?) {
                rememberHost(url?.let { Uri.parse(it).host })
                scope.launch { check(manual = false) }
            }
        }
        webView = view
        val url = Uri.Builder()
            .scheme("https")
            .authority(school.server)
            .path("/WebUntis/")
            .appendQueryParameter("school", school.loginName)
            .build()
        view.loadUrl(url.toString())
        start(scope)
        return view
    }

    private fun start(scope: CoroutineScope) {
        // The server from the school search is trusted as-is (some schools host WebUntis themselves).
        if (school.server !in hosts) hosts.add(school.server)
        task = scope.launch {
            while (isActive) {
                delay(2_000)
                check(manual = false)
            }
        }
    }

    fun stop() {
        task?.cancel()
    }

    fun rememberHost(host: String?) {
        // Only real WebUntis hosts: "webuntis.evil.example" must not become the school's server.
        if (host == null || !(host.equals("webuntis.com", ignoreCase = true) || host.endsWith(".webuntis.com", ignoreCase = true)) || host in hosts) return
        hosts.add(host)
    }

    /**
     * [manual]: the student says they are logged in; check every host now and show
     * what WebUntis answered if it still fails.
     */
    suspend fun check(manual: Boolean) {
        if (done || checking) return
        val view = webView ?: return
        rememberHost(view.url?.let { Uri.parse(it).host })
        if (manual) {
            checking = true
            status = L("Prüfe die Anmeldung …")
        }
        try {
            val report = mutableListOf<String>()
            for (host in hosts.toList()) {
                // Only this WebUntis server's cookies (CookieManager matches domain and path).
                val cookies = (CookieManager.getInstance().getCookie("https://$host/WebUntis/") ?: "")
                    .split(";")
                    .map { it.trim() }
                    .filter { it.contains("=") }
                val session = cookies.firstOrNull { it.startsWith("JSESSIONID=") }?.removePrefix("JSESSIONID=")
                if (session == null) {
                    report.add("$host: " + L("noch keine Sitzung"))
                    continue
                }
                val header = cookies.joinToString("; ")
                // Automatic checks only ask WebUntis again when the cookies changed.
                if (!manual && lastChecked[host] == header) continue
                lastChecked[host] = header
                val probe = UntisClient(browserCookies = header).probeBrowserLogin(host, session)
                if (probe.auth != null && !done) {
                    done = true
                    stop()
                    status = L("Angemeldet. Stundenplan wird geladen …")
                    onLoggedIn(host, header, session)
                    return
                }
                report.add(probe.detail)
            }
            if (manual) {
                status = L("Meldo erkennt die Anmeldung noch nicht. Das hat WebUntis geantwortet:")
                detail = report.joinToString("\n")
            }
        } finally {
            if (manual) checking = false
        }
    }
}
