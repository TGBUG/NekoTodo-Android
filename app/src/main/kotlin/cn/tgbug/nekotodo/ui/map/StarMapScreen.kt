package cn.tgbug.nekotodo.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.domain.MapNode
import cn.tgbug.nekotodo.domain.MapNodeKind
import cn.tgbug.nekotodo.domain.MapViewport
import cn.tgbug.nekotodo.domain.StarMap
import cn.tgbug.nekotodo.domain.StarMapLayout
import cn.tgbug.nekotodo.ui.tasks.TaskFormSheet
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlin.math.roundToInt

/**
 * 星图(源映射模式),对标 Web 端的 canvas 视图:
 * 星=源信息,条目绕星成环,任务绕条目铺开;可平移缩放,点节点进对应页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarMapScreen(
    viewModel: StarMapViewModel,
    onBack: () -> Unit,
    onOpenSource: (Long) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var viewport by remember { mutableStateOf(MapViewport()) }
    var highlightedItemId by remember { mutableStateOf<Long?>(null) }
    var editingTask by remember { mutableStateOf<Task?>(null) }

    val map = remember(state.details) { StarMapLayout.build(state.details) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setPollingActive(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.setPollingActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setPollingActive(false)
        }
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    // 图片条目的位图:在协程里加载,避免在组合期写状态。
    val images = remember { mutableStateMapOf<String, ImageBitmap>() }
    val imageUuids = remember(map) { map.nodes.mapNotNull { it.imageUuid }.distinct() }
    LaunchedEffect(imageUuids) {
        if (imageUuids.isEmpty()) return@LaunchedEffect
        val loader = SingletonImageLoader.get(context)
        imageUuids.forEach { uuid ->
            if (images[uuid] == null) {
                val request = ImageRequest.Builder(context)
                    .data(viewModel.fileUrl(uuid))
                    .build()
                val bitmap = runCatching { loader.execute(request) }.getOrNull()
                    ?.let { (it.image as? BitmapImage)?.bitmap }
                if (bitmap != null) images[uuid] = bitmap.asImageBitmap()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("源映射") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = { TextButton(onClick = viewModel::refreshNow) { Text("刷新") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.error != null -> androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = viewModel::refreshNow,
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("重试") }
                }

                map.isEmpty -> Text(
                    text = "还没有源信息。去 AI 创建提交一次,拆解出的条目会出现在这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

                else -> {
                    StarMapCanvas(
                        map = map,
                        viewport = viewport,
                        onViewportChange = { viewport = it },
                        images = images,
                        highlightedItemId = highlightedItemId,
                        onTapNode = { node ->
                            when (node.kind) {
                                MapNodeKind.STAR -> onOpenSource(node.id)
                                MapNodeKind.ITEM -> {
                                    highlightedItemId =
                                        if (highlightedItemId == node.id) null else node.id
                                }

                                MapNodeKind.TASK -> editingTask =
                                    state.allTasks.firstOrNull { it.id == node.id }
                            }
                        },
                    )
                    Text(
                        text = "拖动平移、双指缩放;点星看源信息,点条目高亮,点任务编辑",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                    )
                }
            }
        }
    }

    editingTask?.let { task ->
        TaskFormSheet(
            editing = task,
            focusField = null,
            categorySuggestions = state.categoryNames,
            zone = state.zone,
            onDismiss = { editingTask = null },
            onSubmit = { description, details, deadlineIso, category, onResult ->
                viewModel.submitEdit(
                    taskId = task.id,
                    description = description,
                    details = details,
                    deadlineIso = deadlineIso,
                    category = category,
                ) { error ->
                    onResult(error)
                    if (error == null) editingTask = null
                }
            },
        )
    }
}

@Composable
private fun StarMapCanvas(
    map: StarMap,
    viewport: MapViewport,
    onViewportChange: (MapViewport) -> Unit,
    images: Map<String, ImageBitmap>,
    highlightedItemId: Long?,
    onTapNode: (MapNode) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val border = MaterialTheme.colorScheme.outline
    val panel = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val ok = MaterialTheme.colorScheme.tertiary
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(textColor) { TextStyle(fontSize = 11.sp, color = textColor) }

    // pointerInput(Unit) 的 lambda 只在首次组合时创建一次。若直接捕获视口/星图这两个
    // 参数,它们会一直停在初始值:拖动时位移永远只等于最后一个事件的增量(看着像没动),
    // 平移之后点击也会拿错误的变换做命中测试。所以必须经由 State 读最新值。
    val currentViewport = rememberUpdatedState(viewport)
    val currentMap = rememberUpdatedState(map)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val vp = currentViewport.value
                    val node = currentMap.value.hitTest(
                        vp.toWorldX(offset.x),
                        vp.toWorldY(offset.y),
                    )
                    if (node != null) onTapNode(node)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val vp = currentViewport.value
                    onViewportChange(vp.zoomBy(zoom, centroid.x, centroid.y).panBy(pan.x, pan.y))
                }
            },
    ) {
        withTransform({
            translate(viewport.offsetX, viewport.offsetY)
            scale(viewport.scale, viewport.scale, pivot = Offset.Zero)
        }) {
            map.edges.forEach { edge ->
                val from = map.nodes[edge.from]
                val to = map.nodes[edge.to]
                drawLine(
                    color = border,
                    start = Offset(from.x, from.y),
                    end = Offset(to.x, to.y),
                    strokeWidth = 1f / viewport.scale,
                )
            }
            map.nodes.forEach { node ->
                val highlighted = node.id == highlightedItemId || node.itemId == highlightedItemId
                drawNode(node, highlighted, accent, border, panel, muted, ok, images)
            }
        }

        // 标签放在缩放坐标系之外:字号不随缩放变化(Web 端也是 11/k)。
        map.nodes.forEach { node ->
            val layout = textMeasurer.measure(AnnotatedString(node.label), labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    x = viewport.toScreenX(node.x) - layout.size.width / 2f,
                    y = viewport.toScreenY(node.y + node.radius) + 14f,
                ),
            )
        }
    }
}

private fun DrawScope.drawNode(
    node: MapNode,
    highlighted: Boolean,
    accent: Color,
    border: Color,
    panel: Color,
    muted: Color,
    ok: Color,
    images: Map<String, ImageBitmap>,
) {
    val center = Offset(node.x, node.y)
    when (node.kind) {
        MapNodeKind.STAR -> {
            // 外圈光晕 + 实心核心(与 Web 端一致)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent, Color.Transparent),
                    center = Offset(node.x - 8f, node.y - 8f),
                    radius = node.radius + 14f,
                ),
                radius = node.radius + 14f,
                center = center,
            )
            drawCircle(color = accent, radius = node.radius * 0.55f, center = center)
        }

        MapNodeKind.ITEM -> {
            val bitmap = node.imageUuid?.let { images[it] }
            if (bitmap != null) {
                val path = Path().apply { addOval(Rect(center = center, radius = node.radius)) }
                clipPath(path) {
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(
                            (node.x - node.radius).roundToInt(),
                            (node.y - node.radius).roundToInt(),
                        ),
                        dstSize = IntSize(
                            (node.radius * 2f).roundToInt(),
                            (node.radius * 2f).roundToInt(),
                        ),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                drawCircle(
                    color = if (highlighted) accent else border,
                    radius = node.radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (highlighted) 3f else 1.5f,
                    ),
                )
            } else {
                drawCircle(color = panel, radius = node.radius, center = center)
                drawCircle(
                    color = if (highlighted) accent else muted,
                    radius = node.radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (highlighted) 3f else 1.5f,
                    ),
                )
            }
            if (highlighted) {
                drawCircle(
                    color = accent,
                    radius = node.radius + 6f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }
        }

        MapNodeKind.TASK -> {
            drawCircle(
                color = if (node.completed) ok.copy(alpha = 0.55f) else accent,
                radius = node.radius,
                center = center,
            )
            if (highlighted) {
                drawCircle(
                    color = accent,
                    radius = node.radius + 4f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }
        }
    }
}
