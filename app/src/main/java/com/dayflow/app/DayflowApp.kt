package com.dayflow.app

import android.app.Application
import com.dayflow.app.data.Repo
import com.dayflow.app.notify.Notifier
import com.dayflow.app.notify.schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DayflowApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        Repo.setup(this)                         // handles only; UI flows can subscribe immediately
        scope.launch { Repo.init(this@DayflowApp) } // seeding + daily roll off the main thread
        Notifier.init(this); schedule(this)
    }
}
