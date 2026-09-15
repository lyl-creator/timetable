package com.lyl.timetable.reminder

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 上课提醒所需的系统授权状态与跳转入口。
 *
 * 三项授权分别影响不同环节，缺任何一项都会削弱提醒的可靠性：
 * - 通知权限（Android 13 起）：缺失时闹钟仍会准时触发，但看不到任何提示；
 * - 精确闹钟：被系统收回后退化为非精确闹钟，可能晚几分钟；
 * - 电池优化白名单：部分 ROM 的后台限制较激进，加入白名单可显著提高存活率。
 */
object ReminderPermissions {

    /** 是否已获得通知权限（Android 13 以下默认具备） */
    fun notificationsGranted(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 是否需要运行时申请通知权限（Android 13 起） */
    fun needsNotificationRequest(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** 精确闹钟是否可用 */
    fun exactAlarmAllowed(context: Context): Boolean = ReminderScheduler.canScheduleExact(context)

    /** 系统是否会限制本应用的后台运行（尚未加入电池优化白名单时为 true） */
    fun batteryRestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return !power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 跳转本应用的通知设置 */
    fun notificationSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APP_NOTIFICATION_SETTINGS
    ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** 跳转精确闹钟授权（Android 12 起为特殊权限，需在系统设置中确认） */
    fun exactAlarmSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
        } else {
            appDetailsIntent(context)
        }

    /** 申请把本应用加入电池优化白名单 */
    @SuppressLint("BatteryLife")
    fun batteryOptimizationIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    ).setData(Uri.fromParts("package", context.packageName, null))

    /** 跳转应用详情页（部分 ROM 的电池优化入口在系统设置中另寻） */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
}
