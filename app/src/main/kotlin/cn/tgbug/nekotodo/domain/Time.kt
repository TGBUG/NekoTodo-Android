package cn.tgbug.nekotodo.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 时间戳一律以 UTC 在后端流转,展示与输入按用户时区换算。
 * 这里用 java.time 直接表达,不再需要 Web 端那套手工推时区偏移量的做法。
 */
object Time {

    private val ZONE_SUFFIX = Regex("([zZ]|[+-]\\d{2}:\\d{2})$")

    private val WALL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    private val SHORT_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    private val END_OF_DAY = LocalTime.of(23, 59)

    fun zone(id: String?): ZoneId =
        id?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.of("UTC")

    /** 后端返回的应是 UTC ISO 8601,但可能缺少末尾的 Z;缺了就补上再解析。 */
    fun parseApiInstant(raw: String): Instant {
        val trimmed = raw.trim()
        return Instant.parse(if (ZONE_SUFFIX.containsMatchIn(trimmed)) trimmed else trimmed + "Z")
    }

    /** datetime-local 输入框的值缺省是本地墙钟时间,需要按用户时区解释。 */
    fun wallToInstant(wall: String, zone: ZoneId): Instant =
        LocalDateTime.parse(wall).atZone(zone).toInstant()

    fun instantToWall(instant: Instant, zone: ZoneId): String =
        instant.atZone(zone).toLocalDateTime().format(WALL_FORMATTER)

    fun localDateOf(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    /** 列表里展示截止时间用的短格式。 */
    fun formatShort(instant: Instant, zone: ZoneId): String =
        instant.atZone(zone).format(SHORT_FORMATTER)

    // ---- 截止日期预设(与 Web 端「今晚 / 明天 / 三天后 / 一周后」一致)----

    fun tonight(now: Instant, zone: ZoneId): Instant =
        localDateOf(now, zone).atTime(END_OF_DAY).atZone(zone).toInstant()

    fun endOfDayAfter(now: Instant, zone: ZoneId, days: Long): Instant =
        localDateOf(now, zone).plusDays(days).atTime(END_OF_DAY).atZone(zone).toInstant()

    /** 保持当前时刻的墙钟时间,只把日期往后推。 */
    fun sameTimeAfter(now: Instant, zone: ZoneId, days: Long): Instant =
        LocalDateTime.ofInstant(now, zone).plusDays(days).atZone(zone).toInstant()
}
