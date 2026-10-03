package cn.tgbug.nekotodo.domain

import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TaskOrderingTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val utc = ZoneId.of("UTC")

    private fun task(
        id: Long,
        deadline: String? = null,
        priority: Int = 1,
        description: String = "任务$id",
        details: String = "",
        status: TaskStatus = TaskStatus.INCOMPLETE,
    ) = Task(
        id = id,
        description = description,
        status = status,
        details = details,
        deadline = deadline,
        priority = priority,
    )

    @Test
    fun `排序以截止日为主键 priority 为次键 无截止排在最后`() {
        val tasks = listOf(
            task(1, deadline = null, priority = 1),
            task(2, deadline = "2026-08-10T00:00:00Z", priority = 2),
            task(3, deadline = "2026-08-10T00:00:00Z", priority = 1),
            task(4, deadline = "2026-08-09T00:00:00Z", priority = 5),
        )

        assertEquals(listOf(4L, 3L, 2L, 1L), TaskOrdering.sorted(tasks, shanghai).map { it.id })
    }

    @Test
    fun `截止日分组按用户时区的自然日划分`() {
        // UTC 下跨了两天,但在上海都是 8/6
        val earlier = task(1, deadline = "2026-08-05T23:00:00Z")
        val later = task(2, deadline = "2026-08-06T01:00:00Z")

        assertTrue(TaskOrdering.sameDayGroup(earlier, later, shanghai))
        assertFalse(TaskOrdering.sameDayGroup(earlier, later, utc))
    }

    @Test
    fun `无截止时间的任务彼此同组`() {
        assertTrue(TaskOrdering.sameDayGroup(task(1), task(2), shanghai))
        assertFalse(TaskOrdering.sameDayGroup(task(1), task(2, "2026-08-10T00:00:00Z"), shanghai))
    }

    @Test
    fun `拖到某个任务之前换算成组内位次`() {
        val tasks = listOf(
            task(1, "2026-08-10T00:00:00Z", priority = 1),
            task(2, "2026-08-10T00:00:00Z", priority = 2),
            task(3, "2026-08-10T00:00:00Z", priority = 3),
        )

        // 3 拖到 1 之前:摘掉 3 后组是 [1,2],1 的下标为 0 → 位次 1
        assertEquals(1, TaskOrdering.positionBefore(tasks, draggedId = 3, beforeId = 1, shanghai))
        // 1 拖到 2 之前:摘掉 1 后组是 [2,3],2 的下标为 0 → 位次 1
        assertEquals(1, TaskOrdering.positionBefore(tasks, draggedId = 1, beforeId = 2, shanghai))
        // 1 拖到 3 之前:摘掉 1 后组是 [2,3],3 的下标为 1 → 位次 2
        assertEquals(2, TaskOrdering.positionBefore(tasks, draggedId = 1, beforeId = 3, shanghai))
    }

    @Test
    fun `跨截止日组或自我拖动一律返回 null`() {
        val tasks = listOf(
            task(1, "2026-08-10T00:00:00Z"),
            task(2, "2026-08-11T00:00:00Z"),
            task(3, deadline = null),
        )

        assertNull(TaskOrdering.positionBefore(tasks, draggedId = 1, beforeId = 2, shanghai))
        assertNull(TaskOrdering.positionBefore(tasks, draggedId = 2, beforeId = 3, shanghai))
        assertNull(TaskOrdering.positionBefore(tasks, draggedId = 1, beforeId = 1, shanghai))
    }

    @Test
    fun `拖到组末尾取摘掉自己后的元素数加一`() {
        val tasks = listOf(
            task(1, "2026-08-10T00:00:00Z", priority = 1),
            task(2, "2026-08-10T00:00:00Z", priority = 2),
            task(9, "2026-08-11T00:00:00Z"),
        )

        assertEquals(2, TaskOrdering.positionAtGroupEnd(tasks, draggedId = 1, shanghai))
        assertNull(TaskOrdering.positionAtGroupEnd(tasks, draggedId = 9, shanghai))
    }

    @Test
    fun `松手后的位次由被拖任务后面那个任务决定`() {
        val tasks = listOf(
            task(1, "2026-10-05T02:00:00Z", priority = 1),
            task(2, "2026-10-05T02:00:00Z", priority = 2),
        )
        // 把 1 拖到 2 的位置之后,界面上是 [2, 1]——这里是用户拖动后的排列,不能再排序
        val visible = listOf(tasks[1], tasks[0])

        assertEquals(2, TaskOrdering.positionAfterDrop(tasks, visible, draggedId = 1, shanghai))
    }

    @Test
    fun `拖到组末尾时后面那个任务属于下一组也能算出位次`() {
        // 复现真实数据形状:10-03 一条、10-05 同一组两条、10-06 一条。
        val mobile = task(29, "2026-10-03T15:59:00Z", priority = 1)
        val dragA = task(30, "2026-10-05T02:00:00Z", priority = 1)
        val dragB = task(31, "2026-10-05T02:00:00Z", priority = 2)
        val dragC = task(32, "2026-10-06T02:00:00Z", priority = 1)
        val all = listOf(mobile, dragA, dragB, dragC)

        // 用户把 A 往下拖到 B 之后 → 界面顺序 [mobile, B, A, C]
        // A 后面那个任务 C 属于另一组,这正是曾经让"拖到组末尾"被误判为无效移动的分支。
        val visible = listOf(mobile, dragB, dragA, dragC)

        assertEquals(2, TaskOrdering.positionAfterDrop(all, visible, draggedId = 30, shanghai))
    }

    @Test
    fun `拖到可见列表末尾等价于落到本组末尾`() {
        val dragA = task(30, "2026-10-05T02:00:00Z", priority = 1)
        val dragB = task(31, "2026-10-05T02:00:00Z", priority = 2)
        val all = listOf(dragA, dragB)

        val visible = listOf(dragB, dragA) // A 被拖到最后,后面没有任务了

        assertEquals(2, TaskOrdering.positionAfterDrop(all, visible, draggedId = 30, shanghai))
    }

    @Test
    fun `把后面那个任务拖到前面`() {
        val a = task(1, "2026-10-05T02:00:00Z", priority = 1)
        val b = task(2, "2026-10-05T02:00:00Z", priority = 2)
        val all = listOf(a, b)

        val visible = listOf(b, a) // B 上移到 A 之前

        // B 后面是 A(同组)→ 插到 A 之前 → 位次 1
        assertEquals(1, TaskOrdering.positionAfterDrop(all, visible, draggedId = 2, shanghai))
    }

    @Test
    fun `组内只有自己时松手没有有效位次`() {
        val alone = task(1, "2026-10-05T02:00:00Z", priority = 1)
        val other = task(2, "2026-10-06T02:00:00Z", priority = 1)
        val all = listOf(alone, other)

        assertNull(TaskOrdering.positionAfterDrop(all, all, draggedId = 1, shanghai))
    }

    @Test
    fun `positionOf 返回组内位次而不是全局下标`() {
        val tasks = listOf(
            task(1, "2026-10-05T02:00:00Z", priority = 1),
            task(2, "2026-10-05T02:00:00Z", priority = 2),
            task(3, "2026-10-06T02:00:00Z", priority = 1),
        )

        assertEquals(1, TaskOrdering.positionOf(tasks, taskId = 1, shanghai))
        assertEquals(2, TaskOrdering.positionOf(tasks, taskId = 2, shanghai))
        // 3 在全局是第 3 个,但在自己的组里是第 1 个
        assertEquals(1, TaskOrdering.positionOf(tasks, taskId = 3, shanghai))
        assertNull(TaskOrdering.positionOf(tasks, taskId = 99, shanghai))
    }

    @Test
    fun `上移下移换算成组内位次`() {
        val tasks = listOf(
            task(1, "2026-08-10T00:00:00Z", priority = 1),
            task(2, "2026-08-10T00:00:00Z", priority = 2),
            task(3, "2026-08-10T00:00:00Z", priority = 3),
        )

        assertEquals(2, TaskOrdering.positionByOffset(tasks, taskId = 1, offset = 1, shanghai))
        assertEquals(3, TaskOrdering.positionByOffset(tasks, taskId = 2, offset = 1, shanghai))
        assertEquals(1, TaskOrdering.positionByOffset(tasks, taskId = 2, offset = -1, shanghai))
        assertEquals(2, TaskOrdering.positionByOffset(tasks, taskId = 3, offset = -1, shanghai))
    }

    @Test
    fun `上移下移到边界返回 null`() {
        val tasks = listOf(
            task(1, "2026-08-10T00:00:00Z", priority = 1),
            task(2, "2026-08-10T00:00:00Z", priority = 2),
        )

        assertNull(TaskOrdering.positionByOffset(tasks, taskId = 1, offset = -1, shanghai))
        assertNull(TaskOrdering.positionByOffset(tasks, taskId = 2, offset = 1, shanghai))
        assertNull(TaskOrdering.positionByOffset(tasks, taskId = 1, offset = 0, shanghai))
    }

    @Test
    fun `组内只有一个任务时不能上下移`() {
        val tasks = listOf(task(1, "2026-08-10T00:00:00Z"), task(2, "2026-08-11T00:00:00Z"))

        assertNull(TaskOrdering.positionByOffset(tasks, taskId = 1, offset = 1, shanghai))
        assertNull(TaskOrdering.positionByOffset(tasks, taskId = 1, offset = -1, shanghai))
    }

    @Test
    fun `同一截止日组的任务按 priority 连续排列`() {
        val tasks = listOf(
            task(1, "2026-08-10T00:00:00Z", priority = 3),
            task(2, "2026-08-10T00:00:00Z", priority = 1),
            task(3, "2026-08-10T00:00:00Z", priority = 2),
        )

        assertEquals(listOf(2L, 3L, 1L), TaskOrdering.sorted(tasks, shanghai).map { it.id })
    }
}
