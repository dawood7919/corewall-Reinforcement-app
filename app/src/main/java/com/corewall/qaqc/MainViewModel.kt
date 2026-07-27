package com.corewall.qaqc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
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

/**
 * العدسات (Lenses) — بدل الأدوات المنفصلة: نفس المسقط بيتعاد تلوينه
 * وتفاصيله حسب العدسة، من غير ما تفقد سياقك (دور/عنصر/زوم).
 */
enum class Lens(val label: String) {
    REINF("التسليح"),
    COUNT("العدّ"),
    DATA("الداتا")
}

/** أقسام التطبيق الرئيسية — بتتبدّل من القائمة الجانبية. */
enum class Section(val title: String) {
    COREWALL("Corewall"),
    MANPOWER("Manpower")
}

/** شاشات ملء-الشاشة إضافية من القائمة الجانبية (S13–S16). */
enum class AppScreen { NOTIFICATIONS, SETTINGS, SYNC, ABOUT }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val repo: AppRepository = (app as CoreWallApp).repository
    val files: FilesManager = (app as CoreWallApp).filesManager
    private val settingsStore: SettingsStore = (app as CoreWallApp).settingsStore

    val planData = repo.planData
    val logic = ScheduleLogic(repo.baseSchedule.levels)
    val levels: List<String> = repo.baseSchedule.levels

    /** ترتيب ثابت للعناصر (s1..s63) لوضع التسمية. */
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

    // تاريخ التبويبات — عشان زرار الرجوع في الموبايل يرجّع خطوة ورا مش يقفل التطبيق
    private val tabHistory = ArrayDeque<Int>()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack

    private val _currentLevel = MutableStateFlow("GROUND")
    val currentLevel: StateFlow<String> = _currentLevel

    private val _namingMode = MutableStateFlow(false)
    val namingMode: StateFlow<Boolean> = _namingMode

    private val _selectedElementId = MutableStateFlow<String?>(null)
    val selectedElementId: StateFlow<String?> = _selectedElementId

    /** الجدول الفعّال = المرجعي + تعديلات المستخدم المخزنة في Room. */
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

    // ---------- Actions ----------

    /** تبديل العدسة بيحافظ على السياق: نفس الدور ونفس العنصر المختار. */
    fun setLens(lens: Lens) {
        _lens.value = lens
        if (lens != Lens.REINF) _namingMode.value = false
    }

    /** من القائمة الجانبية: افتح المسقط (تبويب 1) بعدسة معيّنة. */
    fun goToLens(lens: Lens) {
        setSection(Section.COREWALL)
        setLens(lens)
        setTabIndex(1)
    }

    fun goToManpower() {
        setSection(Section.MANPOWER)
        setTabIndex(0)
    }

    /** من القائمة الجانبية: افتح تبويب معيّن في قسم Corewall (مثلاً الإعدادات). */
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

    /** رجوع خطوة في التبويبات — بيرجّع true لو فيه خطوة اترجعت. */
    fun popTab(): Boolean {
        val prev = tabHistory.removeLastOrNull() ?: return false
        _tabIndex.value = prev
        _canGoBack.value = tabHistory.isNotEmpty()
        return true
    }

    fun setLevel(level: String) {
        if (level in levels) _currentLevel.value = level
    }

    fun stepLevel(delta: Int) {
        val idx = levels.indexOf(_currentLevel.value)
        val next = (idx + delta).coerceIn(0, levels.size - 1)
        _currentLevel.value = levels[next]
    }

    fun setNamingMode(enabled: Boolean) { _namingMode.value = enabled }

    fun selectElement(id: String?) { _selectedElementId.value = id }

    /**
     * حفظ اسم العنصر. بيقفل الـSheet فوراً — من غير ما يفتح
     * عنصر جديد تلقائي (الانتقال للتالي بزرار منفصل).
     */
    fun saveName(elementId: String, mark: String) {
        viewModelScope.launch {
            repo.setName(elementId, mark)
            _selectedElementId.value = null
        }
    }

    /** فتح العنصر التالي غير المسمّى (زرار صريح، مش سلوك تلقائي). */
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

    /**
     * حفظ تعديل قيم صف — بيتخزن كفرق عن الجدول المرجعي:
     * القيم المطابقة للأصل مش بتتسجل، ولو مفيش فروق التعديل بيتشال.
     */
    fun saveRangeEdit(mark: String, rowIndex: Int, values: Map<String, String>, baseValues: Map<String, String>) {
        val patch = values.filter { (k, v) -> baseValues[k] != v }
        viewModelScope.launch { repo.saveRangeEdit(mark, rowIndex, patch) }
    }

    fun clearRangeEdit(mark: String, rowIndex: Int) {
        viewModelScope.launch { repo.clearRangeEdit(mark, rowIndex) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    // ---------- Corewall Counting ----------

    val barCounts: StateFlow<List<BarCountEntity>> = repo.barCounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** حفظ صفوف العدّ لعنصر في الدور الحالي — بيقفل الـSheet بعد الحفظ. */
    fun saveBarCounts(elementId: String, entries: List<BarCountEntity>) {
        viewModelScope.launch {
            repo.replaceBarCounts(elementId, _currentLevel.value, entries)
            _selectedElementId.value = null
        }
    }

    // ---------- أداة Data: بلان فيل + الملفات + المهام + عارض PDF ----------

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

    /** نسخ ملفات مختارة كمرفقات لعنصر في الدور الحالي. */
    fun addDataFiles(elementId: String, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val level = _currentLevel.value
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                files.importUris(uris, files.attachmentsDir(level, elementId))
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

    /** مهام الدور الشغّال بس — عزل كامل زي باقي كل حاجة. */
    val tasks: StateFlow<List<TaskEntity>> =
        combine(repo.tasks, _currentLevel) { all, level ->
            all.filter { it.level == level }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** المهمة الجديدة بتتربط تلقائياً بالدور الشغّال. */
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

    // -------- الملاحظات الغنية (صور + تنسيق) --------

    val notes: StateFlow<List<NoteEntity>> = repo.notes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** الملاحظة اللي بتتعدّل دلوقتي في المحرّر (null = المحرّر مقفول). */
    private val _editingNote = MutableStateFlow<NoteEntity?>(null)
    val editingNote: StateFlow<NoteEntity?> = _editingNote

    /** فتح محرّر ملاحظة: موجودة للتعديل، أو جديدة فاضية للعنصر في الدور الحالي. */
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

    /** حفظ صامت (Auto-save) من غير ما يقفل المحرّر. */
    fun autosaveNote(note: NoteEntity, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.saveNote(note.copy(updatedAt = System.currentTimeMillis()))
            onSaved(id)
        }
    }

    /** عارض الصور بملء الشاشة. */
    private val _viewingImage = MutableStateFlow<String?>(null)
    val viewingImage: StateFlow<String?> = _viewingImage
    fun openImage(path: String) { _viewingImage.value = path }
    fun closeImage() { _viewingImage.value = null }

    /** شاشة ملء-الشاشة الحالية (إشعارات/إعدادات/مزامنة/عن) — من القائمة الجانبية. */
    private val _appScreen = MutableStateFlow<AppScreen?>(null)
    val appScreen: StateFlow<AppScreen?> = _appScreen
    fun openAppScreen(screen: AppScreen) { _appScreen.value = screen }
    fun closeAppScreen() { _appScreen.value = null }

    /** عدد الإشعارات غير المقروءة (مشتق من منطق الفجوات/التغييرات — placeholder ثابت مؤقتاً). */
    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications
    fun setUnreadNotifications(n: Int) { _unreadNotifications.value = n }

    /** أسماء العناصر المتاحة للـmentions (@). */
    fun allMarks(): List<String> = repo.baseSchedule.allMarks

    // -------- Manpower (معزول لكل دور) --------

    /** ملفات الحضور في الدور الشغّال بس. */
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

    // ---------- Derived ----------

    fun attentionFor(level: String): List<AttentionItem> =
        AttentionDiff.attentionFor(schedule.value, logic, level)

    fun markFor(elementId: String): String? = names.value[elementId]

    fun elementForMark(mark: String): PlanElement? {
        val id = names.value.entries.firstOrNull { it.value.equals(mark, ignoreCase = true) }?.key
        return planData.elements.firstOrNull { it.id == id }
    }

    /** الأسماء المرجعية المتاحة (اللي لسه متسمّتش لعناصر تانية). */
    fun availableMarks(exceptElementId: String?): List<String> {
        val used = names.value.filterKeys { it != exceptElementId }.values.toSet()
        return repo.baseSchedule.allMarks.filter { it !in used }
    }
}
