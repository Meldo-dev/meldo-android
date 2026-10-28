package app.meldeplanner.android.model

import app.meldeplanner.android.data.SecureStore
import app.meldeplanner.android.data.UntisLink

/**
 * "server|school|user", the key it always had. A name containing "|" could make two
 * accounts share it, so those get an escaped key in their own "server#…" form.
 */
fun keychainAccount(link: UntisLink): String {
    if ('|' !in link.loginName && '|' !in link.user) return "${link.server}|${link.loginName}|${link.user}"
    fun part(text: String) = text.replace("%", "%25").replace("|", "%7C")
    return "${link.server}#${part(link.loginName)}|${part(link.user)}"
}

/** The secure store, bound to the app context on first use. */
fun AppModel.secrets(): SecureStore {
    context?.let(SecureStore::init)
    return SecureStore
}

const val TEAMS_ACCOUNT = "microsoft-teams"
