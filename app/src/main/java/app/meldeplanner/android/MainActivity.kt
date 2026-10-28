package app.meldeplanner.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.meldeplanner.android.model.BackgroundRefresh
import app.meldeplanner.android.ui.navigation.DeepLinkTarget
import app.meldeplanner.android.ui.navigation.RootScreen
import app.meldeplanner.android.ui.theme.MeldoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var deepLink by mutableStateOf<DeepLinkTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val model = (application as MeldoApp).model
        deepLink = parse(intent)
        setContent {
            MeldoTheme(model.preferences) {
                // Rebuild the hierarchy when the language changes so every string re-resolves.
                key(model.preferences.language) {
                    RootScreen(model, meldoFeatures, deepLink) { deepLink = null }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Pick up Meldungen logged from widgets, the tile or notifications and re-plan reminders.
                model.reloadFromDisk()
                model.refreshSchedules()
                // Like iOS when the app becomes active: update WebUntis and Teams if due,
                // then every 30 seconds while the app stays open.
                launch { model.refreshConnections() }
                launch { BackgroundRefresh.foregroundLoop(model) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving Meldo: sync again in about 5 minutes (only matters with Google Drive on).
        val model = (application as MeldoApp).model
        if (model.wantsRefreshSoon) BackgroundRefresh.scheduleSoon(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parse(intent)?.let { deepLink = it }
    }

    /** `meldeplanner://melden` and `meldeplanner://hausaufgaben`, like the iOS deep links. */
    private fun parse(intent: Intent?): DeepLinkTarget? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "meldeplanner") return null
        return when (uri.host) {
            "melden" -> DeepLinkTarget.melden
            "hausaufgaben" -> DeepLinkTarget.homework
            else -> null
        }
    }
}
