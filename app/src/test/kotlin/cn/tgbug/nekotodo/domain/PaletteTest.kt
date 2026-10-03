package cn.tgbug.nekotodo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    private val dark = setOf(true, false)

    /** 故意包含接近纯白/纯黑/纯灰的极端选色,护栏要能兜住。 */
    private fun sweep(): List<Int> {
        val colors = mutableListOf<Int>()
        for (hue in 0 until 360 step 30) {
            for (saturation in listOf(0f, 0.15f, 0.5f, 0.9f, 1f)) {
                for (lightness in listOf(0f, 0.05f, 0.3f, 0.5f, 0.7f, 0.95f, 1f)) {
                    colors += Palette.fromHsl(hue.toFloat(), saturation, lightness)
                }
            }
        }
        colors += 0xFFFFFFFF.toInt()
        colors += 0xFF000000.toInt()
        colors += 0xFF808080.toInt()
        return colors
    }

    @Test
    fun `默认色在浅色模式下与 Web 的观感一致`() {
        val palette = Palette.derive(Palette.DEFAULT_ACCENT, dark = false)

        // 主色原样保留(默认值是手动挑的对比度合适的那一个)
        assertEquals(Palette.DEFAULT_ACCENT, palette.accent)
        // 主文字与 Web 的 #3a3f63 同族(深蓝灰),但明度比 Web 略低——
        // 这是为了让暖色 accent 也守住 7:1 刻意做的取舍,所以这里只断言"同族 + 对比度达标"。
        assertTrue("fg 应是深蓝灰,实际 ${palette.fg.toUInt().toString(16)}", isDarkBlueGrey(palette.fg))
        assertTrue("fg/bg 对比度应远高于 7", Palette.contrast(palette.fg, palette.bg) >= 9f)
        // 表面层是同一色相的极浅底色
        assertTrue("bg 应该很亮", Palette.luminance(palette.bg) > 0.8f)
        assertTrue("panel 应比 bg 更亮", Palette.luminance(palette.panel) > Palette.luminance(palette.bg))
    }

    @Test
    fun `深色模式下主色被提亮`() {
        val light = Palette.derive(Palette.DEFAULT_ACCENT, dark = false)
        val darkPalette = Palette.derive(Palette.DEFAULT_ACCENT, dark = true)

        assertTrue(
            "深色模式的 accent 应该比浅色模式更亮",
            Palette.luminance(darkPalette.accent) > Palette.luminance(light.accent),
        )
        assertTrue("深色模式的表面应该很暗", Palette.luminance(darkPalette.bg) < 0.1f)
        assertTrue(
            "深色模式下面板应比背景更亮",
            Palette.luminance(darkPalette.panel) > Palette.luminance(darkPalette.bg),
        )
    }

    @Test
    fun `主文字与表面层的对比度在任何色相下都达标`() {
        for (isDark in dark) {
            for (accent in sweep()) {
                val palette = Palette.derive(accent, isDark)
                val label = "accent=${accent.toUInt().toString(16)} dark=$isDark"

                assertTrue(
                    "fg/bg 对比度不足: $label -> ${Palette.contrast(palette.fg, palette.bg)}",
                    Palette.contrast(palette.fg, palette.bg) >= 7f,
                )
                assertTrue(
                    "fg/panel 对比度不足: $label -> ${Palette.contrast(palette.fg, palette.panel)}",
                    Palette.contrast(palette.fg, palette.panel) >= 7f,
                )
                assertTrue(
                    "accent 上的前景色对比度不足: $label -> ${Palette.contrast(palette.onAccent, palette.accent)}",
                    Palette.contrast(palette.onAccent, palette.accent) >= 4f,
                )
            }
        }
    }

    @Test
    fun `护栏钳制住极端选色`() {
        // 纯白 accent:必须被压暗,否则按钮和浅色表面会糊在一起
        val white = Palette.derive(0xFFFFFFFF.toInt(), dark = false)
        assertTrue("白色 accent 应被钳制到可用的亮度过", Palette.luminance(white.accent) < 0.6f)

        // 纯黑 accent:必须被提亮一点,否则在浅色表面上像一块洞
        val black = Palette.derive(0xFF000000.toInt(), dark = false)
        assertTrue("黑色 accent 应被钳制", Palette.luminance(black.accent) > 0.0f)

        // 灰色 accent:饱和度被抬到最低限,表面层仍带一点色
        val grey = Palette.derive(0xFF808080.toInt(), dark = false)
        val hsl = Palette.toHsl(grey.accent)
        assertTrue("灰色 accent 的饱和度应被抬起", hsl[1] >= 0.19f)
    }

    @Test
    fun `深色模式下按钮前景色在极亮 accent 上仍可读`() {
        val palette = Palette.derive(0xFFFFFFFF.toInt(), dark = true)
        assertTrue(Palette.contrast(palette.onAccent, palette.accent) >= 4f)
    }

    @Test
    fun `HSL 往返不丢色相`() {
        for (hue in 0 until 360 step 17) {
            val argb = Palette.fromHsl(hue.toFloat(), 0.6f, 0.5f)
            val hsl = Palette.toHsl(argb)
            val diff = kotlin.math.abs(hsl[0] - hue)
            assertTrue("色相漂移过大: $hue -> ${hsl[0]}", kotlin.math.min(diff, 360f - diff) <= 1f)
        }
    }

    /** 深蓝灰:亮度很低、蓝通道最高(与 Web 的 #3a3f63 同族)。 */
    private fun isDarkBlueGrey(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return Palette.luminance(argb) < 0.1f && b > r && b > g && r < 120 && g < 120 && b < 140
    }
}
