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

    /**
     * A weight with exactly one decimal, for a statistic: "78.4", "84.0".
     *
     * The body-weight summary is a comparison of three numbers, so the decimals line up instead of
     * trailing zeros being trimmed away; the log and the fields keep [formatNumber]'s behaviour,
     * where "80" is a weight someone typed and "80,0" would be false precision.
     */
    fun formatOneDecimal(weightKg: Double, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 1
        }.format(weightKg)

    private fun numberFormat(locale: Locale): NumberFormat = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }
}
