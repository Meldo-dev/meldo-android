package app.meldeplanner.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

/**
 * Material 3 type scale with Expressive emphasis: bolder headlines and titles so
 * screens read with the confidence of the iOS large titles. Mapping from the iOS
 * text styles: largeTitle → headlineLarge, title → headlineMedium, title2 →
 * headlineSmall, title3 → titleLarge, headline → titleMedium, body → bodyLarge,
 * callout → bodyMedium, subheadline → titleSmall/bodyMedium, footnote →
 * bodySmall, caption → labelMedium, caption2 → labelSmall.
 */
val MeldoTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}
