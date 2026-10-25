package io.github.thomasvanriel.solitaire.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * A calm, warm look: muted sage felt, linen-coloured cards and buttons in soft wood and cream tones,
 * instead of Material's bright defaults. Nothing very saturated, nothing pure black or pure white.
 */

/** The table: a muted sage green, and a deeper, quieter one for dark mode. */
val Felt = Color(0xFF4A6B5A)
val FeltDark = Color(0xFF223229)

/** Text and icons drawn straight on the felt. */
val OnFelt = Color(0xFFF4EFE4)

/** Notices and banners floating over the board. */
val Bubble = Color(0xEB2E2A25)

/** How long the gentle fades and slides of pop-ups, banners and the win screen take. */
const val SOFT_FADE_MS = 600

fun cosyColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(0xFFD9BE98),
        onPrimary = Color(0xFF3B2D1C),
        primaryContainer = Color(0xFF574631),
        onPrimaryContainer = Color(0xFFF3E3CB),
        secondary = Color(0xFFC9C0AE),
        onSecondary = Color(0xFF333026),
        secondaryContainer = Color(0xFF4A4337),
        onSecondaryContainer = Color(0xFFEDE3D1),
        tertiary = Color(0xFFA9C8B4),
        onTertiary = Color(0xFF1E3428),
        background = Color(0xFF221F1B),
        onBackground = Color(0xFFEAE3D6),
        surface = Color(0xFF221F1B),
        onSurface = Color(0xFFEAE3D6),
        surfaceVariant = Color(0xFF4A453D),
        onSurfaceVariant = Color(0xFFCDC4B5),
        surfaceContainerLowest = Color(0xFF1C1A16),
        surfaceContainerLow = Color(0xFF26231F),
        surfaceContainer = Color(0xFF2B2823),
        surfaceContainerHigh = Color(0xFF322E29),
        surfaceContainerHighest = Color(0xFF3B3731),
        outline = Color(0xFF968E80),
        outlineVariant = Color(0xFF4A453D),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF7E6446),
        onPrimary = Color(0xFFFFFAF2),
        primaryContainer = Color(0xFFF2E2C9),
        onPrimaryContainer = Color(0xFF3B2D1C),
        secondary = Color(0xFF6E6656),
        onSecondary = Color(0xFFFFFAF2),
        secondaryContainer = Color(0xFFEFE6D4),
        onSecondaryContainer = Color(0xFF3F3727),
        tertiary = Color(0xFF4F6F5C),
        onTertiary = Color(0xFFFFFAF2),
        background = Color(0xFFFBF7EF),
        onBackground = Color(0xFF34302A),
        surface = Color(0xFFFBF7EF),
        onSurface = Color(0xFF34302A),
        surfaceVariant = Color(0xFFEAE2D4),
        onSurfaceVariant = Color(0xFF5E574B),
        surfaceContainerLowest = Color(0xFFFFFDF8),
        surfaceContainerLow = Color(0xFFF8F3EA),
        surfaceContainer = Color(0xFFF4EEE3),
        surfaceContainerHigh = Color(0xFFF1EADD),
        surfaceContainerHighest = Color(0xFFEBE3D5),
        outline = Color(0xFF8C8475),
        outlineVariant = Color(0xFFD5CCBC),
    )
}
