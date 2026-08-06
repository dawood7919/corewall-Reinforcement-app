package com.corewall.qaqc

import android.app.Application
import com.corewall.qaqc.ai.AiEngine
import com.corewall.qaqc.ai.AiRepository
import com.corewall.qaqc.data.AppRepository
import com.corewall.qaqc.data.FileLibrary
import com.corewall.qaqc.data.FilesManager
import com.corewall.qaqc.data.SettingsStore
import com.corewall.qaqc.data.db.AppDatabase

class CoreWallApp : Application() {
    lateinit var repository: AppRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var filesManager: FilesManager
        private set
    lateinit var aiRepository: AiRepository
        private set
    lateinit var aiEngine: AiEngine
        private set
    lateinit var fileLibrary: FileLibrary
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        settingsStore = SettingsStore(this)
        filesManager = FilesManager(this)
        val db = AppDatabase.get(this)
        aiRepository = AiRepository(db.aiAnalysisDao())
        aiEngine = AiEngine(db.documentDao(), db.docFactDao(), db.chatMessageDao(), db.promptDao())
        fileLibrary = FileLibrary(db.fileMetaDao(), db.linkDao())
    }
}
