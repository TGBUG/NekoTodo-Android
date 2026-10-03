package cn.tgbug.nekotodo.domain

import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TaskFilterTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun task(
        id: Long,
        deadline: String? = null,
        priority: Int = 1,
        description: String = "任务$id",
        details: String = "",
        category: String? = null,
        status: TaskStatus = TaskStatus.INCOMPLETE,
        sourceItemId: Long? = null,
    ) = Task(
        id = id,
        description = description,
        status = status,
        details = details,
        deadline = deadline,
        priority = priority,
        category = category,
        sourceItemId = sourceItemId,
    )

    private fun ids(tasks: List<Task>) = tasks.map { it.id }

    @Test
    fun `文本可以命中描述或细节`() {
        val tasks = listOf(
            task(1, description = "买牛奶"),
            task(2, description = "做作业", details = "数学练习册 p50"),
            task(3, description = "别的"),
        )

        assertEquals(listOf(1L), ids(TaskFilter.apply(tasks, SearchQuery(text = "牛奶"), shanghai)))
        assertEquals(listOf(2L), ids(TaskFilter.apply(tasks, SearchQuery(text = "练习册"), shanghai)))
    }

    @Test
    fun `文本匹配忽略大小写`() {
        val tasks = listOf(task(1, description = "Read Kotlin Docs"))

        assertEquals(listOf(1L), ids(TaskFilter.apply(tasks, SearchQuery(text = "kotlin"), shanghai)))
    }

    @Test
    fun `按分类与状态过滤`() {
        val tasks = listOf(
            task(1, category = "生活"),
            task(2, category = "学习"),
            task(3, category = "生活", status = TaskStatus.COMPLETED),
        )

        assertEquals(listOf(1L, 3L), ids(TaskFilter.apply(tasks, SearchQuery(category = "生活"), shanghai)))
        assertEquals(
            listOf(3L),
            ids(TaskFilter.apply(tasks, SearchQuery(category = "生活", status = TaskStatus.COMPLETED), shanghai)),
        )
    }

    @Test
    fun `日期区间按用户时区的自然日解释`() {
        val tasks = listOf(
            // 上海时间 8/6 01:00,属于 8/6
            task(1, deadline = "2026-08-05T17:00:00Z"),
            // 上海时间 8/5 23:00,不属于 8/6
            task(2, deadline = "2026-08-05T15:00:00Z"),
            task(3, deadline = null),
        )

        val result = TaskFilter.apply(
            tasks,
            SearchQuery(from = LocalDate.of(2026, 8, 6), to = LocalDate.of(2026, 8, 6)),
            shanghai,
        )

        assertEquals(listOf(1L), ids(result))
    }

    @Test
    fun `区间上界含当天 23 时 59 分`() {
        val tasks = listOf(
            // 上海时间 8/6 23:00
            task(1, deadline = "2026-08-06T15:00:00Z"),
            // 上海时间 8/7 01:00,超出上界
            task(2, deadline = "2026-08-06T17:00:00Z"),
        )

        val result = TaskFilter.apply(
            tasks,
            SearchQuery(to = LocalDate.of(2026, 8, 6)),
            shanghai,
        )

        assertEquals(listOf(1L), ids(result))
    }

    @Test
    fun `给了日期条件就排除无截止时间的任务`() {
        val tasks = listOf(task(1, deadline = null))

        assertTrue(TaskFilter.apply(tasks, SearchQuery(from = LocalDate.of(2026, 8, 1)), shanghai).isEmpty())
    }

    @Test
    fun `无任何条件时全部返回且按截止日排序`() {
        val tasks = listOf(
            task(1, deadline = null, priority = 1),
            task(2, deadline = "2026-08-10T00:00:00Z", priority = 2),
            task(3, deadline = "2026-08-10T00:00:00Z", priority = 1),
        )

        assertEquals(listOf(3L, 2L, 1L), ids(TaskFilter.apply(tasks, SearchQuery(), shanghai)))
    }

    @Test
    fun `isOverdue 只对未完成且已过截止时间的任务成立`() {
        val now = Instant.parse("2026-08-10T00:00:00Z")

        assertTrue(TaskFilter.isOverdue(task(1, deadline = "2026-08-09T00:00:00Z"), now))
        assertFalse(TaskFilter.isOverdue(task(2, deadline = "2026-08-11T00:00:00Z"), now))
        assertFalse(
            TaskFilter.isOverdue(
                task(3, deadline = "2026-08-09T00:00:00Z", status = TaskStatus.COMPLETED),
                now,
            ),
        )
        assertFalse(TaskFilter.isOverdue(task(4, deadline = null), now))
    }
}
