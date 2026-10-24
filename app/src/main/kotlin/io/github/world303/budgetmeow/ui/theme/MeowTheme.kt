package io.github.world303.budgetmeow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import io.github.world303.budgetmeow.core.model.AppTheme
import io.github.world303.budgetmeow.core.model.MeowSettings
import io.github.world303.budgetmeow.core.model.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import java.time.LocalTime

/** 记账语义色。Miuix 色板只有 primary/error，收入/支出/省钱需要自己定 */
object MeowPalette {
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val Blue = Color(0xFF007AFF)
    val Orange = Color(0xFFFF9500)
    val Purple = Color(0xFFAF52DE)
    private val PinkSeed = Color(0xFFE91E63)
}

fun parseClock(text: String): LocalTime? =
    runCatching { LocalTime.parse(text.trim()) }.getOrNull()

/**
 * 定时深色：start > end 视为跨夜（22:00 → 07:00）。
 */
fun isScheduledDarkNow(settings: MeowSettings, now: LocalTime = LocalTime.now()): Boolean {
    val start = parseClock(settings.darkStartTime) ?: LocalTime.of(22, 0)
    val end = parseClock(settings.darkEndTime) ?: LocalTime.of(7, 0)
    return if (start <= end) {
        !now.isBefore(start) && now.isBefore(end)
    } else {
        !now.isBefore(start) || now.isBefore(end)
    }
}

private fun boldTextStyles(): TextStyles {
    val base = defaultTextStyles()
    return defaultTextStyles(
        main = base.main.copy(fontWeight = FontWeight.Medium),
        paragraph = base.paragraph.copy(fontWeight = FontWeight.Medium),
        body1 = base.body1.copy(fontWeight = FontWeight.Medium),
        body2 = base.body2.copy(fontWeight = FontWeight.Medium),
        button = base.button.copy(fontWeight = FontWeight.SemiBold),
        footnote1 = base.footnote1.copy(fontWeight = FontWeight.Medium),
        footnote2 = base.footnote2.copy(fontWeight = FontWeight.Medium),
        headline1 = base.headline1.copy(fontWeight = FontWeight.SemiBold),
        headline2 = base.headline2.copy(fontWeight = FontWeight.SemiBold),
        subtitle = base.subtitle.copy(fontWeight = FontWeight.Medium),
        title1 = base.title1.copy(fontWeight = FontWeight.Bold),
        title2 = base.title2.copy(fontWeight = FontWeight.Bold),
        title3 = base.title3.copy(fontWeight = FontWeight.SemiBold),
        title4 = base.title4.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun MeowTheme(
    settings: MeowSettings,
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = darkOverride ?: when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Scheduled -> isScheduledDarkNow(settings)
    }

    val controller = remember(dark, settings.appTheme) {
        if (settings.appTheme == AppTheme.Pink) {
            // 粉色主题走 Monet 播种配色，keyColor 定成粉，深浅各自生成
            ThemeController(
                colorSchemeMode = if (dark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                keyColor = MeowPalette.PinkSeed,
                paletteStyle = ThemePaletteStyle.TonalSpot,
            )
        } else {
            ThemeController(colorSchemeMode = if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        }
    }

    MiuixTheme(
        controller = controller,
        textStyles = if (settings.boldText) remember { boldTextStyles() } else remember { defaultTextStyles() },
        content = content,
    )
}
