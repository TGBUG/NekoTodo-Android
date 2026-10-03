package cn.tgbug.nekotodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import cn.tgbug.nekotodo.R
import cn.tgbug.nekotodo.domain.Appearance
import cn.tgbug.nekotodo.domain.BackgroundOption
import cn.tgbug.nekotodo.domain.NekoPalette
import cn.tgbug.nekotodo.domain.Palette
import coil3.compose.AsyncImage
import java.io.File

/**
 * 把推导出来的调色板映射到 Material3 的角色上。
 *
 * 两件事必须一起做,否则会出问题:
 *  1. 表面色带透明度(与 Web 的 `rgba(...)` 面板一致),于是卡片与顶栏会半透明地叠在
 *     磨砂背景层上——这就是"磨砂玻璃"在 Compose 里的做法(Compose 没有 Web 的
 *     `backdrop-filter`,改成整张背景图预先模糊 + 面板半透明);
 *  2. **连带着把其余角色也一起推导**。只覆盖 primary/background 的话,其余角色会退回
 *     Material 的基线紫,换个主色就不协调——已选中的 FilterChip、底部面板、分割线
 *     都属于这一类。`surfaceTint` 置为透明,避免高度叠加再给半透明表面上色。
 */
private fun NekoPalette.toColorScheme(): ColorScheme {
    val panelSurface = Color(panel).copy(alpha = panelAlpha)
    val background = Color(bg).copy(alpha = bgAlpha)
    // 弹层(底部面板、对话框)要比卡片更实一些:一是聚焦,二是半透明容器里的阴影会透出来。
    val container = Color(panel).copy(alpha = (panelAlpha + 0.30f).coerceAtMost(0.92f))
    val softAccent = Color(accentSoft).copy(alpha = accentSoftAlpha)

    return if (isDark) {
        darkColorScheme(
            primary = Color(accent),
            onPrimary = Color(onAccent),
            primaryContainer = softAccent,
            onPrimaryContainer = Color(fg),
            secondary = Color(accent),
            onSecondary = Color(onAccent),
            secondaryContainer = softAccent,
            onSecondaryContainer = Color(fg),
            tertiary = Color(ok),
            tertiaryContainer = Color(ok).copy(alpha = 0.18f),
            background = background,
            onBackground = Color(fg),
            surface = panelSurface,
            onSurface = Color(fg),
            surfaceVariant = panelSurface,
            onSurfaceVariant = Color(muted),
            surfaceContainerLowest = container,
            surfaceContainerLow = container,
            surfaceContainer = container,
            surfaceContainerHigh = container,
            surfaceContainerHighest = container,
            surfaceTint = Color.Transparent,
            outline = Color(border).copy(alpha = borderAlpha),
            outlineVariant = Color(border).copy(alpha = borderAlpha),
            error = Color(danger),
        )
    } else {
        lightColorScheme(
            primary = Color(accent),
            onPrimary = Color(onAccent),
            primaryContainer = softAccent,
            onPrimaryContainer = Color(fg),
            secondary = Color(accent),
            onSecondary = Color(onAccent),
            secondaryContainer = softAccent,
            onSecondaryContainer = Color(fg),
            tertiary = Color(ok),
            tertiaryContainer = Color(ok).copy(alpha = 0.18f),
            background = background,
            onBackground = Color(fg),
            surface = panelSurface,
            onSurface = Color(fg),
            surfaceVariant = panelSurface,
            onSurfaceVariant = Color(muted),
            surfaceContainerLowest = container,
            surfaceContainerLow = container,
            surfaceContainer = container,
            surfaceContainerHigh = container,
            surfaceContainerHighest = container,
            surfaceTint = Color.Transparent,
            outline = Color(border).copy(alpha = borderAlpha),
            outlineVariant = Color(border).copy(alpha = borderAlpha),
            error = Color(danger),
        )
    }
}

@Composable
fun NekoTodoTheme(
    appearance: Appearance,
    content: @Composable () -> Unit,
) {
    val dark = appearance.isDark(isSystemInDarkTheme())
    val palette = remember(appearance.accent, dark) { Palette.derive(appearance.accent, dark) }
    val colorScheme = remember(palette) { palette.toColorScheme() }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * 磨砂背景层:预先模糊过的背景图铺满,内容叠在上面。
 * 面板的半透明由 ColorScheme 的表面色负责,这里只画背景。
 */
@Composable
fun NekoBackground(
    appearance: Appearance,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (appearance.effectiveBackground) {
            BackgroundOption.HOSHINO -> BackgroundImage(R.drawable.back_hoshino)

            BackgroundOption.KOHARU -> BackgroundImage(R.drawable.back_koharu)

            BackgroundOption.CUSTOM -> {
                val path = appearance.customBackgroundPath
                val file = remember(path) { path?.let(::File) }
                if (file != null && file.exists()) {
                    BackgroundImage(file)
                } else {
                    // 用户选的图片被清掉时退回内置素材,不留空白。
                    BackgroundImage(R.drawable.back_hoshino)
                }
            }

            BackgroundOption.NONE -> Unit
        }
        content()
    }
}

@Composable
private fun BackgroundImage(model: Any) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}
