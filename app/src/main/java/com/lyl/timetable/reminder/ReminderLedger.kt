package com.lyl.timetable.reminder

/**
 * 提醒去重记录的纯逻辑部分。
 *
 * 不引用任何 Android API，因此编解码、过期判断、占位规则都能被 JVM 单元测试完整覆盖；
 * Android 侧的 SharedPreferences 读写由 [ReminderDelivered] 负责。
 *
 * 记录以 `触发时刻 + 课程名 + 起始节次` 为键。键中刻意不含日期：提醒的触发时刻本身
 * 已经是一个绝对时间戳，把这三者拼在一起即可唯一标识「某门课在某天某节前的这一次提醒」。
 * 课程名中不可能出现 [FIELD] 这样的控制字符，因此用它作分隔符比 `=` / `|` 更安全。
 */
internal object ReminderLedger {

    /** 字段分隔符 */
    const val FIELD = '\u0001'

    /** 记录分隔符 */
    const val RECORD = '\n'

    /** 记录保留时长：超过它的旧记录会自然过期并被清理，避免无限增长 */
    const val WINDOW_MS = 6 * 60 * 60 * 1000L

    /** 一条记录包含的字段数：触发时刻 ␁ 课程名 ␁ 起始节次 ␁ 投递时刻 */
    private const val FIELD_COUNT = 4

    fun keyOf(triggerMillis: Long, name: String, startSection: Int): String =
        "$triggerMillis$FIELD$name$FIELD$startSection"

    /** 解析持久化的记录文本；格式不合法的行会被丢弃，不会影响其余记录 */
    fun decode(raw: String?): MutableMap<String, Long> {
        val out = LinkedHashMap<String, Long>()
        if (raw.isNullOrBlank()) return out
        for (record in raw.split(RECORD)) {
            if (record.isBlank()) continue
            val parts = record.split(FIELD)
            if (parts.size != FIELD_COUNT) continue
            val trigger = parts[0].toLongOrNull() ?: continue
            val startSection = parts[2].toIntOrNull() ?: continue
            val deliveredAt = parts[3].toLongOrNull() ?: continue
            out[keyOf(trigger, parts[1], startSection)] = deliveredAt
        }
        return out
    }

    fun encode(entries: Map<String, Long>): String =
        entries.entries.joinToString(RECORD.toString()) { (key, value) -> "$key$FIELD$value" }

    /** 该提醒是否已投递过（且在保留窗口内） */
    fun isDelivered(
        entries: Map<String, Long>,
        key: String,
        now: Long,
        windowMs: Long = WINDOW_MS
    ): Boolean {
        val deliveredAt = entries[key] ?: return false
        return now - deliveredAt < windowMs
    }

    /**
     * 尝试占位：首次投递返回 `true`，窗口内的重复投递返回 `false`。
     *
     * 占位成功会就地写入 [entries]；调用方随后应调用 [prune] 再持久化。
     */
    fun claim(
        entries: MutableMap<String, Long>,
        key: String,
        now: Long,
        windowMs: Long = WINDOW_MS
    ): Boolean {
        if (isDelivered(entries, key, now, windowMs)) return false
        entries[key] = now
        return true
    }

    /** 清除已过期的记录，就地修改 [entries] */
    fun prune(entries: MutableMap<String, Long>, now: Long, windowMs: Long = WINDOW_MS) {
        entries.entries.removeAll { now - it.value >= windowMs }
    }
}
