package com.lyl.timetable.reminder

import android.content.Context
import android.content.Intent

/**
 * 提醒投递去重。
 *
 * 现在有两条通道都会投递同一条提醒：应用内的[ReminderService]计时器，以及设备休眠时把 CPU
 * 唤醒的[ReminderScheduler]闹钟。两条通道可能先后触发（例如闹钟先到、设备随后唤醒），
 * 因此这里记录「已经投递过」的提醒，保证用户只会看到一条通知。
 *
 * 记录以 `触发时刻 + 课程名 + 起始节次` 为键，并在 [ReminderLedger.WINDOW_MS] 之后自然过期，
 * 因此不会无限增长，也能避免设备时间被回拨后重复提醒。
 *
 * 编解码与过期判断放在纯 Kotlin 的 [ReminderLedger] 中（可被单元测试覆盖），
 * 本对象只负责 Android 侧的持久化与通知投递。
 */
internal object ReminderDelivered {

    private const val PREFS = "timetable_reminders"
    private const val KEY_ENTRIES = "delivered_entries"

    /** 投递服务内排定的提醒（应用自发通知的主通道） */
    fun deliver(context: Context, event: ReminderEvent) {
        val app = context.applicationContext
        if (!claim(app, event.triggerMillis, event.courseName, event.startSection)) return
        ReminderNotifications.showEvent(app, event)
    }

    /** 投递闹钟唤醒带来的提醒（设备休眠时的兜底通道） */
    fun deliverFromIntent(context: Context, intent: Intent) {
        val app = context.applicationContext
        val triggerMillis = intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER_MS, 0L)
        val name = intent.getStringExtra(ReminderScheduler.EXTRA_NAME).orEmpty()
        val startSection = intent.getIntExtra(ReminderScheduler.EXTRA_START_SECTION, 0)
        if (!claim(app, triggerMillis, name, startSection)) return
        ReminderNotifications.show(app, intent)
    }

    /** 该提醒是否已经投递过（服务排定计划时用来跳过已送达的项） */
    fun wasDelivered(context: Context, event: ReminderEvent): Boolean {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val key = ReminderLedger.keyOf(event.triggerMillis, event.courseName, event.startSection)
        return ReminderLedger.isDelivered(load(app), key, now)
    }

    /** 占位：首次投递返回 true，窗口内重复投递返回 false */
    private fun claim(context: Context, triggerMillis: Long, name: String, startSection: Int): Boolean {
        val now = System.currentTimeMillis()
        val entries = load(context)
        val key = ReminderLedger.keyOf(triggerMillis, name, startSection)
        if (!ReminderLedger.claim(entries, key, now)) return false
        ReminderLedger.prune(entries, now)
        save(context, entries)
        return true
    }

    private fun load(context: Context): MutableMap<String, Long> =
        ReminderLedger.decode(prefs(context).getString(KEY_ENTRIES, null))

    private fun save(context: Context, entries: Map<String, Long>) {
        prefs(context).edit().putString(KEY_ENTRIES, ReminderLedger.encode(entries)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
