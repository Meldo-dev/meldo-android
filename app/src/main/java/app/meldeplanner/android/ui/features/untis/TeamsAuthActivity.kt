package app.meldeplanner.android.ui.features.untis

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import app.meldeplanner.android.MainActivity
import app.meldeplanner.android.data.TeamsFailure
import kotlinx.coroutines.CompletableDeferred

/**
 * A sign-in page (Microsoft for Teams, Google for Drive) in a Custom Tab, the counterpart
 * of iOS' `webAuthenticationSession`: [authenticate] opens the page and waits for the
 * redirect to `meldeplanner://auth` or `app.meldeplanner.android:/oauth2redirect`, which
 * [TeamsAuthActivity] receives. Closing it ends with [TeamsFailure.Cancelled].
 */
object TeamsAuthSession {
    @Volatile private var pending: CompletableDeferred<String>? = null
    @Volatile private var expectedState: String? = null

    /**
     * Opens [url] and suspends until Microsoft redirects back with [state]; throws
     * [TeamsFailure.Cancelled] when closed. Any app can open `meldeplanner://auth`, so a
     * callback with another state is ignored instead of ending the sign-in.
     */
    suspend fun authenticate(context: Context, url: String, state: String, ephemeral: Boolean = false): String {
        pending?.cancel()
        val deferred = CompletableDeferred<String>()
        expectedState = state
        pending = deferred
        val builder = CustomTabsIntent.Builder().setShowTitle(true)
        // No shared browser cookies where the browser supports it: after "trennen" no sign-in stays behind.
        if (ephemeral && CustomTabsClient.isEphemeralBrowsingSupported(context, CustomTabsClient.getPackageName(context, null) ?: "")) {
            builder.setEphemeralBrowsingEnabled(true)
        }
        builder.build().launchUrl(context, url.toUri())
        try {
            return deferred.await()
        } finally {
            if (pending === deferred) pending = null
        }
    }

    /** The student came back to Meldo without finishing the sign-in. */
    fun cancel() {
        pending?.completeExceptionally(TeamsFailure.Cancelled)
    }

    val isWaiting: Boolean get() = pending?.isActive == true

    internal fun complete(callback: String): Boolean {
        if (callback.toUri().getQueryParameter("state") != expectedState) return false
        return pending?.complete(callback) ?: false
    }
}

/**
 * Receives `meldeplanner://auth?code=…` (or Google's redirect) from the browser, hands it to the waiting
 * sign-in and returns to Meldo (closing the Custom Tab above it).
 */
class TeamsAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A callback that isn't the one Meldo waits for (wrong state, opened by another app)
        // must not close Microsoft's page either.
        if (intent?.data?.toString()?.let(TeamsAuthSession::complete) != true) {
            finish()
            return
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
