package cn.tgbug.nekotodo.domain

import cn.tgbug.nekotodo.data.FileKind
import cn.tgbug.nekotodo.data.SourceFile
import cn.tgbug.nekotodo.data.SourceInfoDetail
import cn.tgbug.nekotodo.data.SourceItem
import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class StarMapTest {

    private fun task(id: Long, itemId: Long?, description: String = "任务$id", status: TaskStatus = TaskStatus.INCOMPLETE) =
        Task(
            id = id,
            description = description,
            status = status,
            sourceItemId = itemId,
        )

    private fun item(id: Long, content: String, fileId: Long? = null) =
        SourceItem(id = id, sourceInfoId = 1, content = content, sourceFileId = fileId)

    private fun imageFile(id: Long, uuid: String) = SourceFile(
        id = id,
        sourceInfoId = 1,
        kind = FileKind.IMAGE,
        fileUuid = uuid,
    )

    private fun detail(
        id: Long,
        content: String,
        items: List<SourceItem> = emptyList(),
        tasks: List<Task> = emptyList(),
        files: List<SourceFile> = emptyList(),
    ) = SourceInfoDetail(id = id, content = content, sourceItems = items, tasks = tasks, sourceFiles = files)

    @Test
    fun `没有源信息时星图为空`() {
        assertTrue(StarMapLayout.build(emptyList()).isEmpty)
    }

    @Test
    fun `单个源信息 条目绕星均匀分布且从正上方起`() {
        val map = StarMapLayout.build(
            listOf(
                detail(
                    id = 7,
                    content = "寒假作业清单",
                    items = listOf(item(1, "语文"), item(2, "数学"), item(3, "英语"), item(4, "物理")),
                ),
            ),
        )

        val star = map.nodes.first { it.kind == MapNodeKind.STAR }
        // 一个源信息时网格只有一格,星在格子中心
        assertEquals(210f, star.x, 0.01f)
        assertEquals(210f, star.y, 0.01f)
        assertEquals("寒假作业清单", star.label)

        val items = map.nodes.filter { it.kind == MapNodeKind.ITEM }
        assertEquals(4, items.size)
        // 第一条在正上方(角度 -90°)
        assertEquals(210f, items[0].x, 0.01f)
        assertEquals(80f, items[0].y, 0.01f)
        // 每条到星的距离都是 130
        items.forEach { node ->
            val distance = sqrt((node.x - star.x) * (node.x - star.x) + (node.y - star.y) * (node.y - star.y))
            assertEquals(130f, distance, 0.05f)
        }
        // 相邻条目夹角相等(90°)
        val angles = items.map { kotlin.math.atan2((it.y - star.y).toDouble(), (it.x - star.x).toDouble()) }
        assertEquals(angles[0] - angles[1], angles[1] - angles[2], 1e-4)
    }

    @Test
    fun `图片条目的节点更大并带上图片 uuid`() {
        val map = StarMapLayout.build(
            listOf(
                detail(
                    id = 1,
                    content = "拍照",
                    items = listOf(item(1, "来自照片", fileId = 9), item(2, "纯文本")),
                    files = listOf(imageFile(9, "uuid-9")),
                ),
            ),
        )

        val items = map.nodes.filter { it.kind == MapNodeKind.ITEM }
        assertEquals(38f, items.first { it.id == 1L }.radius, 0.01f)
        assertEquals("uuid-9", items.first { it.id == 1L }.imageUuid)
        assertEquals(18f, items.first { it.id == 2L }.radius, 0.01f)
        assertNull(items.first { it.id == 2L }.imageUuid)
    }

    @Test
    fun `任务挂在所属条目附近并标记完成状态`() {
        val map = StarMapLayout.build(
            listOf(
                detail(
                    id = 1,
                    content = "清单",
                    items = listOf(item(1, "数学")),
                    tasks = listOf(
                        task(11, 1, "做练习册", TaskStatus.COMPLETED),
                        task(12, 1, "复习"),
                        task(13, null, "孤立的任务"), // 没有条目,不该出现在星图上
                    ),
                ),
            ),
        )

        val tasks = map.nodes.filter { it.kind == MapNodeKind.TASK }
        assertEquals(2, tasks.size)
        assertEquals(false, tasks.any { it.id == 13L })

        val item = map.nodes.first { it.kind == MapNodeKind.ITEM }
        tasks.forEach { node ->
            val distance = sqrt((node.x - item.x) * (node.x - item.x) + (node.y - item.y) * (node.y - item.y))
            assertEquals(85f, distance, 0.05f)
            assertEquals(1L, node.itemId)
        }
        assertTrue(tasks.first { it.id == 11L }.completed)
        assertTrue(!tasks.first { it.id == 12L }.completed)
    }

    @Test
    fun `边的数量等于条目数加任务数`() {
        val map = StarMapLayout.build(
            listOf(
                detail(
                    id = 1,
                    content = "A",
                    items = listOf(item(1, "a1"), item(2, "a2")),
                    tasks = listOf(task(11, 1), task(12, 2), task(13, 2)),
                ),
                detail(id = 2, content = "B", items = listOf(item(3, "b1"))),
            ),
        )

        val items = map.nodes.count { it.kind == MapNodeKind.ITEM }
        val tasks = map.nodes.count { it.kind == MapNodeKind.TASK }
        assertEquals(items + tasks, map.edges.size)
        // 每条边两端的下标都必须有效
        assertTrue(map.edges.all { it.from in map.nodes.indices && it.to in map.nodes.indices })
    }

    @Test
    fun `多个源信息按网格排布且不重叠`() {
        val map = StarMapLayout.build(
            (1L..5L).map { detail(id = it, content = "源$it", items = listOf(item(it, "条目$it"))) },
        )

        val stars = map.nodes.filter { it.kind == MapNodeKind.STAR }
        assertEquals(5, stars.size)
        // 互不重叠(网格间距 420,星半径 30)
        stars.forEach { a ->
            stars.filter { it.id != a.id }.forEach { b ->
                val distance = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
                assertTrue("星之间不该重叠", distance > 60f)
            }
        }
    }

    @Test
    fun `命中测试取最上层且容差为半径与14的较大者`() {
        val map = StarMapLayout.build(
            listOf(detail(id = 1, content = "A", items = listOf(item(1, "a")), tasks = listOf(task(11, 1)))),
        )
        val task = map.nodes.first { it.kind == MapNodeKind.TASK }

        assertNotNull(map.hitTest(task.x, task.y))
        assertEquals(task.id, map.hitTest(task.x, task.y)!!.id)
        // 任务半径 11,容差取 14:距离 13 处仍应命中
        assertNotNull(map.hitTest(task.x + 13f, task.y))
        // 距离 20 处不该命中任何东西
        assertNull(map.hitTest(task.x + 20f, task.y + 20f))
    }

    @Test
    fun `缩放围绕锚点 锚点下的世界坐标不变`() {
        val viewport = MapViewport(scale = 1f, offsetX = 30f, offsetY = -10f)
        val anchorX = 400f
        val anchorY = 300f
        val worldX = viewport.toWorldX(anchorX)
        val worldY = viewport.toWorldY(anchorY)

        val zoomed = viewport.zoomBy(2f, anchorX, anchorY)

        assertEquals(2f, zoomed.scale, 1e-5f)
        assertEquals("锚点下的世界坐标不该移动", worldX, zoomed.toWorldX(anchorX), 1e-3f)
        assertEquals("锚点下的世界坐标不该移动", worldY, zoomed.toWorldY(anchorY), 1e-3f)
    }

    @Test
    fun `缩放被夹在上下限内`() {
        val zoomedOut = MapViewport(scale = 1f).zoomBy(0.001f, 0f, 0f)
        assertEquals(MapViewport.MIN_SCALE, zoomedOut.scale, 1e-5f)

        val zoomedIn = MapViewport(scale = 1f).zoomBy(1000f, 0f, 0f)
        assertEquals(MapViewport.MAX_SCALE, zoomedIn.scale, 1e-5f)

        // 已经到上限时再放大不该改变视口
        assertEquals(zoomedIn, zoomedIn.zoomBy(2f, 10f, 10f))
    }

    @Test
    fun `屏幕与世界坐标互为逆变换`() {
        val viewport = MapViewport(scale = 1.7f, offsetX = -120f, offsetY = 88f)
        listOf(0f, 123.5f, -400f).forEach { value ->
            assertEquals(value, viewport.toWorldX(viewport.toScreenX(value)), 1e-3f)
            assertEquals(value, viewport.toWorldY(viewport.toScreenY(value)), 1e-3f)
        }
    }

    @Test
    fun `平移只改偏移不改缩放`() {
        val moved = MapViewport(scale = 1.5f, offsetX = 0f, offsetY = 0f).panBy(12f, -7f)

        assertEquals(1.5f, moved.scale, 1e-5f)
        assertEquals(12f, moved.offsetX, 1e-5f)
        assertEquals(-7f, moved.offsetY, 1e-5f)
    }

    @Test
    fun `条目为空时星图只有那几个星`() {
        val map = StarMapLayout.build(listOf(detail(id = 1, content = "空清单"), detail(id = 2, content = "也是空")))

        assertEquals(2, map.nodes.size)
        assertTrue(map.nodes.all { it.kind == MapNodeKind.STAR })
        assertTrue(map.edges.isEmpty())
        // 两格的星在同一条水平线上,间距 420
        val xs = map.nodes.map { it.x }.sorted()
        assertEquals(420f, abs(xs[1] - xs[0]), 0.01f)
    }
}
