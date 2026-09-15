package com.lyl.timetable.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 上课提醒的兜底投递入口。
 *
 * 正常情况由 [ReminderService] 在前台服务内计时并直接投递通知；本接收器只在
 * 「设备深度休眠、服务内计时器不推进」时被 [ReminderScheduler] 的唤醒闹钟拉起，
 * 负责把 CPU 唤醒后的这一条提醒补上。
 *
 * 两条通道共用 [ReminderDelivered] 的去重记录，因此不会重复提醒。
 * 声明为 `exported="false"`：只有系统持有时钟触发的本应用闹钟才能触发它，
 * 其他应用无法伪造提醒。
 *
 * 课程信息通过 Intent 附加数据传入，因此这里不需要访问数据库，
 * 进程被系统重新拉起后可在极短时间内完成通知投递。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_REMIND -> {
                ReminderDelivered.deliverFromIntent(context, intent)
                // 闹钟是一次性的，投递后需要把后续提醒重新排上
                ReminderScheduler.rescheduleFromStore(context)
            }

            ReminderScheduler.ACTION_REARM -> ReminderScheduler.rescheduleFromStore(context)
        }
    }

    /** 打开应用时需要携带的跳转信息（点击通知进入「今日」） */
    companion object {
        internal fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, com.lyl.timetable.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(com.lyl.timetable.MainActivity.EXTRA_OPEN_TODAY, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
