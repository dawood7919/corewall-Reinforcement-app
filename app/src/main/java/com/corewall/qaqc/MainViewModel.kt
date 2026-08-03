package com.corewall.qaqc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.corewall.qaqc.ai.aiMessage
import com.corewall.qaqc.data.AppRepository
import com.corewall.qaqc.data.AppSettings
import com.corewall.qaqc.data.FilesManager
import com.corewall.qaqc.data.SettingsStore
import com.corewall.qaqc.data.db.AttendanceFileEntity
import com.corewall.qaqc.data.db.BarCountEntity
import com.corewall.qaqc.data.db.CommentEntity
import com.corewall.qaqc.data.db.DailyAttendanceEntity
import com.corewall.qaqc.data.db.ElementAttachmentEntity
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.data.db.PdfAnnotationEntity
import com.corewall.qaqc.data.db.SitePhotoEntity
import com.corewall.qaqc.data.db.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.corewall.qaqc.data.model.PlanElement
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.domain.AttentionDiff
import com.corewall.qaqc.domain.AttentionItem
import com.corewall.qaqc.domain.ScheduleLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Lens(val label: String) {
    REINF("التسليح"),
    COUNT("العدّ"),
    DATA("الداتا")
}

enum class Section(val title: String) {
    COREWALL("Corewall"),
    MANPOWER("Manpower")
}

enum class AppScreen {
    NOTIFICATIONS, SETTINGS, SYNC, ABOUT, FLOOR_NOTES, SITE_PHOTOS, AI_ANALYSIS, AI_SETTINGS, AI_CHAT, AI_KNOWLEDGE, AI_REPORTS
}

