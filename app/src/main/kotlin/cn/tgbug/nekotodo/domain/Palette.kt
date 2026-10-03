package cn.tgbug.nekotodo.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 从用户选的一个 accent 推出整套调色板。
 *
 * 依据:Web 端那 9 个 CSS 变量整体偏向同一个色相——
 *  - 表面层(背景/面板/边框):同色相、高饱和但明度极高(浅色模式)或明度极低(深色模式),再叠透明度;
 *  - 文字层(主要/次要):同色相,但**明度是固定的**,与色相无关;
 *  - 交互色:饱和的那一支。
 *
 * 因此只给 accent 就能推出整盘。两条护栏:
 *  1. accent 的明度/饱和度先被钳制到可用区间,避免用户选到接近纯白/纯黑导致表面层塌掉;
 *  2. 文字层的明度固定 → **主文字的对比度由构造保证**,不会因为用户选了怪色相而变差;
 *     按钮上的前景色则在"白字"与"深墨字"里挑对比度更高的那个。
 */
data class NekoPalette(
    val isDark: Boolean,
    val accent: Int,
    val onAccent: Int,
    val accentSoft: Int,
    val bg: Int,
    val panel: Int,
    val fg: Int,
    val muted: Int,
    val border: Int,
    val danger: Int,
    val ok: Int,
) {
    /**
     * 表面层是半透明的(叠在磨砂背景上)。
     * 底色遮罩取得比 Web 的 0.30 略高:Web 上文字几乎都落在面板里,而手机上有一些
     * 次要文字(分组标题、空状态提示)直接压在背景上,遮罩薄了会读不清。
     */
    val bgAlpha: Float get() = if (isDark) 0.42f else 0.40f
    val panelAlpha: Float get() = if (isDark) 0.55f else 0.52f
    val borderAlpha: Float get() = if (isDark) 0.25f else 0.45f
    val accentSoftAlpha: Float get() = 0.16f
}

object Palette {

    /** 与 Web 端 Hoshino 主题一致的主色。 */
    const val DEFAULT_ACCENT: Int = 0xFF6C77DA.toInt()

    private const val WHITE = 0xFFFFFFFF.toInt()

    fun derive(accentArgb: Int, dark: Boolean): NekoPalette {
        val hsl = toHsl(accentArgb)
        val hue = hsl[0]
        val rawSaturation = hsl[1]
        val rawLightness = hsl[2]

        // ---- 护栏 1:把 accent 钳制到"能当按钮底色、也能与表面层区分开"的区间 ----
        val saturation: Float
        val lightness: Float
        if (dark) {
            // 深色模式把 accent 往亮处提,让它在深表面上仍是"亮的那一支"。
            saturation = rawSaturation.coerceIn(0.25f, 0.95f).coerceAtLeast(0.55f)
            lightness = rawLightness.coerceIn(0.62f, 0.88f).coerceAtLeast(0.76f)
        } else {
            saturation = rawSaturation.coerceIn(0.20f, 0.95f)
            lightness = rawLightness.coerceIn(0.28f, 0.66f)
        }
        val accent = fromHsl(hue, saturation, lightness)

        // 表面层的饱和度:
        //  - 浅色模式:极浅的底色需要较高的饱和才看得出色相(Web 的 --bg 就是 100% 饱和度的极浅色);
        //  - 深色模式:必须**低饱和**。Web 的深色背景饱和度只有约 0.20;若这里跟着 accent 放大,
        //    黄色一类天生高亮度的色相会把暗表面抬亮,主文字对比度会被吃掉(这条是扫色测试发现的)。
        val surfaceSaturation = if (dark) {
            (saturation * 0.35f).coerceIn(0.12f, 0.45f)
        } else {
            (saturation * 1.7f).coerceIn(0.40f, 1f)
        }
        val bg = if (dark) fromHsl(hue, surfaceSaturation, 0.18f) else fromHsl(hue, surfaceSaturation, 0.96f)
        val panel = if (dark) fromHsl(hue, surfaceSaturation, 0.22f) else fromHsl(hue, surfaceSaturation, 0.99f)

        // ---- 护栏 2:文字层明度固定,对比度与色相无关 ----
        // 饱和度上限压得比较低:暖色(橙/黄)在相同的 HSL 明度下亮度天生更高,
        // 若让文字保持高饱和,暖色 accent 的文字在浅表面上会变"发亮",对比度被吃掉。
        // 压低饱和既拉回亮度,也更耐看。这条同样是扫色属性测试逼出来的。
        val textSaturation = if (dark) {
            (saturation * 0.6f).coerceIn(0.10f, 0.50f)
        } else {
            (saturation * 0.43f).coerceIn(0.08f, 0.28f)
        }
        val fg = if (dark) fromHsl(hue, textSaturation, 0.94f) else fromHsl(hue, textSaturation, 0.27f)
        // 次要文字比 Web 的 62% 略深:它常直接压在磨砂背景上,太浅会读不清。
        val muted = if (dark) fromHsl(hue, textSaturation, 0.74f) else fromHsl(hue, textSaturation, 0.56f)

        val borderSaturation = (saturation * 0.9f).coerceIn(0.15f, 0.90f)
        val border = if (dark) {
            fromHsl(hue, borderSaturation, 0.82f)
        } else {
            fromHsl(hue, borderSaturation, 0.735f)
        }

        // 按钮上的字:在白与深墨之间挑对比度更高的那个,保证任何 accent 都读得清。
        val ink = fromHsl(hue, 0.30f, 0.08f)
        val onAccent = if (contrast(WHITE, accent) >= contrast(ink, accent)) WHITE else ink

        val danger = if (dark) 0xFFFF7A86.toInt() else 0xFFE0485E.toInt()
        val ok = if (dark) 0xFF55C97B.toInt() else 0xFF3BA55D.toInt()

        return NekoPalette(
            isDark = dark,
            accent = accent,
            onAccent = onAccent,
            accentSoft = accent,
            bg = bg,
            panel = panel,
            fg = fg,
            muted = muted,
            border = border,
            danger = danger,
            ok = ok,
        )
    }

    // ---- HSL 与对比度:纯计算,便于单测 ----

    /** 返回 [hue(0..360), saturation(0..1), lightness(0..1)]。 */
    internal fun toHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val lightness = (max + min) / 2f
        val delta = max - min
        val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
        val rawHue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val hue = ((rawHue % 360f) + 360f) % 360f
        return floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness)
    }

    internal fun fromHsl(hue: Float, saturation: Float, lightness: Float): Int {
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (((hue % 360f) + 360f) % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        fun channel(value: Float) = ((value + m).coerceIn(0f, 1f) * 255f).roundToInt()
        return (0xFF shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
    }

    private fun linearize(channel: Int): Float {
        val v = channel / 255f
        return if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    /** WCAG 相对亮度(忽略 alpha)。 */
    fun luminance(argb: Int): Float =
        0.2126f * linearize((argb shr 16) and 0xFF) +
            0.7152f * linearize((argb shr 8) and 0xFF) +
            0.0722f * linearize(argb and 0xFF)

    /** WCAG 对比度,1.0 ~ 21.0。 */
    fun contrast(a: Int, b: Int): Float {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = max(la, lb)
        val lo = min(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }
}
