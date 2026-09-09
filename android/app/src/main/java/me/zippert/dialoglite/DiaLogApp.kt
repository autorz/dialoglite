package me.zippert.dialoglite

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.zippert.dialoglite.sync.SyncScheduler

class DiaLogApp : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch { container.repository.primeBaseUrl() }
        SyncScheduler.schedulePeriodic(this)
    }
}
