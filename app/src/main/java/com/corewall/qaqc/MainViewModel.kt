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

enum class AppScreen { NOTIFICATIONS, SETTINGS, SYNC, ABOUT, FLOOR_NOTES, SITE_PHOTOS }

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

    fun addSitePhoto(filePath: String, comment: String) {
        viewModelScope.launch {
            repo.saveSitePhoto(
                SitePhotoEntity(
                    level = _currentLevel.value,
                    filePath = filePath,
                    comment = comment.trim(),
                    timestamp = System.currentTimeMillis()
                )
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
