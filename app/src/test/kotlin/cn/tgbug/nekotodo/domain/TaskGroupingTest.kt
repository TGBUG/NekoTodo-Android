package cn.tgbug.nekotodo.domain

import cn.tgbug.nekotodo.data.Task
import cn.tgbug.nekotodo.data.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class TaskGroupingTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    private fun task(
        id: Long,
        deadline: String? = null,
        priority: Int = 1,
        category: String? = null,
        status: TaskStatus = TaskStatus.INCOMPLETE,
    ) = Task(
        id = id,
        description = "任务$id",
        status = status,
        deadline = deadline,
        priority = priority,
        category = category,
    )

    @Test
    fun `分类只统计有分类的任务且计数是未完成数`() {
        val tasks = listOf(
            task(1, category = "生活"),
            task(2, category = "生活", status = TaskStatus.COMPLETED),
            task(3, category = "学习"),
            task(4, category = null),
            task(5, category = "  "),
        )

        val categories = TaskGrouping.categories(tasks)

        assertEquals(2, categories.size)
        assertEquals(CategoryCount("生活", 1), categories.first { it.name == "生活" })
        assertEquals(CategoryCount("学习", 1), categories.first { it.name == "学习" })
    }

    @Test
    fun `分类全部完成时计数为零但仍然列出`() {
        val tasks = listOf(task(1, category = "生活", status = TaskStatus.COMPLETED))

        assertEquals(listOf(CategoryCount("生活", 0)), TaskGrouping.categories(tasks))
    }

    @Test
    fun `未完成与已完成分成两组且组内保持原顺序`() {
        val tasks = listOf(
            task(1),
            task(2, status = TaskStatus.COMPLETED),
            task(3),
            task(4, status = TaskStatus.COMPLETED),
        )

        val (incomplete, completed) = TaskGrouping.splitByCompletion(tasks)

        assertEquals(listOf(1L, 3L), incomplete.map { it.id })
        assertEquals(listOf(2L, 4L), completed.map { it.id })
    }

    @Test
    fun `拖动的允许范围只覆盖自己所在的截止日组`() {
        val tasks = TaskOrdering.sorted(
            listOf(
                task(1, "2026-08-10T00:00:00Z", priority = 1),
                task(2, "2026-08-10T00:00:00Z", priority = 2),
                task(3, "2026-08-11T00:00:00Z", priority = 1),
                task(4, "2026-08-11T00:00:00Z", priority = 2),
            ),
            shanghai,
        )

        assertEquals(0..1, TaskGrouping.reorderBounds(tasks, draggedId = 1, shanghai))
        assertEquals(0..1, TaskGrouping.reorderBounds(tasks, draggedId = 2, shanghai))
        assertEquals(2..3, TaskGrouping.reorderBounds(tasks, draggedId = 3, shanghai))
    }

    @Test
    fun `无截止时间的任务自成一个可拖动区间并排在最前之后`() {
        val tasks = TaskOrdering.sorted(
            listOf(
                task(1, deadline = null, priority = 1),
                task(2, deadline = null, priority = 2),
                task(3, "2026-08-10T00:00:00Z", priority = 1),
            ),
            shanghai,
        )

        // 有截止的排前面,无截止的自成一组在后面
        assertEquals(listOf(3L, 1L, 2L), tasks.map { it.id })
        assertEquals(1..2, TaskGrouping.reorderBounds(tasks, draggedId = 1, shanghai))
        assertEquals(0..0, TaskGrouping.reorderBounds(tasks, draggedId = 3, shanghai))
    }

    @Test
    fun `不在列表里的任务没有可拖动范围`() {
        assertNull(TaskGrouping.reorderBounds(listOf(task(1)), draggedId = 99, shanghai))
    }

    @Test
    fun `分类排序对中文按拼音而非码位`() {
        val tasks = listOf(task(1, category = "学习"), task(2, category = "生活"), task(3, category = "工作"))

        val names = TaskGrouping.categories(tasks).map { it.name }

        // 按拼音应为 工作(g) < 生活(s) < 学习(x);按 UTF-16 码位则会得到另一顺序
        assertEquals(listOf("工作", "生活", "学习"), names)
        assertTrue(names.isNotEmpty())
    }
}
