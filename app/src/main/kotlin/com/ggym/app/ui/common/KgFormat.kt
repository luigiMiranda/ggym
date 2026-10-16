package com.ggym.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.util.Locale

/** The locale the UI is being formatted in, so every number follows the user's device. */
@Composable
fun activeLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * Weights, as text.
 *
 * Formatting only — the kg suffix is a string resource with a placeholder (spine §5), because
 * "82,5 kg" versus "82,5kg" is a translation question, not a numeric one. Fraction digits are
 * trimmed, so 80 kg is "80" and not "80,0": the 2.5 kg plate is the smallest step the field
 * offers, and a trailing zero reads like false precision.
 */
object KgFormat {

    /** [weightKg] as a plain number in [locale] — "82.5" in en, "82,5" in it. */
    fun formatNumber(weightKg: Double, locale: Locale): String =
        numberFormat(locale).format(weightKg)

    private fun numberFormat(locale: Locale): NumberFormat = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
}
