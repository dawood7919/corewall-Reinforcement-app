package com.corewall.qaqc

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    /**
     * نطاق يعيش مع التطبيق — للتسخين وقت التشغيل بس.
     * `SupervisorJob` عشان فشل تسخين مايوقّعش أي حاجة تانية.
     */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // الإنشاء هنا رخيص: كله كائنات بتلفّ DAOs. القراية الفعلية
        // للأصول وفتح قاعدة البيانات مؤجّلين.
        repository = AppRepository(this)
        settingsStore = SettingsStore(this)
        filesManager = FilesManager(this)
        val db = AppDatabase.get(this)
        aiRepository = AiRepository(db.aiAnalysisDao())
        aiEngine = AiEngine(db.documentDao(), db.docFactDao(), db.chatMessageDao(), db.promptDao())
        fileLibrary = FileLibrary(db.fileMetaDao(), db.linkDao())

        // التسخين في الخلفية: فكّ الـJSON وفتح القاعدة بيحصلوا بالتوازي
        // مع رسم أول شاشة بدل ما يتأخّروها.
        startupScope.launch {
            runCatching { repository.warmUp() }
            runCatching { db.openHelper.readableDatabase }
            // المهملات بتتنضّف مرة عند التشغيل — بعد فتح القاعدة عشان
            // ما تتأخّرش أول شاشة عشان حاجة محدش مستنيها.
            runCatching { repository.purgeOldNoteTrash() }
            // المنبّهات بتضيع مع إعادة التشغيل أو مع إيقاف التطبيق قسري —
            // بنعيد بناءها من القاعدة، ودي عملية رخيصة (صف واحد فيه تذكير).
            runCatching { com.corewall.qaqc.notify.NoteReminders.ensureChannel(this@CoreWallApp) }
            runCatching { com.corewall.qaqc.notify.NoteReminders.rescheduleAll(this@CoreWallApp) }
        }
    }
}
