package cn.tgbug.nekotodo.domain

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** 背景:两套内置素材各来自 Web 端的 Hoshino / Koharu 模板,Default 模板没有背景图,对应 NONE。 */
enum class BackgroundOption(val id: String) {
    HOSHINO("hoshino"),
    KOHARU("koharu"),
    CUSTOM("custom"),
    NONE("none"),
    ;

    companion object {
        fun fromId(value: String?): BackgroundOption =
            entries.firstOrNull { it.id == value } ?: HOSHINO
    }
}

/**
 * 外观设置只存在本地——后端 `/me/preferences` 只有提示词模板与时区两个字段,
 * 没有存外观的地方,所以换手机或重装都要重新调。
 */
data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Int = Palette.DEFAULT_ACCENT,
    val background: BackgroundOption = BackgroundOption.HOSHINO,
    val customBackgroundPath: String? = null,
) {
    fun isDark(systemInDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    /** 选了"自定义"却没有可用的图片文件时退回内置素材,避免整屏空白。 */
    val effectiveBackground: BackgroundOption
        get() = if (background == BackgroundOption.CUSTOM && customBackgroundPath.isNullOrBlank()) {
            BackgroundOption.HOSHINO
        } else {
            background
        }
}
