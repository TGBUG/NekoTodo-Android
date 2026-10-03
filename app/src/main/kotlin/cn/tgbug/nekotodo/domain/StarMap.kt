package cn.tgbug.nekotodo.domain

import cn.tgbug.nekotodo.data.FileKind
import cn.tgbug.nekotodo.data.SourceInfoDetail
import cn.tgbug.nekotodo.data.TaskStatus
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 星图的几何与视口计算(纯函数,不依赖 Android)。
 *
 * 对应 Web 端 index.html 里的 MapView:源信息是"星",按网格铺开;条目绕星排成圆环;
 * 任务再绕各自条目铺开。这里只算坐标与命中,绘制与手势交给界面层。
 */

enum class MapNodeKind { STAR, ITEM, TASK }

data class MapNode(
    val kind: MapNodeKind,
    val id: Long,
    val x: Float,
    val y: Float,
    val radius: Float,
    /** 画在节点下方的短标签(Web 端按类型截断到 8/10/12 个字)。 */
    val label: String,
    val fullLabel: String,
    /** 图片条目才会带,用于把图片裁圆画进节点里。 */
    val imageUuid: String? = null,
    val completed: Boolean = false,
    /** 任务节点所属的条目 id —— 高亮条目时要把它的任务一起点亮。 */
    val itemId: Long? = null,
)

data class MapEdge(val from: Int, val to: Int)

data class StarMap(
    val nodes: List<MapNode> = emptyList(),
    val edges: List<MapEdge> = emptyList(),
) {
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun nodeById(id: Long): MapNode? = nodes.firstOrNull { it.id == id }

    /**
     * 命中测试:从后往前找(后画的在上层),容差取 max(r, 14) —— 与 Web 端一致,
     * 否则小组件很难点中。
     */
    fun hitTest(x: Float, y: Float): MapNode? {
        for (index in nodes.indices.reversed()) {
            val node = nodes[index]
            val reach = max(node.radius, HIT_TOLERANCE)
            val dx = x - node.x
            val dy = y - node.y
            if (dx * dx + dy * dy <= reach * reach) return node
        }
        return null
    }

    companion object {
        const val HIT_TOLERANCE = 14f
    }
}

object StarMapLayout {

    // 与 Web 端相同的常量,改这里就等于改观感。
    private const val CELL = 420f
    private const val STAR_RADIUS = 30f
    private const val ITEM_ORBIT = 130f
    private const val ITEM_RADIUS = 18f
    private const val IMAGE_ITEM_RADIUS = 38f
    private const val TASK_ORBIT = 85f
    private const val TASK_RADIUS = 11f
    private const val TASK_SPREAD = 0.9f

    fun build(details: List<SourceInfoDetail>): StarMap {
        if (details.isEmpty()) return StarMap()

        val nodes = mutableListOf<MapNode>()
        val edges = mutableListOf<MapEdge>()
        val columns = max(1, ceil(sqrt(details.size.toFloat())).toInt())

        details.forEachIndexed { sourceIndex, detail ->
            val centerX = (sourceIndex % columns) * CELL + CELL / 2f
            val centerY = (sourceIndex / columns) * CELL + CELL / 2f
            val starIndex = nodes.size
            nodes += MapNode(
                kind = MapNodeKind.STAR,
                id = detail.id,
                x = centerX,
                y = centerY,
                radius = STAR_RADIUS,
                label = detail.content.take(12).ifBlank { "(图片)" },
                fullLabel = detail.content.ifBlank { "(图片源信息)" },
            )

            val items = detail.sourceItems
            val tasksByItem = detail.tasks
                .filter { it.sourceItemId != null }
                .groupBy { it.sourceItemId }

            items.forEachIndexed { itemIndex, item ->
                // 条目绕星均匀分布,起点在正上方(与 Web 端一致)。
                val angle = itemIndex.toFloat() / max(1, items.size) * TWO_PI - HALF_PI
                val itemX = centerX + cos(angle) * ITEM_ORBIT
                val itemY = centerY + sin(angle) * ITEM_ORBIT

                val file = detail.sourceFiles.firstOrNull { it.id == item.sourceFileId }
                val isImage = file?.kind == FileKind.IMAGE
                val itemNodeIndex = nodes.size
                nodes += MapNode(
                    kind = MapNodeKind.ITEM,
                    id = item.id,
                    x = itemX,
                    y = itemY,
                    radius = if (isImage) IMAGE_ITEM_RADIUS else ITEM_RADIUS,
                    label = item.content.take(10),
                    fullLabel = item.content,
                    imageUuid = if (isImage) file?.fileUuid else null,
                )
                edges += MapEdge(starIndex, itemNodeIndex)

                val tasks = tasksByItem[item.id].orEmpty()
                tasks.forEachIndexed { taskIndex, task ->
                    // 任务沿条目角度两侧铺开;只有一条时不铺(与 Web 端一致)。
                    val spread = if (tasks.size == 1) {
                        0f
                    } else {
                        (taskIndex.toFloat() / (tasks.size - 1) - 0.5f) * TASK_SPREAD
                    }
                    val taskAngle = angle + spread
                    nodes += MapNode(
                        kind = MapNodeKind.TASK,
                        id = task.id,
                        x = itemX + cos(taskAngle) * TASK_ORBIT,
                        y = itemY + sin(taskAngle) * TASK_ORBIT,
                        radius = TASK_RADIUS,
                        label = task.description.take(8),
                        fullLabel = task.description,
                        completed = task.status == TaskStatus.COMPLETED,
                        itemId = item.id,
                    )
                    edges += MapEdge(itemNodeIndex, nodes.size - 1)
                }
            }
        }
        return StarMap(nodes = nodes, edges = edges)
    }

    private const val TWO_PI = (2 * PI).toFloat()
    private const val HALF_PI = (PI / 2).toFloat()
}

/**
 * 视口:平移 + 缩放。缩放要围绕锚点(Web 端是光标,这里是双指中心),
 * 公式与 Web 端相同:`offset' = anchor - (anchor - offset) * (k'/k)`。
 */
data class MapViewport(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun panBy(dx: Float, dy: Float) = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)

    fun zoomBy(factor: Float, anchorX: Float, anchorY: Float): MapViewport {
        val next = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (next == scale) return this
        val ratio = next / scale
        return MapViewport(
            scale = next,
            offsetX = anchorX - (anchorX - offsetX) * ratio,
            offsetY = anchorY - (anchorY - offsetY) * ratio,
        )
    }

    /** 世界坐标 → 屏幕坐标。 */
    fun toScreenX(x: Float) = offsetX + x * scale
    fun toScreenY(y: Float) = offsetY + y * scale

    /** 屏幕坐标 → 世界坐标(命中测试前要先换算)。 */
    fun toWorldX(x: Float) = (x - offsetX) / scale
    fun toWorldY(y: Float) = (y - offsetY) / scale

    companion object {
        const val MIN_SCALE = 0.2f
        const val MAX_SCALE = 4f
    }
}
