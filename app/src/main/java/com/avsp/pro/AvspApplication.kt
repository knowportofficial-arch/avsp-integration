package com.avsp.pro

import android.app.Application
import com.avsp.pro.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.avsp.pro.m7.M7Services

class AvspApplication : Application() {
    lateinit var container: AppContainer
        private set

    lateinit var m7Services: M7Services
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        m7Services = M7Services(this)
        appScope.launch {
            runCatching {
                container.moduleStatusRepository.ensureDefaults()
                container.logger.info("M1", "AVSP application initialized")
            }
        }
    }
}
