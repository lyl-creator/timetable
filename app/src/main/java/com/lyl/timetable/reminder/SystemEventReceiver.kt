package com.lyl.timetable.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 系统事件接收器：闹钟在重启、改时间、改时区、应用升级后都会失效或被清空，这里负责重排。
 *
 * 过滤的都是受保护广播（只能由系统发出），因此声明为 `exported="true"` 不会带来伪造风险；
 * 本接收器也只做「重新排布闹钟」这一件事，从不直接投递通知。
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action in HANDLED_ACTIONS) {
            ReminderScheduler.rescheduleFromStore(context)
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ReminderScheduler.ACTION_REARM
        )
    }
}
