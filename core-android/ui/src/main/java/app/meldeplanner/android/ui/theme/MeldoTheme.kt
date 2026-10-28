@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.meldeplanner.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.meldeplanner.android.data.AppTheme
import app.meldeplanner.android.data.AppearanceChoice
import app.meldeplanner.android.data.Preferences
import app.meldeplanner.android.data.SubjectColor

/** Meldo brand colors. */
object MeldoColors {
    val indigo = Color(0xFF3B3FD8)
    val indigoLight = Color(0xFF8D90FF)
    val orange = Color(0xFFFF9A4D)
    val yellow = Color(0xFFFFD447)
    val ink = Color(0xFF14152A)
}

/**
 * Material 3 color schemes for the three accents. iOS uses the accent as `tint`;
 * here it is the primary color (tone 40 in light, tone 80 in dark, as Material
 * You does), with Material You's wallpaper colors as an extra choice.
 */
private fun accentScheme(theme: AppTheme, dark: Boolean): ColorScheme = when (theme) {
    AppTheme.orange -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFFFB68E), onPrimary = Color(0xFF542100),
            primaryContainer = Color(0xFF773300), onPrimaryContainer = Color(0xFFFFDBC9),
            secondary = Color(0xFFE6BEAB), onSecondary = Color(0xFF432B1D),
            secondaryContainer = Color(0xFF5C4132), onSecondaryContainer = Color(0xFFFFDBC9),
            tertiary = Color(0xFFBFC2FF), onTertiary = Color(0xFF1A1BA8),
            tertiaryContainer = Color(0xFF2B2DC3), onTertiaryContainer = Color(0xFFE0E0FF),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF9C4400), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDBC9), onPrimaryContainer = Color(0xFF331200),
            secondary = Color(0xFF765848), onSecondary = Color.White,
            secondaryContainer = Color(0xFFFFDBC9), onSecondaryContainer = Color(0xFF2B160A),
            tertiary = Color(0xFF4044DB), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE0E0FF), onTertiaryContainer = Color(0xFF00006E),
        )
    }
    AppTheme.indigo -> if (dark) {
        darkColorScheme(
            primary = Color(0xFFBFC2FF), onPrimary = Color(0xFF1A1BA8),
            primaryContainer = Color(0xFF2B2DC3), onPrimaryContainer = Color(0xFFE0E0FF),
            secondary = Color(0xFFC5C4DD), onSecondary = Color(0xFF2E2F42),
            secondaryContainer = Color(0xFF444559), onSecondaryContainer = Color(0xFFE1E0F9),
            tertiary = Color(0xFFFFB68E), onTertiary = Color(0xFF542100),
            tertiaryContainer = Color(0xFF773300), onTertiaryContainer = Color(0xFFFFDBC9),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4044DB), onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E0FF), onPrimaryContainer = Color(0xFF00006E),
            secondary = Color(0xFF5C5D72), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE1E0F9), onSecondaryContainer = Color(0xFF191A2C),
            tertiary = Color(0xFF9C4400), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFDBC9), onTertiaryContainer = Color(0xFF331200),
        )
    }
    AppTheme.green -> if (dark) {
        darkColorScheme(
            primary = Color(0xFF7FDA87), onPrimary = Color(0xFF003911),
            primaryContainer = Color(0xFF00531A), onPrimaryContainer = Color(0xFF9BF6A0),
            secondary = Color(0xFFB8CCB4), onSecondary = Color(0xFF243424),
            secondaryContainer = Color(0xFF3A4B39), onSecondaryContainer = Color(0xFFD4E8CF),
            tertiary = Color(0xFFA1CED8), onTertiary = Color(0xFF00363E),
            tertiaryContainer = Color(0xFF1F4D55), onTertiaryContainer = Color(0xFFBDEAF4),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006E26), onPrimary = Color.White,
            primaryContainer = Color(0xFF9BF6A0), onPrimaryContainer = Color(0xFF002107),
            secondary = Color(0xFF52634F), onSecondary = Color.White,
            secondaryContainer = Color(0xFFD4E8CF), onSecondaryContainer = Color(0xFF101F10),
            tertiary = Color(0xFF39656D), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFBDEAF4), onTertiaryContainer = Color(0xFF001F24),
        )
    }
}

/**
 * Neutrals tinted with the accent, with clear steps between page, cards and raised
 * layers. Baseline Material puts cards only ~1.15:1 above the page, which reads as
 * gray on gray; these spread the layers apart like iOS grouped backgrounds. In light
 * mode cards are near-white on a tinted page, as on iOS.
 */
