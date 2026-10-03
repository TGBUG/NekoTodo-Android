package cn.tgbug.nekotodo.ui.appearance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.tgbug.nekotodo.domain.BackgroundOption
import cn.tgbug.nekotodo.domain.Palette
import cn.tgbug.nekotodo.domain.ThemeMode

private val PRESET_ACCENTS = listOf(
    0xFF6C77DA.toInt(), // Hoshino 默认(蓝紫)
    0xFF97A0F0.toInt(), // Hoshino 深色模式的那一支
    0xFF3C9AE0.toInt(),
    0xFF3BA55D.toInt(),
    0xFFE08A3C.toInt(),
    0xFFD9628F.toInt(),
    0xFF9B5DE0.toInt(),
    0xFFE0485E.toInt(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel,
    onBack: () -> Unit,
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()

    // 用 SAF 而不是相册选择器:背景图常是"下载来的壁纸",而相册选择器只认媒体库
    // 索引过的照片/视频,下载目录里的图片未必可见。SAF 同样不需要权限。
    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.pickCustomBackground(uri) }

    // 滑杆用草稿值:拖动时只更新预览,松手才落盘(避免每秒几十次磁盘写)。
    val storedHsl = remember(appearance.accent) { Palette.toHsl(appearance.accent) }
    var hue by remember(storedHsl[0]) { mutableFloatStateOf(storedHsl[0]) }
    var saturation by remember(storedHsl[1]) { mutableFloatStateOf(storedHsl[1]) }
    var lightness by remember(storedHsl[2]) { mutableFloatStateOf(storedHsl[2]) }
    val draftAccent = remember(hue, saturation, lightness) {
        Palette.fromHsl(hue, saturation, lightness)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("主题")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = appearance.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label()) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            SectionTitle("主色")
            Text(
                text = "整套配色由这一个色相推导:表面层是同色相的极浅/极暗色,文字层明度固定," +
                    "所以无论选什么颜色,文字对比度都达标;明度与饱和度会被自动钳制在可用区间。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SliderRow("色相", hue, 0f..359f, onChange = { hue = it }, onFinished = {
                viewModel.setAccent(Palette.fromHsl(hue, saturation, lightness))
            })
            SliderRow("饱和度", saturation, 0f..1f, onChange = { saturation = it }, onFinished = {
                viewModel.setAccent(Palette.fromHsl(hue, saturation, lightness))
            })
            SliderRow("明度", lightness, 0f..1f, onChange = { lightness = it }, onFinished = {
                viewModel.setAccent(Palette.fromHsl(hue, saturation, lightness))
            })

            PalettePreview(draftAccent, appearance.isDark(systemDark))

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PRESET_ACCENTS.forEach { preset ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(preset), CircleShape)
                            .clickable {
                                viewModel.setAccent(preset)
                                val hsl = Palette.toHsl(preset)
                                hue = hsl[0]
                                saturation = hsl[1]
                                lightness = hsl[2]
                            },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            SectionTitle("背景")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackgroundOption.entries.forEach { option ->
                    FilterChip(
                        selected = appearance.background == option,
                        onClick = { viewModel.setBackground(option) },
                        label = { Text(option.label()) },
                    )
                }
            }
            OutlinedButton(
                onClick = { pickBackground.launch(arrayOf("image/*")) },
            ) { Text("选择背景图片") }
            Text(
                text = "自定义图片会被降采样并模糊后存在本机(玻璃观感需要模糊底图," +
                    "而 Compose 没有 Web 的 backdrop-filter)。外观设置只存本机,不随账号同步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 预览用:按推导结果给出一组色块,让用户看到"表面/文字/交互"三层。 */
@Composable
private fun PalettePreview(accent: Int, dark: Boolean) {
    val palette = remember(accent, dark) { Palette.derive(accent, dark) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("推导结果预览", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("表面", palette.bg)
                Swatch("面板", palette.panel)
                Swatch("文字", palette.fg)
                Swatch("次要", palette.muted)
                Swatch("边框", palette.border)
                Swatch("交互", palette.accent)
            }
            Surface(
                color = Color(palette.accent),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = "按钮上的字",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(palette.onAccent),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Swatch(label: String, argb: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(argb), MaterialTheme.shapes.small),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun BackgroundOption.label(): String = when (this) {
    BackgroundOption.HOSHINO -> "Hoshino"
    BackgroundOption.KOHARU -> "Koharu"
    BackgroundOption.CUSTOM -> "自定义"
    BackgroundOption.NONE -> "无"
}
