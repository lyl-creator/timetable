package com.lyl.timetable.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提醒去重记录（[ReminderLedger]）的编解码与占位规则验证。
 *
 * 这部分逻辑决定「服务内计时器」与「系统唤醒闹钟」两条投递通道会不会让用户收到两条通知，
 * 也是唯一需要跨进程持久化的提醒状态，因此单独覆盖。
 */
class ReminderLedgerTest {

    private val hour = 60 * 60 * 1000L

    // ------------------------------------------------------------------
    //  键与占位
    // ------------------------------------------------------------------

    @Test
    fun `同一门课同一触发时刻的键相同，课程名不同则不同`() {
        val a = ReminderLedger.keyOf(1_000L, "高等数学", 1)
        val b = ReminderLedger.keyOf(1_000L, "高等数学", 1)
        val c = ReminderLedger.keyOf(1_000L, "线性代数", 1)
        val d = ReminderLedger.keyOf(2_000L, "高等数学", 1)
        val e = ReminderLedger.keyOf(1_000L, "高等数学", 3)

        assertEquals(a, b)
        assertFalse(a == c)
        assertFalse(a == d)
        assertFalse(a == e)
    }

    @Test
    fun `首次投递占位成功，窗口内重复投递被拒绝`() {
        val entries = LinkedHashMap<String, Long>()
        val key = ReminderLedger.keyOf(10_000L, "大学物理", 5)
        val now = 5_000_000L

        assertTrue(ReminderLedger.claim(entries, key, now))
        assertFalse(ReminderLedger.claim(entries, key, now + 1000))
        assertFalse(ReminderLedger.claim(entries, key, now + ReminderLedger.WINDOW_MS - 1))
    }

    @Test
    fun `超出保留窗口后可以再次占位`() {
        val entries = LinkedHashMap<String, Long>()
        val key = ReminderLedger.keyOf(10_000L, "大学物理", 5)
        val now = 5_000_000L

        assertTrue(ReminderLedger.claim(entries, key, now))
        assertTrue(ReminderLedger.claim(entries, key, now + ReminderLedger.WINDOW_MS))
    }

    @Test
    fun `已投递判断与占位规则一致`() {
        val entries = LinkedHashMap<String, Long>()
        val key = ReminderLedger.keyOf(10_000L, "大学物理", 5)
        val now = 5_000_000L
        assertFalse(ReminderLedger.isDelivered(entries, key, now))

        ReminderLedger.claim(entries, key, now)
        assertTrue(ReminderLedger.isDelivered(entries, key, now + hour))
        assertFalse(ReminderLedger.isDelivered(entries, key, now + ReminderLedger.WINDOW_MS))
    }

    // ------------------------------------------------------------------
    //  编解码往返
    // ------------------------------------------------------------------

    @Test
    fun `编解码往返保持键与投递时刻`() {
        val entries = LinkedHashMap<String, Long>()
        entries[ReminderLedger.keyOf(1_700_000_000_000L, "高等数学", 1)] = 1_700_000_000_000L
        entries[ReminderLedger.keyOf(1_700_003_600_000L, "大学英语(3)", 5)] = 1_700_003_600_000L

        val restored = ReminderLedger.decode(ReminderLedger.encode(entries))

        assertEquals(entries, restored)
    }

    @Test
    fun `课程名含空格与中文括号也能正确还原`() {
        val key = ReminderLedger.keyOf(1_700_000_000_000L, "体育 (篮球) ", 5)
        val entries = linkedMapOf(key to 1_700_000_000_000L)

        val restored = ReminderLedger.decode(ReminderLedger.encode(entries))

        assertEquals(1, restored.size)
        assertTrue(restored.containsKey(key))
    }

    @Test
    fun `空记录与非法记录被安全丢弃`() {
        assertTrue(ReminderLedger.decode(null).isEmpty())
        assertTrue(ReminderLedger.decode("").isEmpty())
        assertTrue(ReminderLedger.decode("   ").isEmpty())

        // 字段数不足、数字字段非法、整行乱码，都不应抛异常也不应产生记录
        val messy = listOf(
            "123",
            "123\u0001高数",
            "abc\u0001高数\u00012\u0001999",
            "123\u0001高数\u0001x\u0001999",
            "123\u0001高数\u00012\u0001y",
            "乱码"
        ).joinToString(ReminderLedger.RECORD.toString())

        assertTrue(ReminderLedger.decode(messy).isEmpty())
    }

    @Test
    fun `非法记录旁的合法记录仍被保留`() {
        val deliveredAt = 1_700_000_000_000L
        val validKey = ReminderLedger.keyOf(deliveredAt, "高等数学", 1)
        val validRecord = "$validKey${ReminderLedger.FIELD}$deliveredAt"
        val raw = listOf("123", validRecord, "乱码")
            .joinToString(ReminderLedger.RECORD.toString())

        val restored = ReminderLedger.decode(raw)

        assertEquals(1, restored.size)
        assertEquals(deliveredAt, restored[validKey])
    }

    // ------------------------------------------------------------------
    //  过期清理
    // ------------------------------------------------------------------

    @Test
    fun `清理只移除超出保留窗口的记录`() {
        val now = 10_000_000L
        val fresh = ReminderLedger.keyOf(1L, "新提醒", 1)
        val stale = ReminderLedger.keyOf(2L, "旧提醒", 2)
        val entries = linkedMapOf(
            // 窗口内：保留
            fresh to now - 1_000L,
            // 恰好到窗口边界：与 isDelivered 的口径一致，视为过期
            stale to now - ReminderLedger.WINDOW_MS
        )

        ReminderLedger.prune(entries, now)

        assertEquals(setOf(fresh), entries.keys)
    }
}
