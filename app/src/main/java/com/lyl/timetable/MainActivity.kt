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

        setContent {
            AppRoot(repository = repository, incomingUri = incomingUri)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 再次以「用课程表打开」方式启动时，交由 Activity 重建流程处理
        setIntent(intent)
    }
}
