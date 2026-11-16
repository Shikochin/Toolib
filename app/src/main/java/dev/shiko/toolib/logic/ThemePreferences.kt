package dev.shiko.toolib.logic

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ui.feice.mochi.theme.ColorSchemeMode
import ui.feice.mochi.theme.DefaultKeyColor
import ui.feice.mochi.theme.PaletteStyle

data class ThemeSettings(
    val mode: ColorSchemeMode = ColorSchemeMode.System,
    val accentColor: Color = DefaultKeyColor,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val predictiveBack: Boolean = true,
    val pageScale: Float = 1f,
    val hdr: Boolean = false,
)

class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("theme_preferences", Context.MODE_PRIVATE)

    fun load(): ThemeSettings = ThemeSettings(
        mode = runCatching {
            ColorSchemeMode.valueOf(preferences.getString(KEY_MODE, null) ?: ColorSchemeMode.System.name)
        }.getOrDefault(ColorSchemeMode.System),
        accentColor = Color(preferences.getInt(KEY_ACCENT, DefaultKeyColor.toArgb())),
        paletteStyle = runCatching {
            PaletteStyle.valueOf(preferences.getString(KEY_STYLE, null) ?: PaletteStyle.TonalSpot.name)
        }.getOrDefault(PaletteStyle.TonalSpot),
        predictiveBack = preferences.getBoolean(KEY_PREDICTIVE_BACK, true),
        pageScale = preferences.getInt(KEY_PAGE_SCALE, 100).div(100f).coerceIn(0.8f, 1.1f),
        hdr = preferences.getBoolean(KEY_HDR, false),
    )

    fun save(settings: ThemeSettings) {
        preferences.edit()
            .putString(KEY_MODE, settings.mode.name)
            .putInt(KEY_ACCENT, settings.accentColor.toArgb())
            .putString(KEY_STYLE, settings.paletteStyle.name)
            .putBoolean(KEY_PREDICTIVE_BACK, settings.predictiveBack)
            .putInt(KEY_PAGE_SCALE, (settings.pageScale.coerceIn(0.8f, 1.1f) * 100f).toInt())
            .putBoolean(KEY_HDR, settings.hdr)
            .apply()
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_ACCENT = "accent"
        const val KEY_STYLE = "style"
        const val KEY_PREDICTIVE_BACK = "predictive_back"
        const val KEY_PAGE_SCALE = "page_scale"
        const val KEY_HDR = "hdr"
    }
}
