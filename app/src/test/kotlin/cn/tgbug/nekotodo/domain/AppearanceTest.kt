package cn.tgbug.nekotodo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {

    @Test
    fun `跟随系统时按系统深浅决定`() {
        val appearance = Appearance(themeMode = ThemeMode.SYSTEM)

        assertTrue(appearance.isDark(systemInDark = true))
        assertFalse(appearance.isDark(systemInDark = false))
    }

    @Test
    fun `显式指定时忽略系统设置`() {
        assertFalse(Appearance(themeMode = ThemeMode.LIGHT).isDark(systemInDark = true))
        assertTrue(Appearance(themeMode = ThemeMode.DARK).isDark(systemInDark = false))
    }

    @Test
    fun `选了自定义但没有图片文件时回退到内置背景`() {
        val broken = Appearance(
            background = BackgroundOption.CUSTOM,
            customBackgroundPath = null,
        )
        assertEquals(BackgroundOption.HOSHINO, broken.effectiveBackground)

        val blankPath = Appearance(background = BackgroundOption.CUSTOM, customBackgroundPath = "  ")
        assertEquals(BackgroundOption.HOSHINO, blankPath.effectiveBackground)
    }

    @Test
    fun `有图片文件时就用自定义背景`() {
        val custom = Appearance(
            background = BackgroundOption.CUSTOM,
            customBackgroundPath = "/data/user/0/cn.tgbug.nekotodo/files/backgrounds/custom.jpg",
        )
        assertEquals(BackgroundOption.CUSTOM, custom.effectiveBackground)
    }

    @Test
    fun `无背景与内置素材不受回退影响`() {
        assertEquals(
            BackgroundOption.NONE,
            Appearance(background = BackgroundOption.NONE).effectiveBackground,
        )
        assertEquals(
            BackgroundOption.KOHARU,
            Appearance(background = BackgroundOption.KOHARU).effectiveBackground,
        )
    }

    @Test
    fun `存储值的解析对脏数据有兜底`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("NOPE"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))

        assertEquals(BackgroundOption.HOSHINO, BackgroundOption.fromId(null))
        assertEquals(BackgroundOption.HOSHINO, BackgroundOption.fromId("nope"))
        assertEquals(BackgroundOption.NONE, BackgroundOption.fromId("none"))
    }

    @Test
    fun `默认外观就是 Web 的 Hoshino 观感`() {
        val default = Appearance()

        assertEquals(ThemeMode.SYSTEM, default.themeMode)
        assertEquals(Palette.DEFAULT_ACCENT, default.accent)
        assertEquals(BackgroundOption.HOSHINO, default.background)
    }
}
