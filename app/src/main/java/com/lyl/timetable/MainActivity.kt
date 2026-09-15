package com.lyl.timetable

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lyl.timetable.data.TimetableRepository
import com.lyl.timetable.ui.AppRoot

class MainActivity : ComponentActivity() {

    private lateinit var repository: TimetableRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        repository = TimetableRepository(applicationContext)

        val incomingUri = intent?.let { intent ->
            if (intent.action == Intent.ACTION_VIEW) intent.data?.toString() else null
        }
        val openToday = intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true

        setContent {
            AppRoot(
                repository = repository,
                incomingUri = incomingUri,
                openToday = openToday
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 再次以「用课程表打开」方式启动时，交由 Activity 重建流程处理
        setIntent(intent)
    }

    companion object {
        /** 点击提醒通知进入应用时直接落到「今日」页 */
        const val EXTRA_OPEN_TODAY = "open_today"
    }
}
