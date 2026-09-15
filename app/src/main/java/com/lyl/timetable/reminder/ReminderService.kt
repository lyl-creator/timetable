package com.lyl.timetable.reminder

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lyl.timetable.data.TimetableRepository
import java.time.Duration
import java.time.LocalDateTime

/**
 * 上课提醒的前台服务 —— **通知由应用自己发出**，而不是交给系统定时机制直接投递。
 *
 * 保活思路与微信的后台表现一致：
 *
 * 1. **前台服务 + 常驻通知**：进程优先级升为前台级（`PROCESS_STATE_FOREGROUND`），
 *    系统回收内存时最后才轮到它，被回收后也会被系统优先重建；
 * 2. **`android:stopWithTask="false"`**：在最近任务里划掉应用时，服务不被销毁——
 *    这是「划掉后台仍能提醒」的关键，也是微信的表现；
 * 3. **`START_STICKY`**：进程若被系统回收，内存允许时自动重建服务并继续按计划提醒；
 * 4. **服务内计时**：用 [Handler] 在进程内计时，到点由服务直接投递通知，不在中途依赖外部调度。
 *
 * 与 [ReminderScheduler] 的分工：闹钟只负责「在设备休眠（Doze）时把 CPU 唤醒」，
 * 唤醒后仍由本服务（或 [ReminderReceiver]）投递同一条通知，两者按「已经投递过」去重，
 * 不会重复提醒。详见 [ReminderDelivered]。
 */
class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val onTimer = Runnable { onTimerFired() }

    private lateinit var repository: TimetableRepository

    /** 当前已在进程内排定的下一条提醒 */
    private var pending: ReminderEvent? = null

    override fun onCreate() {
        super.onCreate()
        repository = TimetableRepository(applicationContext)
        ReminderNotifications.ensureChannels(this)
        instance = this
        // 必须尽快完成 startForeground，否则系统会认为服务启动失败
        goForeground(ReminderNotifications.keepAliveNotification(this, null))
        refresh()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = repository.settings.value
        val stopRequested = intent?.action == ACTION_STOP
        if (stopRequested || !settings.reminderEnabled || !settings.keepAliveEnabled) {
            shutdown()
            return START_NOT_STICKY
        }
        // START_STICKY 重建时 intent 可能为 null，此时同样刷新一次计划即可
        goForeground(ReminderNotifications.keepAliveNotification(this, null))
        repository.reload()
        refresh()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 用户在最近任务里划掉了应用。
     *
     * 由于清单中声明了 `stopWithTask="false"`，本服务不会随之销毁，
     * 这里只需刷新计时器，并确认[ReminderScheduler]的唤醒闹钟仍然有效
     * ——如果设备随后进入深度休眠，仍需要它把 CPU 叫醒。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        ReminderScheduler.rescheduleFromStore(applicationContext)
        refresh()
    }

    override fun onDestroy() {
        handler.removeCallbacks(onTimer)
        pending = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    //  计划与投递
    // ------------------------------------------------------------------

    /** 重新读取课表与设置，排定下一条提醒并同步常驻通知的文案 */
    private fun refresh() {
        handler.removeCallbacks(onTimer)
        pending = null

        val settings = repository.settings.value
        val courses = repository.courses.value
        if (!settings.canScheduleReminder) {
            updateForeground(null)
            return
        }

        val now = LocalDateTime.now()
        val next = ReminderPlanner
            .plan(courses, settings, now.toLocalDate(), now)
            .firstOrNull { !ReminderDelivered.wasDelivered(this, it) }

        updateForeground(next)
        if (next != null) armTimer(next, now)
    }

    /**
     * 在进程内排一个计时器。
     *
     * 计时器基于 `uptimeMillis`，设备进入深度休眠时不会推进——那种情况下由唤醒闹钟
     * 负责把通知送达，本计时器醒来后会被去重逻辑拦住，不会重复投递。
     */
    private fun armTimer(event: ReminderEvent, now: LocalDateTime) {
        val delayMs = Duration.between(now, event.triggerAt).toMillis()
        if (delayMs <= 0L) return
        pending = event
        handler.postDelayed(onTimer, delayMs)
    }

    private fun onTimerFired() {
        val event = pending
        pending = null
        if (event != null && repository.settings.value.canScheduleReminder) {
            ReminderDelivered.deliver(this, event)
        }
        refresh()
    }

    // ------------------------------------------------------------------
    //  前台状态
    // ------------------------------------------------------------------

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, ReminderNotifications.KEEP_ALIVE_ID, notification, type)
        }
    }

    private fun updateForeground(next: ReminderEvent?) {
        runCatching {
            NotificationManagerCompat.from(this).notify(
                ReminderNotifications.KEEP_ALIVE_ID,
                ReminderNotifications.keepAliveNotification(this, next)
            )
        }
    }

    private fun shutdown() {
        handler.removeCallbacks(onTimer)
        pending = null
        if (instance === this) instance = null
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.lyl.timetable.action.START_SERVICE"
        const val ACTION_STOP = "com.lyl.timetable.action.STOP_SERVICE"

        /**
         * 正在运行的服务实例。同一进程内可见，用于在不重启服务的前提下刷新计划
         * （避免频繁调用 `startForegroundService`）。
         */
        @Volatile
        private var instance: ReminderService? = null

        /** 提醒服务当前是否在运行 */
        val isRunning: Boolean get() = instance != null

        /**
         * 确保服务在运行；若已在运行则只刷新计划。
         *
         * 调用方只需关心「提醒该不该工作」，不必判断服务状态。
         */
        fun ensureRunning(context: Context) {
            val live = instance
            if (live != null) {
                live.refresh()
                return
            }
            val app = context.applicationContext
            runCatching {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, ReminderService::class.java).setAction(ACTION_START)
                )
            }
        }

        /** 停止服务（关闭提醒开关或关闭后台常驻时调用） */
        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching { app.stopService(Intent(app, ReminderService::class.java)) }
            if (instance != null) instance = null
        }
    }
}
