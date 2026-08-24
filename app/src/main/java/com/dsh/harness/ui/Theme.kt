package com.dsh.harness.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Design tokens — the "Harness" identity: ink-navy + brass/copper + instrument white. */
data class HarnessTokens(
    val accent: Color,
    val accentText: Color,
    val accentSoft: Color,   // user bubble background
    val bg: Color,
    val surface: Color,       // cards / top bar
    val surfaceAlt: Color,    // assistant bubble background
    val hairline: Color,
    val text: Color,
    val muted: Color,
    val running: Color
)

fun tokens(isDark: Boolean): HarnessTokens = if (isDark) {
    HarnessTokens(
        accent = Color(0xFF5B7FFF),
        accentText = Color(0xFF8FA6FF),
        accentSoft = Color(0xFF223056),   // user bubble (dark blue)
        bg = Color(0xFF0F1115),
        surface = Color(0xFF15181F),
        surfaceAlt = Color(0xFF1B1F29),   // assistant bubble
        hairline = Color(0xFF262B36),
        text = Color(0xFFE7E9EE),
        muted = Color(0xFF878C9A),
        running = Color(0xFF3DDC84)
    )
} else {
    HarnessTokens(
        accent = Color(0xFF4D6BFE),
        accentText = Color(0xFF3B57E8),
        accentSoft = Color(0xFF4D6BFE),   // user bubble (solid DeepSeek blue)
        bg = Color(0xFFF5F6F8),
        surface = Color(0xFFFFFFFF),
        surfaceAlt = Color(0xFFF0F1F4),   // assistant bubble
        hairline = Color(0xFFE4E6EA),
        text = Color(0xFF1F2329),
        muted = Color(0xFF6B7280),
        running = Color(0xFF1FA55A)
    )
}

fun themeColorScheme(isDark: Boolean): ColorScheme {
    val t = tokens(isDark)
    return if (isDark) {
        darkColorScheme(
            primary = t.accent,
            onPrimary = Color(0xFF241A06),
            background = t.bg,
            onBackground = t.text,
            surface = t.surface,
            onSurface = t.text,
            surfaceVariant = t.surfaceAlt,
            onSurfaceVariant = t.muted
        )
    } else {
        lightColorScheme(
            primary = t.accent,
            onPrimary = Color(0xFFFFFFFF),
            background = t.bg,
            onBackground = t.text,
            surface = t.surface,
            onSurface = t.text,
            surfaceVariant = t.surfaceAlt,
            onSurfaceVariant = t.muted
        )
    }
}

/** Locally-composed token set so any composable can reach the palette. */
val LocalHarness = compositionLocalOf { tokens(true) }

/** Simple persisted theme preference (SYSTEM / LIGHT / DARK). */
object ThemePrefs {
    private const val PREFS = "harness_prefs"
    private const val KEY_THEME = "theme_mode"

    val mode: MutableState<ThemeMode> = mutableStateOf(ThemeMode.SYSTEM)

    fun load(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode.value = runCatching { ThemeMode.valueOf(sp.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    fun save(context: Context, m: ThemeMode) {
        mode.value = m
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_THEME, m.name).apply()
    }
}

@Composable
fun effectiveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
