@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.features.untis

import app.meldeplanner.android.ui.theme.MeldoAlertDialog
import android.icu.text.RelativeDateTimeFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.core.L
import app.meldeplanner.android.core.Localization
import app.meldeplanner.android.data.UntisClient
import app.meldeplanner.android.data.UntisFailure
import app.meldeplanner.android.data.UntisLink
import app.meldeplanner.android.model.connectUntis
import app.meldeplanner.android.model.connectUntisWithBrowser
import app.meldeplanner.android.model.disconnectUntis
import app.meldeplanner.android.model.refreshUntis
import app.meldeplanner.android.model.untisLink
import app.meldeplanner.android.model.untisServerIsListed
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.LocalAppModel
import app.meldeplanner.android.ui.rows.meldoAnimateItem
import app.meldeplanner.android.ui.rows.ActionRow
import app.meldeplanner.android.ui.rows.FormFooter
import app.meldeplanner.android.ui.rows.FormHeader
import app.meldeplanner.android.ui.rows.FormItemSurface
import app.meldeplanner.android.ui.rows.FormSection
import app.meldeplanner.android.ui.rows.IconTile
import app.meldeplanner.android.ui.rows.ValueRow
import app.meldeplanner.android.ui.rows.extend
import app.meldeplanner.android.ui.rows.formItems
import app.meldeplanner.android.ui.components.sfSymbol
import app.meldeplanner.android.ui.navigation.LocalNavigator
import app.meldeplanner.android.ui.navigation.Navigator
import app.meldeplanner.android.ui.navigation.MeldoScreen
import app.meldeplanner.android.ui.theme.StateColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.melde_ffi.FfiUntisSchool
import java.time.Duration
import java.time.Instant

/**
 * Connect WebUntis: find the school, sign in, import. Afterwards: status, refresh
 * and disconnect. Port of meldo-ios/MeldePlanner/Features/Untis/UntisConnectView.swift.
 */
@Composable
fun UntisConnectScreen(onConnected: (() -> Unit)? = null) {
    val model = LocalAppModel.current
    val link = model.untisLink
    if (link != null) UntisStatus(link) else UntisSchoolSearch(onConnected)
}

/**
 * [onConnected] runs once the import succeeded (the tour closes itself); without it the
 * login screen just closes and this screen shows the connection.
 */
