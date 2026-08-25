package com.avsp.pro

import android.app.Application
import com.avsp.pro.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AvspApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            runCatching {
                container.moduleStatusRepository.ensureDefaults()
                container.logger.info("M1", "AVSP application initialized")
            }
        }
    }
}
