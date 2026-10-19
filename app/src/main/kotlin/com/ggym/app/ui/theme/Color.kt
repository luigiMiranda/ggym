package com.ggym.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * GGym brand palette, sampled from the app icon (ggym.png).
 *
 * Two rules are enforced by `BrandPaletteTest`:
 *  1. chartreuse never carries text on a light surface (~1.3:1 — unreadable);
 *     the light scheme's primary is deep olive instead;
 *  2. every on/container pair used for body or label text clears WCAG AA.
 */
object GgymBrand {
    /** Near-black canvas; the icon background and the dark scheme's surface. */
    val Canvas = Color(0xFF0D0E10)

    /** Chartreuse body tone — the dark scheme's primary. */
    val Chartreuse = Color(0xFFD5FF00)

    /** Bright chartreuse core, reserved for hero data (weight, countdown). */
    val ChartreuseHero = Color(0xFFEFFF46)

    /** Darker chartreuse, for tonal fills that must not glare. */
    val ChartreuseShade = Color(0xFFB0E600)

    /** Deep olive — the light scheme's primary. ~7:1 on white. */
    val Olive = Color(0xFF4A5D00)
}

/**
 * The hero plate: the near-black card the weight and the countdown live on.
 *
 * It stays dark in the light scheme by design — an off-white page would force chartreuse to
 * disappear (it is ~1.3:1 on white), and the hero number is the one thing on the screen that must
 * never be quiet. The two surface values keep the plate slightly green-tinted in light mode so it
 * sits in the olive palette rather than looking like a hole in the page.
 */
object GgymHeroColors {
    val SurfaceDark = Color(0xFF05060A)
    val SurfaceLight = Color(0xFF101208)
    val BorderDark = Color(0xFF1B1F14)
    val BorderLight = Color(0xFF1E2410)

    /** Body text on the plate: the dark scheme's onSurface, readable on both plates. */
    val Content = Color(0xFFE6E7E4)

    /** Labels and supporting lines on the plate. */
    val Muted = Color(0xFF8A8F7F)

    /** The number itself. */
    val Accent = GgymBrand.ChartreuseHero

    /** The "+2.5" button: a lime control on the dark plate. */
    val AccentButton = GgymBrand.Chartreuse
    val OnAccentButton = Color(0xFF1A2000)

    /** A neutral control on the plate ("-2.5"). */
    val Button = Color(0xFF24272C)
    val OnButton = Color(0xFFE6E7E4)

    /** The in-progress resume card on Workouts. */
    val InProgress = Color(0xFF12200A)
    val InProgressBorder = Color(0xFF3A4A00)
}

val GgymDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD5FF00),
    onPrimary = Color(0xFF1A2000),
    primaryContainer = Color(0xFF3A4A00),
    onPrimaryContainer = Color(0xFFDCFF64),

    secondary = Color(0xFFC3D18F),
    onSecondary = Color(0xFF2A3400),
    secondaryContainer = Color(0xFF3F4A1F),
    onSecondaryContainer = Color(0xFFDFEBBB),

    tertiary = Color(0xFFEFFF46),
    onTertiary = Color(0xFF232A00),
    tertiaryContainer = Color(0xFF4A5500),
    onTertiaryContainer = Color(0xFFF0FF9E),

    background = Color(0xFF0D0E10),
    onBackground = Color(0xFFE6E7E4),
    surface = Color(0xFF0D0E10),
    onSurface = Color(0xFFE6E7E4),
    surfaceVariant = Color(0xFF2A2E24),
    onSurfaceVariant = Color(0xFFC2C7B2),

    surfaceContainerLowest = Color(0xFF08090B),
    surfaceContainerLow = Color(0xFF131418),
    surfaceContainer = Color(0xFF17191D),
    surfaceContainerHigh = Color(0xFF1D2024),
    surfaceContainerHighest = Color(0xFF24272C),

    outline = Color(0xFF8A8F7F),
    outlineVariant = Color(0xFF3A3F35),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    inverseSurface = Color(0xFFE6E7E4),
    inverseOnSurface = Color(0xFF1A1C17),
    inversePrimary = Color(0xFF4A5D00),
    scrim = Color(0xFF000000),
)

val GgymLightColorScheme = lightColorScheme(
    primary = Color(0xFF4A5D00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9F87F),
    onPrimaryContainer = Color(0xFF141F00),

    secondary = Color(0xFF57642F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE9AC),
    onSecondaryContainer = Color(0xFF161E00),

    tertiary = Color(0xFF0B6B4F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA8F0D2),
    onTertiaryContainer = Color(0xFF002116),

    background = Color(0xFFFBFDF6),
    onBackground = Color(0xFF1A1C17),
    surface = Color(0xFFFBFDF6),
    onSurface = Color(0xFF1A1C17),
    surfaceVariant = Color(0xFFE1E4DB),
    onSurfaceVariant = Color(0xFF45483C),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5ED),
    surfaceContainer = Color(0xFFEDF0E6),
    surfaceContainerHigh = Color(0xFFE7EAE0),
    surfaceContainerHighest = Color(0xFFE1E4DB),

    outline = Color(0xFF75796A),
    outlineVariant = Color(0xFFC5C9BA),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    inverseSurface = Color(0xFF2F312C),
    inverseOnSurface = Color(0xFFF1F1EA),
    inversePrimary = Color(0xFFD5FF00),
    scrim = Color(0xFF000000),
)
