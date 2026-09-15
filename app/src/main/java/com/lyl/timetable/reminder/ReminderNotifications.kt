package com.lyl.timetable.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lyl.timetable.R
import com.lyl.timetable.data.Course
import com.lyl.timetable.data.TimeText

/** 上课提醒的通知渠道与通知构建 */
object ReminderNotifications {

    /** 上课提醒（会响铃 / 振动） */
    const val CHANNEL_ID = "course_reminder"
    private const val CHANNEL_NAME = "上课提醒"

    /** 服务运行状态（完全静音，仅用于满足前台服务的常驻通知要求） */
    const val SERVICE_CHANNEL_ID = "service_status"
    private const val SERVICE_CHANNEL_NAME = "后台运行状态"

    /** 常驻通知的固定 id，服务更新它时不会产生第二条通知 */
    const val KEEP_ALIVE_ID = 1

    /** 渠道只需创建一次，重复创建同 id 不会覆盖用户已修改的通知设置 */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "按设定的提前时间提醒即将开始的课程"
                        enableVibration(true)
                        setShowBadge(true)
                    }
            )
        }

        if (manager.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    SERVICE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "保持提醒准时送达。静音、不振动，可折叠在通知栏底部"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
    }

    /**
     * 前台服务的常驻通知：静音、不打扰，仅表示「提醒功能正在后台工作」，
     * 并在展开时告知下一次提醒的时间。
     */
    fun keepAliveNotification(context: Context, next: ReminderEvent?): Notification {
        val text = if (next != null) {
            "下次提醒 ${TimeText.format(next.triggerAt.toLocalTime())}  ${next.title}"
        } else {
            "已开启，正在按课表提醒"
        }
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lesson)
            .setContentTitle("上课提醒已开启")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(ReminderReceiver.contentIntent(context))
            .build()
    }

    /** 由服务直接投递提醒（应用自发通知的主通道） */
    fun showEvent(context: Context, event: ReminderEvent) {
        val app = context.applicationContext
        ensureChannels(app)
        post(
            context = app,
            name = event.courseName,
            teacher = event.teacher,
            location = event.location,
            startText = event.startText,
            endText = event.endText,
            dayOfWeek = event.dayOfWeek,
            startSection = event.startSection,
            endSection = event.endSection,
            lead = event.leadMinutes
        )
    }

    /** 依据闹钟携带的课程信息投递通知（设备休眠时被唤醒的兜底通道） */
    fun show(context: Context, intent: Intent) {
        val app = context.applicationContext
        ensureChannels(app)
        post(
            context = app,
            name = intent.getStringExtra(ReminderScheduler.EXTRA_NAME).orEmpty(),
            teacher = intent.getStringExtra(ReminderScheduler.EXTRA_TEACHER).orEmpty(),
            location = intent.getStringExtra(ReminderScheduler.EXTRA_LOCATION).orEmpty(),
            startText = intent.getStringExtra(ReminderScheduler.EXTRA_START_TEXT).orEmpty(),
            endText = intent.getStringExtra(ReminderScheduler.EXTRA_END_TEXT).orEmpty(),
            dayOfWeek = intent.getIntExtra(ReminderScheduler.EXTRA_DAY, 0),
            startSection = intent.getIntExtra(ReminderScheduler.EXTRA_START_SECTION, 0),
            endSection = intent.getIntExtra(ReminderScheduler.EXTRA_END_SECTION, 0),
            lead = intent.getIntExtra(ReminderScheduler.EXTRA_LEAD, 0)
        )
    }

    private fun post(
        context: Context,
        name: String,
        teacher: String,
        location: String,
        startText: String,
        endText: String,
        dayOfWeek: Int,
        startSection: Int,
        endSection: Int,
        lead: Int
    ) {
        val title = name.ifBlank { "上课提醒" }
        val headline = if (lead > 0) "$lead 分钟后开始" else "现在开始"
        val summary = buildList {
            add(headline)
            if (startText.isNotBlank() && endText.isNotBlank()) add("$startText-$endText")
            if (location.isNotBlank()) add(location)
        }.joinToString("  ·  ")

        val detail = buildList {
            if (dayOfWeek in 1..Course.DAY_NAMES.size) add(Course.DAY_NAMES[dayOfWeek - 1])
            if (startSection > 0 && endSection >= startSection) {
                add(if (startSection == endSection) "$startSection 节" else "$startSection-$endSection 节")
            }
            if (teacher.isNotBlank()) add(teacher)
        }.joinToString("  ·  ")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lesson)
            .setContentTitle(title)
            .setContentText(summary)
            .setSubText(detail.ifBlank { null })
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (detail.isBlank()) summary else "$summary\n$detail"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(ReminderReceiver.contentIntent(context))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                notificationId(title, dayOfWeek, startSection),
                notification
            )
        }
    }

    /** 同一门课在同一时段的提醒只占一个通知位，重排后不会堆积 */
    private fun notificationId(name: String, dayOfWeek: Int, startSection: Int): Int =
        1000 + (name.hashCode() % 100_000) * 31 + dayOfWeek * 7 + startSection
}
