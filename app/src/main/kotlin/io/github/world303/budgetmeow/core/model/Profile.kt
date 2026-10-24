package io.github.world303.budgetmeow.core.model

import io.github.world303.budgetmeow.core.storage.ProfileDto
import io.github.world303.budgetmeow.core.storage.SettingsDto

/** 深色模式策略 */
enum class ThemeMode(val wire: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    Scheduled("scheduled"),
    ;

    companion object {
        fun fromWire(wire: String): ThemeMode = entries.firstOrNull { it.wire == wire } ?: System
    }
}

/** 整体配色：默认（蓝） / 粉色 */
enum class AppTheme(val wire: String) {
    Default("default"),
    Pink("pink"),
    ;

    companion object {
        fun fromWire(wire: String): AppTheme = entries.firstOrNull { it.wire == wire } ?: Default
    }
}

/** 顶栏标题显示什么 */
enum class HeaderDisplayMode(val wire: String) {
    Nickname("nickname"),
    Default("default"),
    Custom("custom"),
    Meow("meow"),
    ;

    companion object {
        fun fromWire(wire: String): HeaderDisplayMode =
            entries.firstOrNull { it.wire == wire } ?: Default
    }
}

data class MeowSettings(
    val theme: ThemeMode = ThemeMode.System,
    val scheduledDarkMode: Boolean = false,
    val darkStartTime: String = "22:00",
    val darkEndTime: String = "07:00",
    val hasSetScheduleTime: Boolean = false,
    val showSaved: Boolean = true,
    val boldText: Boolean = false,
    val headerDisplayMode: HeaderDisplayMode = HeaderDisplayMode.Default,
    val headerCustomText: String = "",
    val appTheme: AppTheme = AppTheme.Default,
    val glassEffect: Boolean = false,
    val showAvatarAsSettingsIcon: Boolean = false,
    val noKeepLogin: Boolean = false,
    val wallpaperPath: String? = null,
)

data class Profile(
    val id: String,
    val nickname: String = "账本主人",
    val avatarPath: String? = null,
    val createdAt: String = TimeUtil.nowIso(),
    val settings: MeowSettings = MeowSettings(),
)

fun SettingsDto.toDomain(): MeowSettings = MeowSettings(
    theme = ThemeMode.fromWire(theme),
    scheduledDarkMode = scheduledDarkMode,
    darkStartTime = darkStartTime,
    darkEndTime = darkEndTime,
    hasSetScheduleTime = hasSetScheduleTime,
    showSaved = showSaved,
    boldText = boldText,
    headerDisplayMode = HeaderDisplayMode.fromWire(headerDisplayMode),
    headerCustomText = headerCustomText,
    appTheme = AppTheme.fromWire(appTheme),
    glassEffect = glassEffect,
    showAvatarAsSettingsIcon = showAvatarAsSettingsIcon,
    noKeepLogin = noKeepLogin,
    wallpaperPath = wallpaperPath,
)

fun MeowSettings.toDto(): SettingsDto = SettingsDto(
    theme = theme.wire,
    scheduledDarkMode = scheduledDarkMode,
    darkStartTime = darkStartTime,
    darkEndTime = darkEndTime,
    hasSetScheduleTime = hasSetScheduleTime,
    showSaved = showSaved,
    boldText = boldText,
    headerDisplayMode = headerDisplayMode.wire,
    headerCustomText = headerCustomText,
    appTheme = appTheme.wire,
    glassEffect = glassEffect,
    showAvatarAsSettingsIcon = showAvatarAsSettingsIcon,
    noKeepLogin = noKeepLogin,
    wallpaperPath = wallpaperPath,
)

fun ProfileDto.toDomain(): Profile = Profile(
    id = id,
    nickname = nickname,
    avatarPath = avatarPath,
    createdAt = createdAt,
    settings = settings.toDomain(),
)

fun Profile.toDto(): ProfileDto = ProfileDto(
    id = id,
    nickname = nickname,
    avatarPath = avatarPath,
    createdAt = createdAt,
    settings = settings.toDto(),
)
