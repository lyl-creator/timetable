package com.lyl.timetable.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lyl.timetable.data.AppSettings
import com.lyl.timetable.data.Course
import com.lyl.timetable.data.TimetableRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 上课提醒的闹钟调度。
 *
 * 采用 **系统闹钟（AlarmManager）+ 清单注册的广播接收器** 方案，而不是常驻服务：
 * 闹钟由系统持有，应用进程被清理、甚至从最近任务里划掉之后，到点仍会由系统唤醒接收器投递通知。
 *
 * 覆盖的失效场景：
 * - 后台清理 / 划掉最近任务 → 闹钟在系统侧，照常触发；
 * - 手机重启 → 系统会清空闹钟，由 [SystemEventReceiver] 收到开机广播后重排；
 * - 手动修改系统时间或时区 → 同样重排，避免触发时刻漂移；
 * - 长期不打开应用 → 一次性预排未来 [ReminderPlanner.DAYS_AHEAD] 天，并加装每日重排闹钟滚动补充。
 *
 * 唯一无法覆盖的是用户在系统设置里「强行停止」应用——此时系统会撤销该应用的全部闹钟，
 * 需重新打开一次应用；设置页对此有说明。
 */
object ReminderScheduler {

    /** 闹钟到点 → 投递通知 */
    const val ACTION_REMIND = "com.lyl.timetable.action.REMIND"

    /** 每日重排，把预排窗口向前滚动 */
    const val ACTION_REARM = "com.lyl.timetable.action.REARM"

    internal const val EXTRA_NAME = "course_name"
    internal const val EXTRA_TEACHER = "course_teacher"
    internal const val EXTRA_LOCATION = "course_location"
    internal const val EXTRA_COLOR = "course_color"
    internal const val EXTRA_DAY = "course_day"
    internal const val EXTRA_START_SECTION = "course_start_section"
    internal const val EXTRA_END_SECTION = "course_end_section"
    internal const val EXTRA_START_TEXT = "course_start_text"
    internal const val EXTRA_END_TEXT = "course_end_text"
    internal const val EXTRA_LEAD = "course_lead"
    internal const val EXTRA_TRIGGER_MS = "course_trigger_ms"

    private const val PREFS = "timetable_reminders"
    private const val KEY_CODES = "scheduled_codes"
    private const val REARM_REQUEST = 2_000_001

    /** 每日重排的时刻（本地时间） */
    private val REARM_TIME: LocalTime = LocalTime.of(0, 10)

    // ------------------------------------------------------------------
    //  对外入口
    // ------------------------------------------------------------------

    /** 依据当前课程与设置重排全部提醒（先撤销旧的，再按最新数据排布） */
    fun reschedule(context: Context, courses: List<Course>, settings: AppSettings) {
        val app = context.applicationContext
        cancelAll(app)
        ReminderNotifications.ensureChannels(app)
        // 通知由应用自身的前台服务发出；闹钟只承担设备休眠时的唤醒职责
        syncService(app, settings)
        if (!settings.canScheduleReminder) return

        val now = LocalDateTime.now()
        val events = ReminderPlanner.plan(courses, settings, now.toLocalDate(), now)
        if (events.isEmpty()) return

        val alarmManager = app.getSystemService(AlarmManager::class.java) ?: return
        val codes = ArrayList<Int>(events.size)
        for ((index, event) in events.withIndex()) {
            val code = index + 1
            val pendingIntent = reminderPendingIntent(app, code, event, create = true)
                ?: continue
            val triggerAt = event.triggerAt
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (setAlarm(alarmManager, triggerAt, pendingIntent)) codes.add(code)
        }
        saveCodes(app, codes)

        // 每日重排：让预排窗口持续向前滚动，长时间不打开应用也不会漏提醒
        runCatching {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextRearmMillis(now),
                AlarmManager.INTERVAL_DAY,
                rearmPendingIntent(app, create = true) ?: return@runCatching
            )
        }
    }

    /** 供广播接收器使用：从本机数据重新排布 */
    fun rescheduleFromStore(context: Context) {
        val app = context.applicationContext
        runCatching {
            val repository = TimetableRepository(app)
            reschedule(app, repository.courses.value, repository.settings.value)
        }
    }

    /** 撤销全部提醒（关闭开关、清空课程时调用） */
    fun cancelAll(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        if (alarmManager != null) {
            for (code in loadCodes(app)) {
                reminderPendingIntent(app, code, null, create = false)?.let { alarmManager.cancel(it) }
            }
            rearmPendingIntent(app, create = false)?.let { alarmManager.cancel(it) }
        }
        saveCodes(app, emptyList())
    }

    /**
     * 让前台服务和「提醒是否应当工作」保持一致。
     *
     * 服务是通知的主通道；[ReminderScheduler] 的闹钟只是设备休眠时的唤醒兜底，
     * 因此开关状态变化时这里要同步启停服务。
     */
    fun syncService(context: Context, settings: AppSettings) {
        if (settings.reminderEnabled && settings.keepAliveEnabled) {
            ReminderService.ensureRunning(context)
        } else {
            ReminderService.stop(context)
        }
    }

    /** 是否具备精确闹钟权限（Android 12 起为特殊权限，可能被系统收回） */
    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    // ------------------------------------------------------------------
    //  内部实现
    // ------------------------------------------------------------------

    private fun setAlarm(
        alarmManager: AlarmManager,
        triggerAt: Long,
        pendingIntent: PendingIntent
    ): Boolean = runCatching {
        if (canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            // 没有精确闹钟授权时退化为「允许在低电耗模式下唤醒」的非精确闹钟：
            // 可能晚几分钟，但仍能在后台被清理的情况下送达
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        true
    }.getOrElse {
        runCatching {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            true
        }.getOrDefault(false)
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
        else true

    private fun reminderPendingIntent(
        context: Context,
        code: Int,
        event: ReminderEvent?,
        create: Boolean
    ): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND)
        if (event != null) {
            intent.putExtra(EXTRA_NAME, event.courseName)
            intent.putExtra(EXTRA_TEACHER, event.teacher)
            intent.putExtra(EXTRA_LOCATION, event.location)
            intent.putExtra(EXTRA_COLOR, event.colorIndex)
            intent.putExtra(EXTRA_DAY, event.dayOfWeek)
            intent.putExtra(EXTRA_START_SECTION, event.startSection)
            intent.putExtra(EXTRA_END_SECTION, event.endSection)
            intent.putExtra(EXTRA_START_TEXT, event.startText)
            intent.putExtra(EXTRA_END_TEXT, event.endText)
            intent.putExtra(EXTRA_LEAD, event.leadMinutes)
            // 与服务内计时器共用的去重键，避免两条通道各投递一次
            intent.putExtra(EXTRA_TRIGGER_MS, event.triggerMillis)
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return runCatching { PendingIntent.getBroadcast(context, code, intent, flags) }.getOrNull()
    }

    private fun rearmPendingIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, SystemEventReceiver::class.java).setAction(ACTION_REARM)
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return runCatching {
            PendingIntent.getBroadcast(context, REARM_REQUEST, intent, flags)
        }.getOrNull()
    }

    private fun nextRearmMillis(now: LocalDateTime): Long {
        var target = now.toLocalDate().atTime(REARM_TIME)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun loadCodes(context: Context): List<Int> {
        val raw = prefs(context).getString(KEY_CODES, null)
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    private fun saveCodes(context: Context, codes: List<Int>) {
        prefs(context).edit().putString(KEY_CODES, codes.joinToString(",")).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
