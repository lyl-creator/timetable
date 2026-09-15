package com.lyl.timetable

import android.app.Application
import com.lyl.timetable.reminder.ReminderNotifications

class TimetableApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 通知渠道需在投递前创建；这里提前建好，进程被系统拉起时也能立刻发通知
        ReminderNotifications.ensureChannels(this)
    }
}