const val FLOOR_NOTE_ID = "__FLOOR__"

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo: AppRepository = (app as CoreWallApp).repository
    val files: FilesManager = (app as CoreWallApp).filesManager
    private val settingsStore: SettingsStore = (app as CoreWallApp).settingsStore

    val planData = repo.planData
    val logic = ScheduleLogic(repo.baseSchedule.levels)
    val levels: List<String> = repo.baseSchedule.levels

    val orderedElements: List<PlanElement> = planData.elements.sortedBy {
        it.id.removePrefix("s").toIntOrNull() ?: Int.MAX_VALUE
    }

    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val _section = MutableStateFlow(Section.COREWALL)
    val section: StateFlow<Section> = _section

    fun setSection(s: Section) {
        if (_section.value == s) return
        _section.value = s
        _tabIndex.value = 0
        _selectedElementId.value = null
        _openAttendanceFileId.value = null
    }

    private val _lens = MutableStateFlow(Lens.REINF)
    val lens: StateFlow<Lens> = _lens

    private val _tabIndex = MutableStateFlow(0)
    val tabIndex: StateFlow<Int> = _tabIndex

    private val tabHistory = ArrayDeque<Int>()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    private val _currentLevel = MutableStateFlow(
        settingsStore.getLastLevel()?.takeIf { it in levels } ?: levels.firstOrNull() ?: "GROUND"
    )
    val currentLevel: StateFlow<String> = _currentLevel

    private val _namingMode = MutableStateFlow(false)
    val namingMode: StateFlow<Boolean> = _namingMode

    private val _selectedElementId = MutableStateFlow<String?>(null)
    val selectedElementId: StateFlow<String?> = _selectedElementId

    val schedule: StateFlow<ScheduleData> = repo.rangeEdits
        .map { repo.applyEdits(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.baseSchedule)

    val names: StateFlow<Map<String, String>> = repo.names
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val inspections: StateFlow<Map<Pair<String, String>, String>> = repo.inspections
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val comments: StateFlow<List<CommentEntity>> = repo.comments
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val editedRowKeys: StateFlow<Set<Pair<String, Int>>> = repo.rangeEdits
        .map { edits -> edits.map { it.mark to it.rowIndex }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun setLens(lens: Lens) {
        _lens.value = lens
        if (lens != Lens.REINF) _namingMode.value = false
    }

    fun goToLens(lens: Lens) {
        setSection(Section.COREWALL)
        setLens(lens)
        setTabIndex(1)
    }

    fun goToManpower() {
        setSection(Section.MANPOWER)
        setTabIndex(0)
    }

    fun goToCorewallTab(index: Int) {
        setSection(Section.COREWALL)
        setTabIndex(index)
    }

    fun setTabIndex(index: Int) {
        if (index == _tabIndex.value) return
        tabHistory.addLast(_tabIndex.value)
        if (tabHistory.size > 24) tabHistory.removeFirst()
        _tabIndex.value = index
        _canGoBack.value = true
    }

    fun popTab(): Boolean {
        val prev = tabHistory.removeLastOrNull() ?: return false
        _tabIndex.value = prev
        _canGoBack.value = tabHistory.isNotEmpty()
        return true
    }

    fun setLevel(level: String) {
        if (level in levels) {
            _currentLevel.value = level
            settingsStore.setLastLevel(level)
        }
    }

    fun stepLevel(delta: Int) {
        val idx = levels.indexOf(_currentLevel.value)
        val next = (idx + delta).coerceIn(0, levels.size - 1)
        _currentLevel.value = levels[next]
        settingsStore.setLastLevel(levels[next])
    }

    fun setNamingMode(enabled: Boolean) { _namingMode.value = enabled }
    fun selectElement(id: String?) { _selectedElementId.value = id }

    fun saveName(elementId: String, mark: String) {
        viewModelScope.launch {
            repo.setName(elementId, mark)
            _selectedElementId.value = null
        }
    }

    fun openNextUnnamed() {
        val named = names.value.keys
        val currentIdx = orderedElements.indexOfFirst { it.id == _selectedElementId.value }
        val order = orderedElements.indices.map { (currentIdx + 1 + it) % orderedElements.size }
        val next = order.map { orderedElements[it] }.firstOrNull { it.id !in named }
        _selectedElementId.value = next?.id
    }

    fun setInspection(elementId: String, status: String) {
        viewModelScope.launch { repo.setInspection(elementId, _currentLevel.value, status) }
    }

    fun addComment(elementId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.addComment(elementId, _currentLevel.value, text.trim()) }
    }

    fun deleteComment(id: Long) {
        viewModelScope.launch { repo.deleteComment(id) }
    }

    fun saveRangeEdit(mark: String, rowIndex: Int, values: Map<String, String>, baseValues: Map<String, String>) {
        val patch = values.filter { (k, v) -> baseValues[k] != v }
        viewModelScope.launch { repo.saveRangeEdit(mark, rowIndex, patch) }
    }

    fun clearRangeEdit(mark: String, rowIndex: Int) {
        viewModelScope.launch { repo.clearRangeEdit(mark, rowIndex) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    val barCounts: StateFlow<List<BarCountEntity>> = repo.barCounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveBarCounts(elementId: String, entries: List<BarCountEntity>) {
        viewModelScope.launch {
            repo.replaceBarCounts(elementId, _currentLevel.value, entries)
            _selectedElementId.value = null
        }
    }

    val attachments: StateFlow<List<ElementAttachmentEntity>> = repo.attachments
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addDataComment(elementId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repo.addAttachment(
                ElementAttachmentEntity(
                    elementId = elementId,
                    level = _currentLevel.value,
                    type = ElementAttachmentEntity.TYPE_COMMENT,
                    text = text.trim(),
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun addDataFiles(elementId: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val level = _currentLevel.value
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                files.importUris(uris, files.attachmentsDir(level, elementId))
                    .also { registerFiles(it, level) }
            }
            copied.forEach { file ->
                repo.addAttachment(
                    ElementAttachmentEntity(
                        elementId = elementId,
                        level = level,
                        type = ElementAttachmentEntity.TYPE_FILE,
                        text = file.name,
                        filePath = file.absolutePath,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deleteAttachment(entity: ElementAttachmentEntity) {
        viewModelScope.launch { repo.deleteAttachment(entity) }
    }

    val tasks: StateFlow<List<TaskEntity>> =
        combine(repo.tasks, _currentLevel) { all, level ->
            all.filter { it.level == level }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun upsertTask(task: TaskEntity) {
        val bound = if (task.id == 0L) task.copy(level = _currentLevel.value) else task
        viewModelScope.launch { repo.upsertTask(bound) }
    }

    fun toggleTaskDone(task: TaskEntity) {
        viewModelScope.launch {
            repo.upsertTask(
                task.copy(
                    done = !task.done,
                    completedAt = if (!task.done) System.currentTimeMillis() else null
                )
            )
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch { repo.deleteTask(id) }
    }

    fun deleteCompletedTasks() {
        viewModelScope.launch { repo.deleteCompletedTasks() }
    }

    val notes: StateFlow<List<NoteEntity>> = repo.notes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _editingNote = MutableStateFlow<NoteEntity?>(null)
    val editingNote: StateFlow<NoteEntity?> = _editingNote

    fun openNoteEditor(elementId: String, existing: NoteEntity? = null) {
        val now = System.currentTimeMillis()
        _editingNote.value = existing ?: NoteEntity(
            elementId = elementId,
            level = _currentLevel.value,
            createdAt = now,
            updatedAt = now
        )
    }

    fun closeNoteEditor() { _editingNote.value = null }

    fun saveNote(note: NoteEntity) {
        viewModelScope.launch {
            val id = repo.saveNote(note.copy(updatedAt = System.currentTimeMillis()))
            _editingNote.value = if (note.id == 0L) note.copy(id = id) else note
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repo.deleteNote(note)
            _editingNote.value = null
        }
    }

    fun autosaveNote(note: NoteEntity, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.saveNote(note.copy(updatedAt = System.currentTimeMillis()))
            onSaved(id)
        }
    }

    private val _viewingImage = MutableStateFlow<String?>(null)
    val viewingImage: StateFlow<String?> = _viewingImage
    fun openImage(path: String) { _viewingImage.value = path }
    fun closeImage() { _viewingImage.value = null }

    // -------- Site Photos --------

    val sitePhotos: StateFlow<List<SitePhotoEntity>> =
        combine(repo.sitePhotos, _currentLevel) { all, level ->
            all.filter { it.level == level }.sortedByDescending { it.timestamp }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addSitePhoto(filePath: String, comment: String, folder: String = "") {
        viewModelScope.launch {
            repo.saveSitePhoto(
                SitePhotoEntity(
                    level = _currentLevel.value,
                    filePath = filePath,
                    comment = comment.trim(),
                    timestamp = System.currentTimeMillis(),
                    folder = folder.trim()
                )
            )
        }
    }

    // -------- مساعد الـ AI (معزول لكل دور زي باقي التطبيق) --------

    private val aiRepo: com.corewall.qaqc.ai.AiRepository = (app as CoreWallApp).aiRepository

    val aiConfig: StateFlow<com.corewall.qaqc.ai.AiConfig> = settingsStore.aiConfig

    private val _aiState = MutableStateFlow<com.corewall.qaqc.ai.model.AiUiState>(
        com.corewall.qaqc.ai.model.AiUiState.NotConfigured
    )
    val aiState: StateFlow<com.corewall.qaqc.ai.model.AiUiState> = _aiState

    private var aiJob: kotlinx.coroutines.Job? = null

    init {
        // أول ما الدور أو الإعدادات تتغيّر: اعرض الكاش فوراً (من غير أي طلب شبكة).
        viewModelScope.launch {
            combine(_currentLevel, settingsStore.aiConfig) { level, cfg -> level to cfg }
                .collect { (level, cfg) ->
                    loadCachedAi(level, cfg)
                    loadCachedDashboard(level, cfg)
                    loadKnowledge()
                    refreshSuggestions()
                    // أول ما يبقى فيه مفتاح: حلّل أي حاجة معلّقة (رفعها قبل المفتاح مثلاً)
                    if (cfg.isConfigured) autoAnalyze()
                }
        }
    }

    init {
        // الاقتراحات بتتبني من بيانات الجودة، فبتتحدّث مع أي تغيير فيها
        viewModelScope.launch {
            combine(repo.inspections, repo.tasks) { _, _ -> Unit }
                .collect { refreshSuggestions() }
        }
    }

    private suspend fun loadCachedAi(level: String, cfg: com.corewall.qaqc.ai.AiConfig) {
        val cached = aiRepo.cachedFor(level)
        _aiState.value = when {
            cached != null -> com.corewall.qaqc.ai.model.AiUiState.Ready(
                analysis = cached.first,
                level = level,
                model = cached.second.model,
                generatedAt = cached.second.createdAt,
                cached = true
            )
            !cfg.isConfigured -> com.corewall.qaqc.ai.model.AiUiState.NotConfigured
            else -> com.corewall.qaqc.ai.model.AiUiState.Idle
        }
    }

    /** بيبني لقطة الدور الحالي ويبعتها للـ AI. مفيش شبكة من غير مفتاح. */
    fun refreshAiAnalysis() {
        val cfg = settingsStore.aiConfig.value
        if (!cfg.isConfigured) {
            _aiState.value = com.corewall.qaqc.ai.model.AiUiState.NotConfigured
            return
        }
        if (aiJob?.isActive == true) return

        val previous = _aiState.value as? com.corewall.qaqc.ai.model.AiUiState.Ready
        _aiState.value = com.corewall.qaqc.ai.model.AiUiState.Loading

        aiJob = viewModelScope.launch {
            val level = _currentLevel.value
            val result = runCatching {
                val context = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    com.corewall.qaqc.ai.context.FloorContextBuilder.build(
                        project = "BHR Tower 1",
                        level = level,
                        planData = planData,
                        schedule = schedule.value,
                        logic = logic,
                        names = names.value,
                        inspections = inspections.value,
                        barCounts = barCounts.value,
                        notes = notes.value,
                        tasks = tasks.value,
                        attachments = attachments.value,
                        attendanceFiles = attendanceFiles.value,
                        dailyAttendance = dailyAttendance.value
                    )
                }
                aiRepo.analyze(cfg, context)
            }
            _aiState.value = result.fold(
                onSuccess = { (analysis, at) ->
                    com.corewall.qaqc.ai.model.AiUiState.Ready(
                        analysis = analysis, level = level, model = cfg.model,
                        generatedAt = at, cached = false
                    )
                },
                onFailure = { e ->
                    com.corewall.qaqc.ai.model.AiUiState.Error(e.aiMessage(), previous)
                }
            )
        }
    }

    fun updateSitePhotoComment(photo: SitePhotoEntity, comment: String) {
        viewModelScope.launch {
            repo.saveSitePhoto(photo.copy(comment = comment.trim()))
        }
    }

    fun deleteSitePhoto(photo: SitePhotoEntity) {
        viewModelScope.launch { repo.deleteSitePhoto(photo) }
    }

    // -------- محرّك المعرفة: تحليل المستندات + المحادثة الهندسية --------

    private val aiEngine: com.corewall.qaqc.ai.AiEngine = (app as CoreWallApp).aiEngine

    private val _chat = MutableStateFlow<List<com.corewall.qaqc.data.db.ChatMessageEntity>>(emptyList())
    val chat: StateFlow<List<com.corewall.qaqc.data.db.ChatMessageEntity>> = _chat

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError

    private val _documents = MutableStateFlow<List<com.corewall.qaqc.data.db.DocumentEntity>>(emptyList())
    val documents: StateFlow<List<com.corewall.qaqc.data.db.DocumentEntity>> = _documents

    private val _analyzing = MutableStateFlow(0)
    val analyzing: StateFlow<Int> = _analyzing

    /** بيحمّل المحادثة والمستندات بتاعة الدور الشغّال. */
    fun loadKnowledge() {
        viewModelScope.launch {
            val level = _currentLevel.value
            _chat.value = aiEngine.history(level)
            _documents.value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                (getApplication<CoreWallApp>()).let { app ->
                    com.corewall.qaqc.data.db.AppDatabase.get(app).documentDao().forLevel(level)
                }
            }
        }
    }

    /**
     * بيتنادى فور رفع أي ملف: بيسجّله في المعرفة وبيحلّله تلقائي
     * لو فيه مفتاح API (من غير مفتاح بيفضل PENDING من غير أي شبكة).
     */
    /**
     * نقطة دخول واحدة لأي ملف بيدخل التطبيق من أي مكان
     * (رفع، كاميرا، صور موقع، مرفقات ملاحظات، مرفقات عناصر).
     */
    fun registerFiles(files: List<java.io.File>, level: String = _currentLevel.value) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            files.forEach { f -> runCatching { aiEngine.register(f, level) } }
            loadKnowledge()
            autoAnalyze()
        }
    }

    /** بيحلّل أي مستند معلّق — بينادى تلقائي عند الرفع، عند فتح التطبيق، وعند إضافة المفتاح. */
    private var autoJob: kotlinx.coroutines.Job? = null
    private fun autoAnalyze() {
        val cfg = settingsStore.aiConfig.value
        if (!cfg.isConfigured || autoJob?.isActive == true) return
        autoJob = viewModelScope.launch {
            _analyzing.value = 1
            val n = runCatching { aiEngine.analyzePending(cfg, levels, max = 12) }.getOrDefault(0)
            _analyzing.value = 0
            loadKnowledge()
            // إشعار استباقي: الـAI بيقول اتحلّل إيه وإيه اللي لقاه
            if (n > 0) {
                val fresh = _documents.value.filter { it.status == "DONE" }.sortedByDescending { it.analyzedAt }.take(n)
                if (fresh.isNotEmpty()) {
                    _uploadInsight.value = buildString {
                        append("حلّلت ")
                        append(fresh.size)
                        append(if (fresh.size == 1) " ملف جديد:\n" else " ملفات جديدة:\n")
                        fresh.forEach { d ->
                            append("• ")
                            append(d.fileName)
                            if (d.docType != "OTHER") append(" [" + d.docType + "]")
                            if (d.summary.isNotBlank()) { append(" — "); append(d.summary.take(120)) }
                            append("\n")
                        }
                    }.trim()
                }
                // اللوحة اتغيّرت لأن فيه معرفة جديدة
                refreshDashboard()
            }
        }
    }

    fun onFilesImported(files: List<java.io.File>) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            val level = _currentLevel.value
            files.forEach { f -> runCatching { aiEngine.register(f, level) } }
            loadKnowledge()
            val cfg = settingsStore.aiConfig.value
            if (cfg.isConfigured) {
                _analyzing.value = _analyzing.value + files.size
                runCatching { aiEngine.analyzePending(cfg, levels, max = files.size.coerceAtMost(6)) }
                _analyzing.value = 0
                loadKnowledge()
            }
        }
    }

    /**
     * تحليل يدوي: بيرجّع كمان أي مستند فشل لقائمة الانتظار،
     * عشان زرار واحد يكفي لإعادة المحاولة بعد إصلاح المفتاح أو الموديل.
     */
    fun analyzePendingDocuments() {
        viewModelScope.launch {
            _documents.value
                .filter { it.status == "FAILED" }
                .forEach { runCatching { aiEngine.reset(it.id) } }
            autoAnalyze()
        }
    }

    /** إعادة محاولة مستند فشل أو معلّق. */
    fun reanalyzeDocument(docId: Long) {
        val cfg = settingsStore.aiConfig.value
        if (!cfg.isConfigured) return
        viewModelScope.launch {
            _analyzing.value = 1
            runCatching { aiEngine.reset(docId) }
            runCatching { aiEngine.analyze(cfg, docId, levels) }
            _analyzing.value = 0
            loadKnowledge()
        }
    }

    /** حقائق مستخرجة من مستند — للعرض في شاشة المعرفة. */
    suspend fun factsFor(docId: Long) = aiEngine.factsFor(docId)

    /**
     * سؤال للمساعد — بيشتغل كوكيل: بيشوف حالة التطبيق، بينفّذ أدوات
     * قراءة عشان يجيب الحقايق، وبيقترح إجراءات التعديل للموافقة.
     */
    fun askAi(question: String) {
        val q = question.trim()
        if (q.isBlank() || _chatBusy.value) return
        val cfg = settingsStore.aiConfig.value
        if (!cfg.isConfigured) { _chatError.value = "ضيف مفتاح API من إعدادات المساعد الذكي الأول."; return }

        _chatBusy.value = true
        _chatError.value = null
        _agentStatus.value = "بيقرا حالة الدور…"
        viewModelScope.launch {
            val level = _currentLevel.value
            // نعرض سؤال المستخدم فوراً
            _chat.value = _chat.value + com.corewall.qaqc.data.db.ChatMessageEntity(
                level = level, role = "user", content = q, createdAt = System.currentTimeMillis()
            )

            val appState = withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.corewall.qaqc.ai.agent.AppSnapshot.build(
                    agentHost, files, _documents.value, currentScreenName()
                )
            }
            val knowledge = runCatching { aiEngine.knowledgeFor(level, q) }.getOrDefault("")
            val history = runCatching { aiEngine.historyDigest(level) }.getOrDefault("")

            runCatching {
                agentEngine.ask(cfg, q, appState, knowledge, history) { thought ->
                    _agentStatus.value = thought
                }
            }.onSuccess { run ->
                aiEngine.saveTurn(level, q, run.answer)
                if (run.executed.isNotEmpty()) {
                    _actionLog.value = (run.executed.reversed() + _actionLog.value).take(60)
                }
                _pendingActions.value = run.pending
            }.onFailure { e ->
                _chatError.value = e.aiMessage()
            }

            _chat.value = aiEngine.history(level)
            _agentStatus.value = null
            _chatBusy.value = false
            refreshSuggestions()
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            aiEngine.clearChat(_currentLevel.value)
            _chat.value = emptyList()
            _pendingActions.value = emptyList()
        }
    }

    fun dismissChatError() { _chatError.value = null }

    // ---------------------------------------------------------------- الوكيل

    /**
     * الشباك اللي الوكيل بيبصّ منه على التطبيق.
     *
     * كائن منفصل مش الـViewModel نفسه، عشان أسماء الحالة هنا (StateFlow)
     * ماتتلخبطش مع أسماء القيم اللحظية اللي الواجهة عايزاها.
     */
    private val agentHost = object : com.corewall.qaqc.ai.agent.AgentHost {
        override val levels get() = this@MainViewModel.levels
        override val currentLevel get() = _currentLevel.value
        override val schedule get() = this@MainViewModel.schedule.value
        override val logic get() = this@MainViewModel.logic
        override val planData get() = this@MainViewModel.planData
        override val names get() = this@MainViewModel.names.value
        override val inspections get() = this@MainViewModel.inspections.value
        override val comments get() = this@MainViewModel.comments.value
        override val barCounts get() = this@MainViewModel.barCounts.value
        override val tasks get() = this@MainViewModel.tasks.value
        override val notes get() = this@MainViewModel.notes.value
        override val sitePhotos get() = this@MainViewModel.sitePhotos.value
        override val dailyAttendance get() = this@MainViewModel.dailyAttendance.value

        override fun attendanceFileLabels(): Map<Long, String> =
            attendanceFiles.value.associate { it.id to "${it.company} — ${it.trade}" }

        override fun setLevel(level: String): Boolean {
            this@MainViewModel.setLevel(level); return true
        }

        override fun openScreen(screen: String): Boolean = navigateTo(screen)

        override fun openFile(path: String): Boolean {
            val f = java.io.File(path)
            if (!f.exists()) return false
            when (f.extension.lowercase()) {
                "pdf" -> openPdf(path)
                "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic" -> openImage(path)
                "dxf", "dwg" -> openCad(path)
                else -> return files.openExternally(f)
            }
            return true
        }

        override suspend fun addTask(title: String, level: String): Boolean = runCatching {
            repo.upsertTask(
                TaskEntity(title = title, level = level, createdAt = System.currentTimeMillis())
            )
        }.isSuccess

        override suspend fun addComment(elementId: String, text: String): Boolean = runCatching {
            repo.addComment(elementId, _currentLevel.value, text)
        }.isSuccess

        override suspend fun setInspection(elementId: String, status: String): Boolean = runCatching {
            repo.setInspection(elementId, _currentLevel.value, status)
        }.isSuccess

        override suspend fun deleteTask(id: Long): Boolean = runCatching { repo.deleteTask(id) }.isSuccess

        override fun elementIdForMark(mark: String): String? =
            names.entries.firstOrNull { it.value.equals(mark.trim(), ignoreCase = true) }?.key
    }

    private val agentEngine by lazy {
        com.corewall.qaqc.ai.agent.AgentEngine(
            com.corewall.qaqc.ai.agent.AgentExecutor(agentHost, files, aiEngine)
        )
    }

    /** سطر بيوضّح الوكيل بيعمل إيه دلوقتي. */
    private val _agentStatus = MutableStateFlow<String?>(null)
    val agentStatus: StateFlow<String?> = _agentStatus

    /** إجراءات مستنية موافقة المستخدم. */
    private val _pendingActions =
        MutableStateFlow<List<com.corewall.qaqc.ai.agent.PendingAction>>(emptyList())
    val pendingActions: StateFlow<List<com.corewall.qaqc.ai.agent.PendingAction>> = _pendingActions

    /** سجل كل إجراء الوكيل عمله — ظاهر وقابل للمراجعة. */
    private val _actionLog =
        MutableStateFlow<List<com.corewall.qaqc.ai.agent.ActionLogEntry>>(emptyList())
    val actionLog: StateFlow<List<com.corewall.qaqc.ai.agent.ActionLogEntry>> = _actionLog

    private val _copilotOpen = MutableStateFlow(false)
    val copilotOpen: StateFlow<Boolean> = _copilotOpen
    fun setCopilotOpen(open: Boolean) { _copilotOpen.value = open }

    private val _suggestions =
        MutableStateFlow<List<com.corewall.qaqc.ai.agent.Suggestion>>(emptyList())
    val suggestions: StateFlow<List<com.corewall.qaqc.ai.agent.Suggestion>> = _suggestions

    /**
     * بيحسب الاقتراحات محلياً — من غير أي نداء شبكة، فبيشتغل أوفلاين
     * وبيتحدّث فوراً مع أي تغيير في البيانات.
     */
    fun refreshSuggestions() {
        viewModelScope.launch {
            _suggestions.value = withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching {
                    com.corewall.qaqc.ai.agent.SuggestionEngine.build(agentHost, files, _documents.value)
                }.getOrDefault(emptyList())
            }
        }
    }

    /** بينفّذ إجراء بعد ما المستخدم وافق عليه. */
    fun confirmAction(id: Long) {
        val p = _pendingActions.value.firstOrNull { it.id == id } ?: return
        _pendingActions.value = _pendingActions.value.filterNot { it.id == id }
        viewModelScope.launch {
            val executor = com.corewall.qaqc.ai.agent.AgentExecutor(agentHost, files, aiEngine)
            val outcome = executor.run(p.action)
            _actionLog.value = (
                listOf(
                    com.corewall.qaqc.ai.agent.ActionLogEntry(
                        at = System.currentTimeMillis(),
                        tool = p.tool.name,
                        detail = p.action.describe(),
                        ok = outcome.ok,
                        auto = false
                    )
                ) + _actionLog.value
                ).take(60)
            _agentStatus.value = outcome.userMessage.ifBlank { null }
            loadKnowledge()
            refreshSuggestions()
        }
    }

    fun dismissAction(id: Long) {
        _pendingActions.value = _pendingActions.value.filterNot { it.id == id }
    }

    /** بيفتح أي ملف بالعارض المناسب — بيستخدمها الشات لما يعرض ملفات. */
    fun openAnyFile(path: String) {
        val f = java.io.File(path)
        if (!f.exists()) return
        when (f.extension.lowercase()) {
            "pdf" -> openPdf(path)
            "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic" -> openImage(path)
            "dxf", "dwg" -> openCad(path)
            else -> files.openExternally(f)
        }
    }

    /** اسم الشاشة المفتوحة — بيتبعت للوكيل عشان يعرف إنت فين. */
    private fun currentScreenName(): String = when (val s = _appScreen.value) {
        null -> when (_section.value) {
            Section.COREWALL -> when (_tabIndex.value) {
                0 -> "الرئيسية (Mission Control)"
                1 -> "المسقط — عدسة ${_lens.value.label}"
                2 -> "الملفات"
                3 -> "المهام"
                else -> "الإعدادات"
            }
            Section.MANPOWER -> when (_tabIndex.value) {
                0 -> "العمالة — الحضور"
                1 -> "العمالة — التقارير"
                2 -> "العمالة — الإحصائيات"
                else -> "الإعدادات"
            }
        }
        else -> "شاشة ${s.name}"
    }

    /** بيفتح شاشة بالاسم — الأسماء دي هي اللي الوكيل بيعرفها. */
    private fun navigateTo(screen: String): Boolean {
        when (screen.trim().uppercase()) {
            "HOME", "DASHBOARD" -> { setSection(Section.COREWALL); setTabIndex(0) }
            "PLAN", "SCHEDULE", "ATTENTION" -> goToLens(Lens.REINF)
            "COUNTING" -> goToLens(Lens.COUNT)
            "FILES" -> { setSection(Section.COREWALL); setTabIndex(2) }
            "TASKS" -> { setSection(Section.COREWALL); setTabIndex(3) }
            "MANPOWER" -> goToManpower()
            "NOTES" -> openAppScreen(AppScreen.FLOOR_NOTES)
            "PHOTOS" -> openAppScreen(AppScreen.SITE_PHOTOS)
            "KNOWLEDGE" -> openAppScreen(AppScreen.AI_KNOWLEDGE)
            "REPORTS" -> openAppScreen(AppScreen.AI_REPORTS)
            "CHAT" -> openAppScreen(AppScreen.AI_CHAT)
            "SETTINGS" -> openAppScreen(AppScreen.SETTINGS)
            else -> return false
        }
        return true
    }

    /** ملخّص نصّي مختصر لحالة الدور — الأرقام محسوبة، مش من الموديل. */
    private fun buildProjectSnapshot(level: String): String {
        val ctx = com.corewall.qaqc.ai.context.FloorContextBuilder.build(
            project = "BHR Tower 1", level = level,
            planData = planData, schedule = schedule.value, logic = logic,
            names = names.value, inspections = inspections.value,
            barCounts = barCounts.value, notes = notes.value, tasks = tasks.value,
            attachments = attachments.value, attendanceFiles = attendanceFiles.value,
            dailyAttendance = dailyAttendance.value
        )
        return buildString {
            appendLine("المشروع: BHR Tower 1 · الدور الشغّال: $level (${ctx.levelIndex + 1} من ${ctx.totalLevels})")
            appendLine("العناصر: ${ctx.elements.total} (حوائط ${ctx.elements.walls}، كمرات رابطة ${ctx.elements.couplingBeams}، كمرات داخلية ${ctx.elements.internalBeams})، مسمّى ${ctx.elements.named}")
            appendLine("الفحص: مقبول ${ctx.inspection.approved}، مصبوب ${ctx.inspection.cast}، WIR ${ctx.inspection.wirSubmitted}، مرفوض ${ctx.inspection.rejected}، بدون ${ctx.inspection.notInspected} — الإنجاز ${ctx.inspection.completionPercent}%")
            if (ctx.dataGaps.isNotEmpty())
                appendLine("فجوات بيانات: " + ctx.dataGaps.joinToString("، ") { "${it.mark} (ناقص ${it.missingLevels.joinToString("/")})" })
            appendLine("العمالة النهاردة: ${ctx.manpower.workersToday} عامل، ${ctx.manpower.foremenToday} فورمان، ${ctx.manpower.engineersToday} مهندس (${ctx.manpower.companies} شركة)")
            appendLine("التوثيق: ${ctx.documentation.notes} ملاحظة، ${ctx.documentation.attachments} مرفق، مهام مفتوحة ${ctx.documentation.openTasks}")
            if (ctx.reinforcement.isNotEmpty()) {
                appendLine("تسليح الدور (الصف الشغّال):")
                ctx.reinforcement.take(25).forEach { r ->
                    if (r.type == "WALL") appendLine("  ${r.mark}: سُمك ${r.widthMm}mm، رأسي ${r.vertical}، أفقي ${r.horizontal}، أطراف ${r.ties} — ${r.inspectionStatus}")
                    else appendLine("  ${r.mark}: ${r.widthMm}×${r.depthMm}mm، سفلي ${r.bottom}، علوي ${r.top}، كانات ${r.links} — ${r.inspectionStatus}")
                }
            }
            if (ctx.barCountChecks.isNotEmpty()) {
                appendLine("عدّ الأسياخ (موقع مقابل رسمة):")
                ctx.barCountChecks.take(15).forEach {
                    appendLine("  ${it.mark}: موقع ${it.siteTotals}، رسمة ${it.drawingTotals}، مطابق=${it.matches}")
                }
            }
        }
    }

    // -------- Phase 2: داشبورد ديناميكي + تقارير + إشعار استباقي --------

    private val aiCacheDao by lazy {
        com.corewall.qaqc.data.db.AppDatabase.get(getApplication()).aiAnalysisDao()
    }

    private val _dashboard = MutableStateFlow<com.corewall.qaqc.ai.model.DashboardState>(
        com.corewall.qaqc.ai.model.DashboardState.NotConfigured
    )
    val dashboard: StateFlow<com.corewall.qaqc.ai.model.DashboardState> = _dashboard

    /** رسالة استباقية بعد تحليل ملفات جديدة — "حلّلت كذا، ودي النتيجة". */
    private val _uploadInsight = MutableStateFlow<String?>(null)
    val uploadInsight: StateFlow<String?> = _uploadInsight
    fun dismissUploadInsight() { _uploadInsight.value = null }

    private var dashJob: kotlinx.coroutines.Job? = null

    private suspend fun loadCachedDashboard(level: String, cfg: com.corewall.qaqc.ai.AiConfig) {
        val cached = runCatching { aiEngine.cachedDashboard(level, aiCacheDao) }.getOrNull()
        _dashboard.value = when {
            cached != null -> com.corewall.qaqc.ai.model.DashboardState.Ready(
                cached.first, level, cached.second, cached = true
            )
            !cfg.isConfigured -> com.corewall.qaqc.ai.model.DashboardState.NotConfigured
            else -> com.corewall.qaqc.ai.model.DashboardState.Idle
        }
    }

    /** الـ AI يعيد بناء لوحة الدور حسب البيانات المتاحة دلوقتي. */
    fun refreshDashboard() {
        val cfg = settingsStore.aiConfig.value
        if (!cfg.isConfigured) {
            _dashboard.value = com.corewall.qaqc.ai.model.DashboardState.NotConfigured
            return
        }
        if (dashJob?.isActive == true) return
        _dashboard.value = com.corewall.qaqc.ai.model.DashboardState.Loading
        dashJob = viewModelScope.launch {
            val level = _currentLevel.value
            val snapshot = withContext(kotlinx.coroutines.Dispatchers.Default) { buildProjectSnapshot(level) }
            runCatching { aiEngine.buildDashboard(cfg, level, snapshot, aiCacheDao) }
                .onSuccess { (spec, at) ->
                    _dashboard.value = com.corewall.qaqc.ai.model.DashboardState.Ready(spec, level, at, cached = false)
                }
                .onFailure { e ->
                    _dashboard.value = com.corewall.qaqc.ai.model.DashboardState.Error(
                        e.aiMessage()
                    )
                }
        }
    }

    // ---- توليد المستندات ----

    private val _report = MutableStateFlow<com.corewall.qaqc.ai.model.GeneratedReport?>(null)
    val report: StateFlow<com.corewall.qaqc.ai.model.GeneratedReport?> = _report

    private val _reportBusy = MutableStateFlow(false)
    val reportBusy: StateFlow<Boolean> = _reportBusy

    private val _reportError = MutableStateFlow<String?>(null)
    val reportError: StateFlow<String?> = _reportError

    fun generateReport(kind: com.corewall.qaqc.ai.model.ReportKind) {
        val cfg = settingsStore.aiConfig.value
        if (!cfg.isConfigured) { _reportError.value = "ضيف مفتاح API الأول."; return }
        if (_reportBusy.value) return
        _reportBusy.value = true
        _reportError.value = null
        viewModelScope.launch {
            val level = _currentLevel.value
            val snapshot = withContext(kotlinx.coroutines.Dispatchers.Default) { buildProjectSnapshot(level) }
            runCatching { aiEngine.generateReport(cfg, level, kind, snapshot) }
                .onSuccess { _report.value = it }
                .onFailure { e ->
                    _reportError.value = e.aiMessage()
                }
            _reportBusy.value = false
        }
    }

    fun clearReport() { _report.value = null; _reportError.value = null }

    /** بيحفظ التقرير كملف Markdown في مكتبة الدور ويرجّع الملف للمشاركة. */
    fun saveReportToFiles(onSaved: (java.io.File?) -> Unit) {
        val r = _report.value ?: return onSaved(null)
        viewModelScope.launch {
            val f = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val dir = files.levelDir(_currentLevel.value)
                    val name = "${r.title}-${_currentLevel.value}-${System.currentTimeMillis()}.md"
                        .replace(Regex("[^\\p{L}\\p{N}._\\-]"), "_")
                    java.io.File(dir, name).apply { writeText(r.markdown) }
                }.getOrNull()
            }
            f?.let { registerFiles(listOf(it)) }
            onSaved(f)
        }
    }

    fun updateAiConfig(transform: (com.corewall.qaqc.ai.AiConfig) -> com.corewall.qaqc.ai.AiConfig) =
        settingsStore.updateAiConfig(transform)

    fun switchAiProvider(provider: com.corewall.qaqc.ai.AiProviderId) =
        settingsStore.switchAiProvider(provider)

    /** شاشة ملء-الشاشة الحالية (إشعارات/إعدادات/مزامنة/عن) — من القائمة الجانبية. */
    private val _appScreen = MutableStateFlow<AppScreen?>(null)
    val appScreen: StateFlow<AppScreen?> = _appScreen
    fun openAppScreen(screen: AppScreen) { _appScreen.value = screen }
    fun closeAppScreen() { _appScreen.value = null }

    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications
    fun setUnreadNotifications(n: Int) { _unreadNotifications.value = n }

    fun allMarks(): List<String> = repo.baseSchedule.allMarks

    val attendanceFiles: StateFlow<List<AttendanceFileEntity>> =
        combine(repo.attendanceFiles, _currentLevel) { all, level ->
            all.filter { it.level == level }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dailyAttendance: StateFlow<List<DailyAttendanceEntity>> = repo.dailyAttendance
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _openAttendanceFileId = MutableStateFlow<Long?>(null)
    val openAttendanceFileId: StateFlow<Long?> = _openAttendanceFileId
    fun openAttendanceFile(id: Long) { _openAttendanceFileId.value = id }
    fun closeAttendanceFile() { _openAttendanceFileId.value = null }

    fun saveAttendanceFile(file: AttendanceFileEntity) {
        val bound = if (file.id == 0L) file.copy(level = _currentLevel.value) else file
        viewModelScope.launch { repo.saveAttendanceFile(bound) }
    }

    fun deleteAttendanceFile(id: Long) {
        viewModelScope.launch {
            repo.deleteAttendanceFile(id)
            if (_openAttendanceFileId.value == id) _openAttendanceFileId.value = null
        }
    }

    fun saveDaily(day: DailyAttendanceEntity) {
        viewModelScope.launch { repo.saveDaily(day.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun deleteDaily(id: Long) {
        viewModelScope.launch { repo.deleteDaily(id) }
    }

    // -------- عارض PDF الداخلي --------

    private val _openPdfPath = MutableStateFlow<String?>(null)
    val openPdfPath: StateFlow<String?> = _openPdfPath

    fun openPdf(path: String) { _openPdfPath.value = path }
    fun closePdf() { _openPdfPath.value = null }

    val pdfAnnotations: StateFlow<List<PdfAnnotationEntity>> = repo.pdfAnnotations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addPdfAnnotation(entity: PdfAnnotationEntity) {
        viewModelScope.launch { repo.addPdfAnnotation(entity) }
    }

    fun undoLastPdfAnnotation(filePath: String, page: Int) {
        viewModelScope.launch { repo.undoLastPdfAnnotation(filePath, page) }
    }

    fun clearPdfPage(filePath: String, page: Int) {
        viewModelScope.launch { repo.clearPdfPage(filePath, page) }
    }

    // -------- عارض CAD (DXF/DWG قياس) --------

    private val _openCadPath = MutableStateFlow<String?>(null)
    val openCadPath: StateFlow<String?> = _openCadPath

    fun openCad(path: String) { _openCadPath.value = path }
    fun closeCad() { _openCadPath.value = null }

    // ---------- Derived ----------

    fun attentionFor(level: String): List<AttentionItem> =
        AttentionDiff.attentionFor(schedule.value, logic, level)

    fun markFor(elementId: String): String? = names.value[elementId]

    fun elementForMark(mark: String): PlanElement? {
        val id = names.value.entries.firstOrNull { it.value.equals(mark, ignoreCase = true) }?.key
        return planData.elements.firstOrNull { it.id == id }
    }

    fun availableMarks(exceptElementId: String?): List<String> {
        val used = names.value.filterKeys { it != exceptElementId }.values.toSet()
        return repo.baseSchedule.allMarks.filter { it !in used }
    }
}
