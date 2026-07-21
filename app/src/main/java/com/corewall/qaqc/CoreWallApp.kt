package com.corewall.qaqc

import android.app.Application
import com.corewall.qaqc.data.AppRepository
import com.corewall.qaqc.data.SettingsStore

class CoreWallApp : Application() {
    lateinit var repository: AppRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        settingsStore = SettingsStore(this)
    }
}
