package cn.tgbug.nekotodo.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimeTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun `缺少末尾 Z 的时间戳按 UTC 解析`() {
        assertEquals(
            Instant.parse("2026-08-05T16:00:00Z"),
            Time.parseApiInstant("2026-08-05T16:00:00"),
        )
    }

    @Test
    fun `带 Z 或带偏移量的时间戳照常解析`() {
        assertEquals(
            Instant.parse("2026-08-05T16:00:00Z"),
            Time.parseApiInstant("2026-08-05T16:00:00Z"),
        )
        assertEquals(
            Instant.parse("2026-08-05T16:00:00Z"),
            Time.parseApiInstant("2026-08-06T00:00:00+08:00"),
        )
    }

    @Test
    fun `UTC 瞬时与本地墙钟时间可以互转`() {
        val instant = Instant.parse("2026-08-05T16:00:00Z") // 上海时间 8/6 00:00
        assertEquals("2026-08-06T00:00", Time.instantToWall(instant, shanghai))
        assertEquals(instant, Time.wallToInstant("2026-08-06T00:00", shanghai))
    }

    @Test
    fun `今晚取本地当天 23 时 59 分`() {
        val now = Instant.parse("2026-08-05T16:00:00Z") // 上海时间 8/6 00:00
        assertEquals(Instant.parse("2026-08-06T15:59:00Z"), Time.tonight(now, shanghai))
    }

    @Test
    fun `明天取本地次日 23 时 59 分`() {
        val now = Instant.parse("2026-08-05T16:00:00Z")
        assertEquals(Instant.parse("2026-08-07T15:59:00Z"), Time.endOfDayAfter(now, shanghai, 1))
    }

    @Test
    fun `三天后保持当前墙钟时刻只把日期推后`() {
        val now = Instant.parse("2026-08-05T16:00:00Z") // 上海时间 8/6 00:00
        assertEquals(Instant.parse("2026-08-08T16:00:00Z"), Time.sameTimeAfter(now, shanghai, 3))
    }

    @Test
    fun `短格式按用户时区展示月日与时分`() {
        val instant = Instant.parse("2026-08-05T16:00:00Z") // 上海时间 8/6 00:00

        assertEquals("08-06 00:00", Time.formatShort(instant, shanghai))
    }

    @Test
    fun `非法或缺失的时区一律回落到 UTC`() {
        assertEquals(ZoneId.of("UTC"), Time.zone("Not/AZone"))
        assertEquals(ZoneId.of("UTC"), Time.zone(null))
        assertEquals(ZoneId.of("UTC"), Time.zone("   "))
        assertEquals(shanghai, Time.zone("Asia/Shanghai"))
    }
}
