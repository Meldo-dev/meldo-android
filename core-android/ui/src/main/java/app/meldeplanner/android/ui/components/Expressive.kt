@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.components

import app.meldeplanner.android.ui.theme.frosted
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.ui.theme.meldoHairline
import app.meldeplanner.android.ui.theme.FrostedWindow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

// Material 3 Expressive building blocks shared by every screen. Use these instead of
// SingleChoiceSegmentedButtonRow and plain DropdownMenu, so all of Meldo feels the same.

/**
 * A single choice as connected toggle buttons (Expressive button group): the
 * selected one is filled and morphs to a round shape. Replaces segmented buttons.
 */
@Composable
fun <T> MeldoSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    symbol: ((T) -> String?)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                // A step above surfaceContainer, so unselected options still read as buttons
                // inside a grouped row of the same color.
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                symbol?.invoke(option)?.let {
                    Icon(sfSymbol(it), null, Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One entry of a [MeldoMenu]. [checked] non-null makes it a checkable item. */
data class MeldoMenuItem(
    val title: String,
    val symbol: String? = null,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Expressive menu: items in rounded groups (iOS menus have the same sections).
 * Each inner list is one group. Closes itself after a choice.
 */
@Composable
fun MeldoMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    groups: List<List<MeldoMenuItem>>,
    modifier: Modifier = Modifier,
    labels: List<String?> = emptyList(),
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        val visible = groups.filter { it.isNotEmpty() }
        visible.forEachIndexed { groupIndex, items ->
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, visible.size), containerColor = meldoMenuGroupColor(), shadowElevation = 0.dp, border = meldoHairline()) {
                FrostedWindow()
labels.getOrNull(groups.indexOf(items))?.let { label ->
                    MenuDefaults.DropdownMenuGroupLabel { Text(label) }
                }
                items.forEachIndexed { index, item ->
                    val shapes = MenuDefaults.itemShape(index, items.size)
                    val icon: (@Composable () -> Unit)? = item.symbol?.let { s ->
                        { Icon(sfSymbol(s), null, Modifier.size(MenuDefaults.LeadingIconSize)) }
                    }
                    val text: @Composable () -> Unit = {
                        Text(
                            item.title,
                            color = if (item.destructive) androidx.compose.material3.MaterialTheme.colorScheme.error
                            else androidx.compose.ui.graphics.Color.Unspecified,
                        )
                    }
                    if (item.checked != null) {
                        CheckableDropdownMenuItem(
                            checked = item.checked,
                            onCheckedChange = { onDismiss(); item.onClick() },
                            text = text,
                            shapes = shapes,
                            // An empty slot when unchecked keeps the text in line with the checked item.
                            leadingIcon = icon ?: { Spacer(Modifier.size(MenuDefaults.LeadingIconSize)) },
                            checkedLeadingIcon = { Icon(sfSymbol("checkmark"), null, Modifier.size(MenuDefaults.LeadingIconSize)) },
                            enabled = item.enabled,
                            colors = meldoMenuItemColors(),
                        )
                    } else {
                        DropdownMenuItem(
                            text = text,
                            shape = shapes.shape,
                            onClick = { onDismiss(); item.onClick() },
                            leadingIcon = icon,
                            enabled = item.enabled,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Checked menu items in the warm accent tone, like segmented buttons and the
 * navigation bar. The Material default is tertiaryContainer, which is Meldo's indigo.
 */
@Composable
fun meldoMenuItemColors() = MenuDefaults.selectableItemColors(
    // Unchecked items sit directly on the (translucent) group panel.
    containerColor = androidx.compose.ui.graphics.Color.Transparent,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

/**
 * Menu panels: translucent over the blurred screen (see FrostedWindow), solid when
 * window blur is off. No shadow — a shadow under a translucent panel shows through
 * as a dark rim; the hairline edge separates it instead.
 */
@Composable
fun meldoMenuGroupColor() = frosted(MaterialTheme.colorScheme.surfaceContainerHigh, alpha = 0.78f)

/** Anchors a [MeldoMenu] to the button that opens it. */
@Composable
fun MenuAnchor(content: @Composable () -> Unit) = Box { content() }
