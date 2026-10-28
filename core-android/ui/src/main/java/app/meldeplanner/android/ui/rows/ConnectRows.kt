package app.meldeplanner.android.ui.rows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.ui.components.DS
import app.meldeplanner.android.ui.components.sfSymbol
import java.time.Instant
import kotlinx.coroutines.delay
import android.icu.text.RelativeDateTimeFormatter
import app.meldeplanner.android.core.Localization
import java.time.Duration

@Composable
fun ConnectList(padding: PaddingValues, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
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
fun ConnectSection(header: String? = null, footer: String? = null, content: @Composable () -> Unit) {
    FormSection(header = header, footer = footer, content = content)
}

@Composable
fun connectRowColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** `LabeledContent`: a label with its value on the trailing side. */
@Composable
fun ConnectLabeledRow(label: String, value: String) = ValueRow(label, value)

/** A button row; shows an Expressive loading indicator while [working]. */
@Composable
fun ConnectActionRow(
    text: String,
    working: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
    symbol: String? = null,
    onClick: () -> Unit,
) = ActionRow(text, symbol = symbol, enabled = enabled, destructive = destructive, working = working, onClick = onClick)

@Composable
fun ConnectNote(text: String, symbol: String, color: Color, inset: Boolean = true) {
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
fun ConnectIconLabel(text: String, symbol: String, headline: Boolean = false) {
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
fun connectRelativeTime(instant: Instant): String {
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