@Composable
private fun UntisSchoolSearch(onConnected: (() -> Unit)?) {
    val navigator = LocalNavigator.current
    var query by remember { mutableStateOf("") }
    var schools by remember { mutableStateOf<List<FfiUntisSchool>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // A gentle note while typing, e.g. that the search is still too general.
    var hint by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            // The field is laid out in the next frame (lazy list).
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 3) {
            schools = emptyList()
            problem = null
            hint = null
            return@LaunchedEffect
        }
        // Debounce typing.
        delay(400)
        searching = true
        try {
            schools = UntisClient().searchSchools(trimmed)
            problem = null
            hint = if (schools.isEmpty()) L("Keine Schule gefunden. Prüfe die Schreibweise oder versuche nur Schulart und Ort.") else null
        } catch (e: UntisFailure.TooManyResults) {
            // Normal while typing: WebUntis lists nothing until the search is specific enough.
            schools = emptyList()
            problem = null
            hint = L("Zu viele Schulen gefunden. Gib mehr vom Namen und den Ort ein, zum Beispiel „Gymnasium Köln-Pesch“.")
        } catch (e: UntisFailure) {
            schools = emptyList()
            hint = null
            problem = UntisMessages.text(e)
        } finally {
            searching = false
        }
    }

    MeldoScreen(L("WebUntis")) { padding ->
        ConnectList(padding) {
            item {
                ConnectSection {
                    Column(Modifier.padding(DS.spacingL), verticalArrangement = Arrangement.spacedBy(DS.spacingM)) {
                        ConnectIconLabel(L("Stundenplan aus WebUntis übernehmen"), "arrow.triangle.2.circlepath", headline = true)
                        Text(
                            L("Meldo übernimmt deinen Stundenplan, Ausfälle und Vertretungen aus WebUntis. Dein Login bleibt auf diesem Gerät und geht nur an den WebUntis-Server deiner Schule."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                            UntisStep(1, L("Schule suchen und antippen"), active = true)
                            UntisStep(2, L("Mit deinem WebUntis-Login anmelden"), active = false)
                            UntisStep(3, L("Meldo importiert deinen Stundenplan"), active = false)
                        }
                    }
                }
            }
            item(key = "search-header") { FormHeader(L("1. Schule finden"), Modifier.padding(horizontal = DS.spacingL)) }
            item(key = "search") {
                SchoolSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    searching = searching,
                    modifier = Modifier.padding(horizontal = DS.spacingL).focusRequester(focus),
                )
            }
            item(key = "search-footer") {
                FormFooter(
                    L("Gib den Namen deiner Schule zusammen mit dem Ort ein, zum Beispiel „Gymnasium Köln-Pesch“. Bei zu allgemeinen Suchen zeigt WebUntis keine Liste an."),
                    Modifier.padding(horizontal = DS.spacingL),
                )
            }
            hint?.let { text ->
                item(key = "hint") {
                    Box(meldoAnimateItem().padding(horizontal = DS.spacingL)) {
                        ConnectNote(text, "text.magnifyingglass", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            problem?.let { text ->
                item(key = "problem") {
                    Box(meldoAnimateItem().padding(horizontal = DS.spacingL)) {
                        ConnectNote(text, "exclamationmark.triangle.fill", StateColors.orange())
                    }
                }
            }
            if (schools.isNotEmpty()) {
                formItems(
                    sectionKey = "schools",
                    items = schools.withIndex().toList(),
                    key = { (index, school) -> "school-$index-${school.server}-${school.loginName}" },
                ) { (_, school), shape ->
                    FormItemSurface(shape) {
                        ListItem(
                            headlineContent = { Text(school.displayName, fontWeight = FontWeight.SemiBold) },
                            supportingContent = school.address.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                            leadingContent = { IconTile(sfSymbol("building.columns")) },
                            trailingContent = { Icon(sfSymbol("chevron.right"), null) },
                            colors = connectRowColors(),
                            modifier = Modifier.clickable(role = Role.Button) {
                                val loginDepth = navigator.depth + 1
                                navigator.push {
                                    UntisLoginScreen(school) {
                                        // Only if the student is still on the login screen (the import runs on).
                                        if (navigator.depth == loginDepth) navigator.pop()
                                        onConnected?.invoke()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The school search as an Expressive search field (pill on a tonal surface, like a SearchBar). */
@Composable
private fun SchoolSearchField(query: String, onQueryChange: (String) -> Unit, searching: Boolean, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().heightIn(min = SearchBarDefaults.InputFieldHeight),
        placeholder = { Text(L("Schulname und Ort")) },
        leadingIcon = { Icon(sfSymbol("magnifyingglass"), null) },
        trailingIcon = {
            when {
                searching -> LoadingIndicator(Modifier.size(32.dp))
                query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(sfSymbol("xmark.circle.fill"), L("Löschen"))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
    )
}

@Composable
private fun UntisLoginScreen(school: FfiUntisSchool, onConnected: () -> Unit) {
    val model = LocalAppModel.current
    val navigator = LocalNavigator.current
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val connected by rememberUpdatedState(onConnected)
    fun connectWithBrowser(server: String, cookies: String, sessionID: String) {
        model.scope.launch {
            working = true
            problem = null
            try {
                model.connectUntisWithBrowser(school, server, cookies, sessionID)
                // Like iOS: no summary to confirm, the screen closes after a successful import.
                connected()
            } catch (e: UntisFailure) {
                problem = UntisMessages.text(e)
            } finally {
                working = false
            }
        }
    }

    fun connect() {
        model.scope.launch {
            working = true
            problem = null
            try {
                model.connectUntis(school, user, password)
                password = ""
                connected()
            } catch (e: UntisFailure) {
                problem = UntisMessages.text(e)
            } finally {
                working = false
            }
        }
    }

    MeldoScreen(L("Anmelden"), largeTitle = false) { padding ->
        ConnectList(padding) {
            item {
                ConnectSection(
                    header = L("2. Anmelden"),
                    footer = L("Für Schulen, bei denen du dich bei WebUntis mit Microsoft 365, IServ oder einer anderen Seite anmeldest. Du meldest dich auf der echten Seite deiner Schule an; Meldo sieht dein Passwort nie."),
                ) {
                    ConnectLabeledRow(L("Schule"), school.displayName)
                    Button(
                        onClick = {
                            openWebLogin(navigator, school) { server, cookies, sessionID ->
                                connectWithBrowser(server, cookies, sessionID)
                            }
                        },
                        enabled = !working,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().padding(DS.spacingM).heightIn(min = 56.dp),
                    ) {
                        Icon(sfSymbol("person.badge.key.fill"), null, Modifier.size(18.dp))
                        Spacer(Modifier.size(DS.spacingS))
                        Text(L("Mit Microsoft 365 oder Schul-Login anmelden"), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                ConnectSection(
                    header = L("Oder mit WebUntis-Benutzername"),
                    footer = L("Derselbe Benutzername und dasselbe Passwort wie auf der WebUntis-Seite oder in der Untis-App deiner Schule. Das Passwort wird nur verschlüsselt auf diesem Gerät gespeichert."),
                ) {
                    Column(Modifier.padding(DS.spacingM), verticalArrangement = Arrangement.spacedBy(DS.spacingS)) {
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text(L("Benutzername")) },
                            singleLine = true,
                            shape = DS.rowShape,
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(L("Passwort")) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = DS.rowShape,
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                if (!working && user.isNotEmpty() && password.isNotEmpty()) connect()
                            }),
                        )
                    }
                }
            }
            problem?.let { text ->
                item(key = "problem") {
                    Box(meldoAnimateItem()) { ConnectSection { ConnectNote(text, "exclamationmark.triangle.fill", StateColors.red()) } }
                }
            }
            item {
                ConnectSection {
                    ConnectActionRow(
                        text = L("Verbinden und importieren"),
                        working = working,
                        enabled = !working && user.isNotEmpty() && password.isNotEmpty(),
                        onClick = ::connect,
                    )
                }
            }
        }
    }

}

@Composable
private fun UntisStatus(link: UntisLink) {
    val model = LocalAppModel.current
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmingDisconnect by remember { mutableStateOf(false) }
    val navigator = LocalNavigator.current
    val school = FfiUntisSchool(server = link.server, loginName = link.loginName, displayName = link.schoolName, address = "")

    fun loginAgain(server: String, cookies: String, sessionID: String) {
        model.scope.launch {
            working = true
            try {
                val s = model.connectUntisWithBrowser(school, server, cookies, sessionID)
                message = L("Aktualisiert: %lld Stunden, %lld Änderungen.", s.lessons, s.changes)
            } catch (e: UntisFailure) {
                message = UntisMessages.text(e)
            } finally {
                working = false
            }
        }
    }

    // The link may come from another device (sync): see untisServerIsListed.
    fun reLogin() {
        model.scope.launch {
            working = true
            try {
                if (untisServerIsListed(link) { UntisClient().searchSchools(it) }) {
                    openWebLogin(navigator, school, ::loginAgain)
                } else {
                    message = L("Diese Schule steht nicht in der WebUntis Schulsuche. Trenne WebUntis und verbinde dich neu.")
                }
            } catch (e: UntisFailure) {
                message = UntisMessages.text(e)
            } finally {
                working = false
            }
        }
    }

    fun refresh() {
        model.scope.launch {
            working = true
            try {
                val s = model.refreshUntis()
                message = if (s != null) {
                    L("Aktualisiert: %lld Stunden, %lld Änderungen.", s.lessons, s.changes)
                } else {
                    L("Bitte melde dich erneut an.")
                }
            } catch (e: UntisFailure) {
                message = UntisMessages.text(e)
            } finally {
                working = false
            }
        }
    }

    MeldoScreen(L("WebUntis")) { padding ->
        ConnectList(padding) {
            item {
                ConnectSection(footer = L("Meldo aktualisiert Stundenplan, Ausfälle und Vertretungen automatisch: beim Öffnen, alle 30 Minuten und im Hintergrund.")) {
                    ConnectLabeledRow(L("Schule"), link.schoolName)
                    ConnectLabeledRow(L("Benutzer"), if (link.browserLogin == true) L("Schul-Login") else link.user)
                    link.lastSync?.let { last ->
                        ConnectLabeledRow(L("Zuletzt aktualisiert"), connectRelativeTime(last))
                    }
                }
            }
            message?.let { text ->
                item(key = "message") {
                    Text(
                        text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = meldoAnimateItem().padding(horizontal = DS.spacingXL),
                    )
                }
            }
            item {
                ConnectSection {
                    ConnectActionRow(
                        L("Jetzt aktualisieren"), symbol = "arrow.triangle.2.circlepath",
                        working = working, enabled = !working, onClick = ::refresh,
                    )
                    if (link.browserLogin == true) {
                        ConnectActionRow(
                            L("Erneut mit Schul-Login anmelden"), symbol = "person.badge.key",
                            enabled = !working, onClick = ::reLogin,
                        )
                    }
                    ConnectActionRow(
                        L("WebUntis trennen"), symbol = "trash", destructive = true,
                        enabled = !working, onClick = { confirmingDisconnect = true },
                    )
                }
            }
        }
    }

    if (confirmingDisconnect) {
        MeldoAlertDialog(
            onDismissRequest = { confirmingDisconnect = false },
            title = { Text(L("WebUntis trennen?")) },
            text = { Text(L("Dein Stundenplan bleibt erhalten, wird aber nicht mehr aktualisiert. Das Passwort wird gelöscht.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDisconnect = false
                        model.disconnectUntis()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(L("Trennen"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDisconnect = false }, shapes = ButtonDefaults.shapes()) { Text(L("Abbrechen")) }
            },
        )
    }
}

/**
 * Opens the school's login page as a pushed screen (iOS: a sheet). Not a Dialog:
 * a WebView in a Compose dialog window is reported invisible and never draws.
 */
private fun openWebLogin(
    navigator: Navigator,
    school: FfiUntisSchool,
    onLoggedIn: (server: String, cookies: String, sessionID: String) -> Unit,
) {
    navigator.push {
        UntisWebLoginScreen(school, onDismiss = navigator::pop) { server, cookies, sessionID ->
            navigator.pop()
            onLoggedIn(server, cookies, sessionID)
        }
    }
}

object UntisMessages {
    fun text(error: Throwable): String = when (val failure = error as? UntisFailure) {
        UntisFailure.WrongCredentials -> L("Benutzername oder Passwort ist falsch.")
        UntisFailure.TooManyResults -> L("Zu viele Schulen gefunden. Gib mehr vom Namen und den Ort ein, zum Beispiel „Gymnasium Köln-Pesch“.")
        UntisFailure.Network -> L("Keine Verbindung zu WebUntis. Prüfe dein Internet.")
        UntisFailure.SessionExpired -> L("Deine WebUntis-Anmeldung ist abgelaufen. Melde dich erneut an.")
        UntisFailure.NoLessons -> L("WebUntis zeigt in den nächsten acht Wochen keinen Unterricht für dich, zum Beispiel wegen Ferien. Versuche es später noch einmal.")
        is UntisFailure.Server -> L("WebUntis hat die Anfrage abgelehnt (Code %lld).", failure.code)
        is UntisFailure.NoAccess, null -> L("Deine Schule erlaubt diesen Zugriff gerade nicht, oder WebUntis wird gewartet.")
    }
}

/** One numbered step of the WebUntis setup. */
@Composable
private fun UntisStep(number: Int, text: String, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
        Box(
            Modifier
                .size(22.dp)
                .background(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "%d".format(Localization.locale, number),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// region Form building blocks (iOS inset grouped List / Form)

@Composable
internal fun ConnectList(padding: PaddingValues, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.imePadding(),
        // MeldoScreen's padding as content padding: the list scrolls under the translucent bars.
        contentPadding = padding.extend(top = DS.spacingS, bottom = DS.spacingXL),
        verticalArrangement = Arrangement.spacedBy(DS.spacingM),
        content = content,
    )
}

/** A grouped section (the shared [FormSection]): each child is one tonal segment. */
@Composable
internal fun ConnectSection(header: String? = null, footer: String? = null, content: @Composable () -> Unit) {
    FormSection(header = header, footer = footer, content = content)
}

@Composable
internal fun connectRowColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** `LabeledContent`: a label with its value on the trailing side. */
@Composable
internal fun ConnectLabeledRow(label: String, value: String) = ValueRow(label, value)

/** A button row; shows an Expressive loading indicator while [working]. */
@Composable
internal fun ConnectActionRow(
    text: String,
    working: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    symbol: String? = null,
    onClick: () -> Unit,
) = ActionRow(text, symbol = symbol, enabled = enabled, destructive = destructive, working = working, onClick = onClick)

@Composable
internal fun ConnectNote(text: String, symbol: String, color: Color, inset: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (inset) DS.spacingL else 0.dp, vertical = if (inset) DS.spacingS else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(DS.spacingS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(sfSymbol(symbol), null, Modifier.size(18.dp), tint = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
internal fun ConnectIconLabel(text: String, symbol: String, headline: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DS.spacingS)) {
        Icon(sfSymbol(symbol), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            text,
            style = if (headline) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (headline) FontWeight.SemiBold else null,
        )
    }
}

/** "vor 5 Minuten" in the app language, kept current (iOS `Text(date, style: .relative)`). */
@Composable
internal fun connectRelativeTime(instant: Instant): String {
    val text by produceState(format(instant), instant) {
        while (true) {
            value = format(instant)
            delay(30_000)
        }
    }
    return text
}

private fun format(instant: Instant): String {
    val formatter = RelativeDateTimeFormatter.getInstance(Localization.locale)
    val seconds = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
        seconds < 3600 -> formatter.format((seconds / 60).toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.MINUTES)
        seconds < 86_400 -> formatter.format((seconds / 3600).toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.HOURS)
        else -> formatter.format((seconds / 86_400).toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.DAYS)
    }
}

// endregion