private fun ColorScheme.withSurfaces(theme: AppTheme, dark: Boolean): ColorScheme {
    // page, lowest, low, container, high, highest, variant text, outline variant
    val n: List<Long> = when (theme) {
        // Dark matches iOS: black page (systemGroupedBackground), cards in #1C1C1E
        // (secondarySystemGroupedBackground), raised layers #2C2C2E / #3A3A3C, secondary
        // text and separators in iOS's neutral grays.
        AppTheme.orange -> if (dark) listOf(0xFF000000, 0xFF000000, 0xFF1C1C1E, 0xFF1C1C1E, 0xFF2C2C2E, 0xFF3A3A3C, 0xFFAEAEB2, 0xFF38383A)
        else listOf(0xFFF5EEEA, 0xFFFFFFFF, 0xFFFFFCFA, 0xFFFFFAF7, 0xFFF8EFEA, 0xFFF0E5DF, 0xFF54453D, 0xFFD9C4B9)
        AppTheme.indigo -> if (dark) listOf(0xFF0E0D13, 0xFF09090E, 0xFF17161F, 0xFF1E1C27, 0xFF282632, 0xFF33313D, 0xFFC8C5D2, 0xFF474651)
        else listOf(0xFFF1F0F7, 0xFFFFFFFF, 0xFFFCFCFF, 0xFFFAF9FF, 0xFFF0EFF7, 0xFFE7E5EF, 0xFF474651, 0xFFC8C5D2)
        AppTheme.green -> if (dark) listOf(0xFF0B0F0B, 0xFF070A07, 0xFF141914, 0xFF1A201A, 0xFF242B24, 0xFF2F362F, 0xFFC1C9BC, 0xFF414A40)
        else listOf(0xFFEFF3EC, 0xFFFFFFFF, 0xFFFBFDF9, 0xFFF9FCF7, 0xFFEEF2EA, 0xFFE5EAE1, 0xFF414A40, 0xFFC1C9BC)
    }
    val c = n.map { Color(it) }
    return copy(
        background = c[0], surface = c[0], surfaceDim = c[0],
        surfaceContainerLowest = c[1], surfaceContainerLow = c[2], surfaceContainer = c[3],
        surfaceContainerHigh = c[4], surfaceContainerHighest = c[5], surfaceBright = c[5],
        surfaceVariant = c[5], onSurfaceVariant = c[6], outlineVariant = c[7],
    )
}

/** The brand accent behind the scheme, for decorative color (rings, the Melden button). */
fun AppTheme.brandColor(): Color = when (this) {
    AppTheme.orange -> MeldoColors.orange
    AppTheme.indigo -> MeldoColors.indigo
    AppTheme.green -> Color(0xFF34C759)
}

/** Whether the app draws dark, after the appearance setting. */
@Composable
fun Preferences.isDark(): Boolean = when (appearance) {
    AppearanceChoice.system -> isSystemInDarkTheme()
    AppearanceChoice.light -> false
    AppearanceChoice.dark -> true
}

/** Wallpaper colors (Material You) exist from Android 12. */
val dynamicColorAvailable: Boolean get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

/** Is the current theme dark? Read by subject colors, which have light and dark variants. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun MeldoTheme(preferences: Preferences, content: @Composable () -> Unit) {
    val dark = preferences.isDark()
    val context = LocalContext.current
    // Meldo is always orange: a stored theme or Material You choice from older versions is ignored.
    val scheme = accentScheme(AppTheme.orange, dark).withSurfaces(AppTheme.orange, dark)
    // System bar icons follow Meldo's appearance, not the device's dark mode.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        // Material 3 Expressive: springy motion, rounder shapes, emphasized type.
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            shapes = MeldoShapes,
            typography = MeldoTypography,
            content = content,
        )
    }
}

/** Corner scale: rounder than baseline Material, like the iOS continuous corners. */
val MeldoShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

/** The wallpaper accent (Material You), for previewing that choice in Einstellungen. */
@Composable
fun dynamicAccentPreview(): Color {
    val context = LocalContext.current
    if (!dynamicColorAvailable) return MaterialTheme.colorScheme.primary
    return if (LocalDarkTheme.current) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
}

/**
 * Subject palette after the iOS system colors it mirrors, which adapt to dark
 * mode. Keys are stored in data.json, never raw values.
 */
val SubjectColor.color: Color
    @Composable get() = color(LocalDarkTheme.current)

fun SubjectColor.color(dark: Boolean): Color = when (this) {
    SubjectColor.blue -> if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    SubjectColor.indigo -> if (dark) Color(0xFF5E5CE6) else Color(0xFF5856D6)
    SubjectColor.purple -> if (dark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
    SubjectColor.pink -> if (dark) Color(0xFFFF375F) else Color(0xFFFF2D55)
    SubjectColor.red -> if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    SubjectColor.orange -> if (dark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    SubjectColor.yellow -> if (dark) Color(0xFFFFD60A) else Color(0xFFFFCC00)
    SubjectColor.green -> if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    SubjectColor.mint -> if (dark) Color(0xFF63E6E2) else Color(0xFF00C7BE)
    SubjectColor.teal -> if (dark) Color(0xFF40C8E0) else Color(0xFF30B0C7)
    SubjectColor.cyan -> if (dark) Color(0xFF64D2FF) else Color(0xFF32ADE6)
    SubjectColor.brown -> if (dark) Color(0xFFAC8E68) else Color(0xFFA2845E)
    SubjectColor.gray -> Color(0xFF8E8E93)
}

/** iOS semantic colors used for states (Entfall red, Vertretung orange, …). */
object StateColors {
    @Composable fun red() = SubjectColor.red.color
    @Composable fun orange() = SubjectColor.orange.color
    @Composable fun green() = SubjectColor.green.color
    @Composable fun blue() = SubjectColor.blue.color
    @Composable fun yellow() = SubjectColor.yellow.color
    @Composable fun gray() = SubjectColor.gray.color
    @Composable fun brown() = SubjectColor.brown.color
}
