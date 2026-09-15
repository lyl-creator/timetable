package com.lyl.timetable.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lyl.timetable.R
import com.lyl.timetable.data.Course

/** 上课提醒的通知渠道与通知构建 */
object ReminderNotifications {

    const val CHANNEL_ID = "course_reminder"
    private const val CHANNEL_NAME = "上课提醒"

    /** 渠道只需创建一次，重复创建同 id 不会覆盖用户已修改的通知设置 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "按设定的提前时间提醒即将开始的课程"
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** 依据闹钟携带的课程信息投递通知 */
    fun show(context: Context, intent: Intent) {
        val app = context.applicationContext
        ensureChannel(app)

        val name = intent.getStringExtra(ReminderScheduler.EXTRA_NAME).orEmpty()
            .ifBlank { "上课提醒" }
        val teacher = intent.getStringExtra(ReminderScheduler.EXTRA_TEACHER).orEmpty()
        val location = intent.getStringExtra(ReminderScheduler.EXTRA_LOCATION).orEmpty()
        val startText = intent.getStringExtra(ReminderScheduler.EXTRA_START_TEXT).orEmpty()
        val endText = intent.getStringExtra(ReminderScheduler.EXTRA_END_TEXT).orEmpty()
        val dayOfWeek = intent.getIntExtra(ReminderScheduler.EXTRA_DAY, 0)
        val startSection = intent.getIntExtra(ReminderScheduler.EXTRA_START_SECTION, 0)
        val endSection = intent.getIntExtra(ReminderScheduler.EXTRA_END_SECTION, 0)
        val lead = intent.getIntExtra(ReminderScheduler.EXTRA_LEAD, 0)

        val headline = if (lead > 0) "$lead 分钟后开始" else "现在开始"
        val summary = buildList {
            add(headline)
            if (startText.isNotBlank() && endText.isNotBlank()) add("$startText-$endText")
            if (location.isNotBlank()) add(location)
        }.joinToString("  ·  ")

        val detail = buildList {
            if (dayOfWeek in 1..Course.DAY_NAMES.size) {
                add(Course.DAY_NAMES[dayOfWeek - 1])
            }
            if (startSection > 0 && endSection >= startSection) {
                add(if (startSection == endSection) "$startSection 节" else "$startSection-$endSection 节")
            }
            if (teacher.isNotBlank()) add(teacher)
        }.joinToString("  ·  ")

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lesson)
            .setContentTitle(name)
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
            .setContentIntent(ReminderReceiver.contentIntent(app))
            .build()

        runCatching {
            NotificationManagerCompat.from(app).notify(
                notificationId(name, dayOfWeek, startSection),
                notification
            )
        }
    }

    /** 同一门课在同一时段的提醒只占一个通知位，重排后不会堆积 */
    private fun notificationId(name: String, dayOfWeek: Int, startSection: Int): Int =
        1000 + (name.hashCode() % 100_000) * 31 + dayOfWeek * 7 + startSection
}
