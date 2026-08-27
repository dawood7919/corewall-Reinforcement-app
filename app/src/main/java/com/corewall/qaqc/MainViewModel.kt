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
import com.corewall.qaqc.data.db.PdfBookmarkEntity
import com.corewall.qaqc.data.db.PdfMeasurementEntity
import com.corewall.qaqc.data.db.PdfScaleEntity
import com.corewall.qaqc.data.db.SitePhotoEntity
import com.corewall.qaqc.data.db.TaskEntity
import com.corewall.qaqc.creative.CreativeDocumentContent
import com.corewall.qaqc.creative.CreativePdfExporter
import com.corewall.qaqc.creative.CreativeTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.corewall.qaqc.data.model.PlanElement
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.domain.ActiveRangeResult
import com.corewall.qaqc.domain.AttentionDiff
import com.corewall.qaqc.domain.AttentionItem
import com.corewall.qaqc.data.FileLibrary
import com.corewall.qaqc.data.FileSearchHit
import com.corewall.qaqc.data.db.FileMetaEntity
import com.corewall.qaqc.domain.FloorSummary
import com.corewall.qaqc.domain.startOfDay
import com.corewall.qaqc.domain.startOfToday
import com.corewall.qaqc.domain.PourReadiness
import com.corewall.qaqc.domain.ScheduleLogic
import com.corewall.qaqc.notes.NotesStore
import com.corewall.qaqc.takeoff.TakeoffStore
import com.corewall.qaqc.notify.NoteReminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.corewall.qaqc.ui.nav.DataSection
import com.corewall.qaqc.ui.nav.Dest
import com.corewall.qaqc.ui.nav.ManpowerSection
import com.corewall.qaqc.ui.nav.NavState
import com.corewall.qaqc.ui.nav.Navigator

enum class Lens(val label: String) {
    REINF("التسليح"),
    COUNT("العدّ"),
    DATA("الداتا")
}


const val FLOOR_NOTE_ID = "__FLOOR__"

/**
 * مدخلات حساب جاهزية الصبّ.
 * `combine` بياخد 5 تدفّقات كحد أقصى في الـoverload المكتوب النوع،
 * فبنلمّهم في حاجة واحدة بدل ما نلجأ لنسخة الـArray غير الآمنة نوعياً.
 */
private data class PourReadinessInputs(
    val level: String,
    val inspections: Map<Pair<String, String>, String>,
    val names: Map<String, String>,
    val schedule: ScheduleData,
    val barCounts: List<BarCountEntity>
)

/**
 * ### سياسة الاشتراك في التدفّقات
 *
 * الـViewModel ده بيعرّف ٣١ `StateFlow`، وتسعتاشر منهم مبنيين فوق استعلام
 * `Flow` من Room. `stateIn(..., Eagerly)` بيفتح الاستعلام **لحظة إنشاء
 * الـViewModel** — يعني فتح التطبيق كان بيشغّل تسعتاشر استعلام `SELECT *`
 * وبيسيبهم مشتركين للأبد، حتى الشاشات اللي المستخدم مافتحهاش. وأسوأ من
 * كده: كل كتابة في أي جدول منهم بتعيد تشغيل الاستعلام وكل الحسابات اللي
 * فوقه، والنتيجة بتترمي لأن مفيش شاشة بتعرضها.
 *
 * فبقى فيه صنفين:
 *
 * • **مباشر (`= …stateIn`)** — التدفّقات اللي بيتقرا منها `‎.value` بشكل
 *   أمري من برّه أي اشتراك: `AgentHost` بيقرا `names` و`inspections`
 *   و`tasks` و`notes` وغيرها كـلقطة لحظية عشان يبني سياق المساعد الذكي.
 *   التدفّق ده لازم يكون مليان **قبل** أول قراية، فبيفضل مباشر.
 *
 * • **مؤجّل (`by lazy { …stateIn }`)** — التدفّقات اللي شاشة واحدة بتعرضها
 *   ومحدش بيقرا `‎.value` منها: علامات الـPDF والقياسات، مكتبة الملفات،
 *   تصنيفات الملاحظات، ملخّص الدور، مكتبة البرومبت. أول قراية بتفتح
 *   الاستعلام، وقبلها التطبيق مش دافع تمنه.
 *
 * القاعدة لما تضيف تدفّق جديد: **مؤجّل افتراضياً**. خلّيه مباشر بس لو
 * فيه كود بيقرا `‎.value` منه من غير ما يكون مشترك.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext: android.content.Context = app.applicationContext

    val repo: AppRepository = (app as CoreWallApp).repository
    val files: FilesManager = (app as CoreWallApp).filesManager
    private val agentExecutionStore = (app as CoreWallApp).agentExecutionStore
    private val creativeDocumentStore = (app as CoreWallApp).creativeDocumentStore
    private val settingsStore: SettingsStore = (app as CoreWallApp).settingsStore

    val planData = repo.planData
    val logic = ScheduleLogic(repo.baseSchedule.levels)
    val levels: List<String> = repo.baseSchedule.levels

    val orderedElements: List<PlanElement> = planData.elements.sortedBy {
        it.id.removePrefix("s").toIntOrNull() ?: Int.MAX_VALUE
    }

    val settings: StateFlow<AppSettings> = settingsStore.settings

    /**
     * الملّاح — المصدر الوحيد لسؤال "أنا فين". بديل الـ٦ آليات تنقّل القديمة.
     */
    // نسخة الحصر بتفتح على الحصر مباشرة — مفيش أدوار ولا فحص فيها أصلاً.
    val navigator = Navigator(
        if (com.corewall.qaqc.BuildConfig.TAKEOFF_ONLY) Dest.Takeoff else Dest.Today
    )
    val navState: StateFlow<NavState> = navigator.state

    private val _lens = MutableStateFlow(Lens.REINF)
    val lens: StateFlow<Lens> = _lens

    /** القسم المختار جوّه شاشة الداتا — تبويب داخلي مش تبويب تنقّل. */
    private val _dataSection = MutableStateFlow(DataSection.FILES)
    val dataSection: StateFlow<DataSection> = _dataSection
    fun setDataSection(s: DataSection) { _dataSection.value = s }

    /** القسم المختار جوّه شاشة العمالة. */
    private val _manpowerSection = MutableStateFlow(ManpowerSection.ATTENDANCE)
    val manpowerSection: StateFlow<ManpowerSection> = _manpowerSection
    fun setManpowerSection(s: ManpowerSection) { _manpowerSection.value = s }

    private val _currentLevel = MutableStateFlow(
        settingsStore.getLastLevel()?.takeIf { it in levels } ?: levels.firstOrNull() ?: "GROUND"
    )
    val currentLevel: StateFlow<String> = _currentLevel

    private val _namingMode = MutableStateFlow(false)
    val namingMode: StateFlow<Boolean> = _namingMode

    private val _selectedElementId = MutableStateFlow<String?>(null)
    val selectedElementId: StateFlow<String?> = _selectedElementId

    /**
     * جدول المكتب + تعديلات المستخدم + الأكواد اللي استوردها.
     *
     * `flowOn(Dispatchers.Default)` مش تزويق: `viewModelScope` شغّال على
     * `Dispatchers.Main.immediate`، يعني **كل** عملية `map`/`combine` هنا
     * كانت بتتنفّذ على خيط الواجهة. و`applyEdits` بيعيد بناء الجدول كله
     * (٣١ كود حيطة + ٤٩ كود كمرة بصفوفهم) في كل تعديل — يعني كل ضغطة حفظ
     * كانت بتوقّف الرسم لحد ما البناء يخلص.
     *
     * نفس المبدأ متطبّق على كل تدفّق مشتق تحت: **الحساب في الخلفية،
     * والنتيجة بس هي اللي بتوصل للواجهة**.
     */
    val schedule: StateFlow<ScheduleData> =
        combine(repo.rangeEdits, repo.importedMarks) { edits, imported ->
            repo.applyEdits(edits, imported)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, repo.baseSchedule)

    val names: StateFlow<Map<String, String>> = repo.names
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val inspections: StateFlow<Map<Pair<String, String>, String>> = repo.inspections
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val comments: StateFlow<List<CommentEntity>> = repo.comments
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val editedRowKeys: StateFlow<Set<Pair<String, Int>>> by lazy {
        repo.rangeEdits
            .map { edits -> edits.map { it.mark to it.rowIndex }.toSet() }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    }

    fun setLens(lens: Lens) {
        _lens.value = lens
        if (lens != Lens.REINF) _namingMode.value = false
    }

    // ───────────────────────────────────────────────────── التنقّل

    /** فتح وجهة. الشاشات بتنده دي بس — عمرها ما بتلمس المكدّس بنفسها. */
    fun go(dest: Dest) {
        if (dest is Dest.Root) selectTab(dest) else navigator.push(dest)
    }

    fun selectTab(root: Dest.Root) {
        navigator.selectTab(root)
        _selectedElementId.value = null
    }

    /** فتح المسقط على عدسة معيّنة. */
    fun goToLens(lens: Lens) {
        setLens(lens)
        selectTab(Dest.Plan)
    }

    /** فتح الداتا على قسم معيّن. */
    fun goToData(section: DataSection) {
        _dataSection.value = section
        selectTab(Dest.Data)
    }

    fun goToManpower(section: ManpowerSection = ManpowerSection.ATTENDANCE) {
        _manpowerSection.value = section
        navigator.push(Dest.Manpower)
    }

    /**
     * الرجوع — قاعدة واحدة بدل الـcascade القديم بـ٩ فروع.
     * بتنضّف بيانات الوجهة اللي اتقفلت عشان ما تفضلش معلّقة.
     */
    fun back(): Boolean = when (val r = navigator.pop()) {
        is Navigator.PopResult.Popped -> { onDestClosed(r.dest); true }
        is Navigator.PopResult.SwitchedTab -> true
        Navigator.PopResult.Exhausted -> false
    }

    /** قفل الوجهة الحالية (زرار الرجوع في الشريط العلوي). */
    fun closeCurrent() { back() }

    private fun onDestClosed(dest: Dest) {
        if (dest is Dest.NoteEditor) _editingNote.value = null
    }

    /** هل زرار الرجوع هيعمل حاجة جوّه التطبيق؟ */
    val canGoBack: StateFlow<Boolean> = navigator.canGoBack

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
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    // ══════════════════════════════════════════════ حصر الكميات

    /**
     * قسم الحصر — مستقل تماماً عن المشروع والأدوار.
     *
     * `by lazy` زي ماسك الملاحظات: اللي مش بيستخدم الحصر مش دافع تمن أي
     * استعلام.
     */
    val takeoff: TakeoffStore by lazy { TakeoffStore(appContext, viewModelScope) }

    fun openTakeoff() = navigator.push(Dest.Takeoff)
    fun openTakeoffProject(id: Long, name: String) =
        navigator.push(Dest.TakeoffProject(id, name))
    fun openTakeoffEditor(drawingId: Long, path: String, name: String) =
        navigator.push(Dest.TakeoffEditor(drawingId, path, name))
    fun openTakeoffData(drawingId: Long, name: String) =
        navigator.push(Dest.TakeoffData(drawingId, name))
    fun openTakeoffFormulas(drawingId: Long, name: String) =
        navigator.push(Dest.TakeoffFormulas(drawingId, name))

    // ══════════════════════════════════════════════ استوديو الإنشاء

    val creativeDocuments by lazy {
        currentLevel
            .flatMapLatest { creativeDocumentStore.documents(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    private val _editingCreativeDocument = MutableStateFlow<com.corewall.qaqc.data.db.CreativeDocumentEntity?>(null)
    val editingCreativeDocument: StateFlow<com.corewall.qaqc.data.db.CreativeDocumentEntity?> = _editingCreativeDocument
    private val _creativeExportState = MutableStateFlow<String?>(null)
    val creativeExportState: StateFlow<String?> = _creativeExportState

    fun openCreativeStudio() = navigator.push(Dest.CreativeStudio)

    fun createCreativeDocument(template: String, title: String = CreativeTemplate.label(template)) {
        viewModelScope.launch {
            val id = creativeDocumentStore.create(_currentLevel.value, template, title)
            openCreativeDocument(id)
        }
    }

    fun openCreativeDocument(id: Long) {
        viewModelScope.launch {
            _editingCreativeDocument.value = creativeDocumentStore.document(id)
            if (_editingCreativeDocument.value != null) navigator.push(Dest.CreativeEditor)
        }
    }

    fun closeCreativeDocument() {
        _editingCreativeDocument.value = null
        _creativeExportState.value = null
        navigator.dismiss(Dest.CreativeEditor)
    }

    fun saveCreativeDocument(content: CreativeDocumentContent) {
        val current = _editingCreativeDocument.value ?: return
        viewModelScope.launch {
            creativeDocumentStore.update(current, content)
            _editingCreativeDocument.value = creativeDocumentStore.document(current.id)
        }
    }

    fun creativeDocumentContent(entity: com.corewall.qaqc.data.db.CreativeDocumentEntity): CreativeDocumentContent =
        creativeDocumentStore.decode(entity)

    fun exportCreativeDocumentPdf() {
        val current = _editingCreativeDocument.value ?: return
        viewModelScope.launch {
            _creativeExportState.value = "جارٍ إنشاء PDF…"
            val content = creativeDocumentStore.decode(current)
            CreativePdfExporter.export(appContext, current.id, content)
                .onSuccess { file ->
                    creativeDocumentStore.recordExport(current.id, "PDF", file.absolutePath)
                    _creativeExportState.value = "تم حفظ PDF: ${file.name}"
                }
                .onFailure { error -> _creativeExportState.value = "تعذّر التصدير: ${error.message ?: "خطأ غير معروف"}" }
        }
    }

    fun exportCreativeDocumentImage() = exportCreative("IMAGE") { _, content ->
        com.corewall.qaqc.creative.CreativeSecondaryExporters.image(appContext, content).getOrThrow()
    }

    fun exportCreativeDocumentWord() = exportCreative("WORD") { _, content ->
        com.corewall.qaqc.creative.CreativeSecondaryExporters.wordCompatible(appContext, content).getOrThrow()
    }

    fun exportCreativeDocumentPackage() {
        val current = _editingCreativeDocument.value ?: return
        viewModelScope.launch {
            _creativeExportState.value = "جارٍ إنشاء الحزمة…"
            val exports = creativeDocumentStore.exports(current.id).map { java.io.File(it.path) }
            com.corewall.qaqc.creative.CreativeSecondaryExporters.packageFiles(appContext, current.title, exports)
                .onSuccess { file ->
                    creativeDocumentStore.recordExport(current.id, "ZIP", file.absolutePath)
                    _creativeExportState.value = "تم حفظ الحزمة: ${file.name}"
                }
                .onFailure { error -> _creativeExportState.value = "تعذّر إنشاء الحزمة: ${error.message ?: "خطأ غير معروف"}" }
        }
    }

    fun shareLatestCreativeExport() {
        val current = _editingCreativeDocument.value ?: return
        viewModelScope.launch {
            val latest = creativeDocumentStore.exports(current.id).firstOrNull()
            if (latest == null) {
                _creativeExportState.value = "صدّر ملفاً أولاً ثم شاركه"
                return@launch
            }
            val shared = files.share(java.io.File(latest.path))
            _creativeExportState.value = if (shared) "تم فتح شاشة المشاركة" else "تعذّر فتح شاشة المشاركة"
        }
    }

    private fun exportCreative(
        format: String,
        work: suspend (com.corewall.qaqc.data.db.CreativeDocumentEntity, CreativeDocumentContent) -> java.io.File
    ) {
        val current = _editingCreativeDocument.value ?: return
        viewModelScope.launch {
            _creativeExportState.value = "جارٍ إنشاء الملف…"
            runCatching { work(current, creativeDocumentStore.decode(current)) }
                .onSuccess { file ->
                    creativeDocumentStore.recordExport(current.id, format, file.absolutePath)
                    _creativeExportState.value = "تم حفظ ${if (format == "IMAGE") "الصورة" else "مستند Word"}: ${file.name}"
                }
                .onFailure { error -> _creativeExportState.value = "تعذّر التصدير: ${error.message ?: "خطأ غير معروف"}" }
        }
    }

    // ══════════════════════════════════════════════ نظام الملاحظات

    /**
     * حالة الملاحظات وأفعالها — في [NotesStore] مش هنا.
     *
     * `by lazy` مقصود: المستخدم اللي فتح التطبيق وفضل على المسقط مش دافع
     * تمن استعلامات التصنيفات ولا خرايطها ولا الفلترة. أول ما شاشة
     * الملاحظات تتفتح، الماسك بيتبني وبيبدأ يشتغل.
     */
    val notesStore: NotesStore by lazy {
        NotesStore(
            repo = repo,
            scope = viewModelScope,
            appContext = appContext,
            notes = notes,
            currentLevel = _currentLevel,
            // الملاحظة المفتوحة في المحرّر لازم تشوف أي تعديل جاي من ورقة
            // الخيارات، وإلا الحفظ التلقائي بيرجّع القيمة القديمة فوقها.
            onMutated = { updated ->
                if (updated.id != 0L && _editingNote.value?.id == updated.id) {
                    _editingNote.value = updated
                }
            }
        )
    }

    /**
     * إنشاء سريع.
     *
     * بيرجّع الملاحظة فوراً بمعرّف حقيقي عشان المحرّر يفتح عليها على طول
     * — من غير حوار ولا خطوة وسيطة. ده أهم فرق بين تدوين سريع وتدوين
     * بيتعب.
     */
    fun createNote(kind: String = NoteEntity.KIND_TEXT, elementId: String = FLOOR_NOTE_ID) {
        viewModelScope.launch {
            val draft = notesStore.draft(kind, elementId)
            val id = repo.saveNote(draft)
            _editingNote.value = draft.copy(id = id)
            navigator.push(Dest.NoteEditor)
        }
    }

    fun createNoteForCapture(action: NotesStore.CaptureAction, elementId: String = FLOOR_NOTE_ID) {
        notesStore.requestCapture(action)
        createNote(NoteEntity.KIND_TEXT, elementId)
    }

    private val _editingNote = MutableStateFlow<NoteEntity?>(null)
    val editingNote: StateFlow<NoteEntity?> = _editingNote

    /**
     * محرّر الملاحظة: البيانات هنا، والظهور من المكدّس. لازم الاتنين يتحرّكوا
     * مع بعض — لو اتحطّت البيانات من غير ما الوجهة تتفتح، المحرّر ما يبانش.
     */
    fun openNoteEditor(elementId: String, existing: NoteEntity? = null) {
        val now = System.currentTimeMillis()
        _editingNote.value = existing ?: NoteEntity(
            elementId = elementId,
            level = _currentLevel.value,
            createdAt = now,
            updatedAt = now
        )
        navigator.push(Dest.NoteEditor)
    }

    fun closeNoteEditor() { navigator.dismiss(Dest.NoteEditor); _editingNote.value = null }

    fun saveNote(note: NoteEntity) {
        viewModelScope.launch {
            val id = repo.saveNote(note.copy(updatedAt = System.currentTimeMillis()))
            _editingNote.value = if (note.id == 0L) note.copy(id = id) else note
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            NoteReminders.cancel(appContext, note.id)
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
    fun openImage(path: String) { navigator.push(Dest.ImageViewer(path)) }
    fun closeImage() { back() }

    // -------- Site Photos --------

    val sitePhotos: StateFlow<List<SitePhotoEntity>> =
        combine(repo.sitePhotos, _currentLevel) { all, level ->
            all.filter { it.level == level }.sortedByDescending { it.timestamp }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
                    keyWorked()
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

    // ------------------------------------------------- معرفة المشروع (مشتركة)

    private val _projectDocuments =
        MutableStateFlow<List<com.corewall.qaqc.data.db.DocumentEntity>>(emptyList())
    val projectDocuments: StateFlow<List<com.corewall.qaqc.data.db.DocumentEntity>> = _projectDocuments

    fun loadProjectKnowledge() {
        viewModelScope.launch {
            _projectDocuments.value = runCatching { aiEngine.projectDocuments() }.getOrDefault(emptyList())
        }
    }

    /**
     * بيستورد ملفات للمكتبة المشتركة.
     * بتتخزّن في مجلد منفصل وبتتسجّل بنطاق المشروع، فالمساعد بيشوفها
     * في كل الأدوار — وده الفرق الوحيد المسموح بيه عن عزل الأدوار.
     */
    fun importProjectKnowledge(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                files.importUris(uris, files.projectKnowledgeDir())
            }
            copied.forEach { f ->
                runCatching { aiEngine.register(f, com.corewall.qaqc.ai.KnowledgeScope.PROJECT) }
            }
            loadProjectKnowledge()
            autoAnalyze()
        }
    }

    /**
     * تحليل ملف بعينه من قائمة الملفات (زرار "تحليل" في قائمة الملف).
     *
     * [promptId] البرومبت اللي المستخدم اختاره من القايمة — null يعني
     * التحليل العام. الاسم بيتخزّن على المستند فـ"حلّل تاني" بيرجع بنفسه.
     */
    fun analyzeFile(file: java.io.File, promptId: Long? = null, onDone: (String) -> Unit = {}) {
        val cfg = settingsStore.aiConfig.value
        if (!cfg.isConfigured) { onDone("ضيف مفتاح API من إعدادات المساعد الذكي الأول."); return }
        viewModelScope.launch {
            _analyzing.value = _analyzing.value + 1
            // التحليل اليدوي قرار صريح: النتيجة تبقى في ذاكرة الدور المفتوح
            // حتى لو ملف PDF نفسه يذكر دوراً آخر في عنوانه أو محتواه.
            val id = runCatching { aiEngine.register(file, _currentLevel.value, forceLevel = true) }.getOrNull()
            if (id == null) { _analyzing.value = 0; onDone("تعذّر تسجيل الملف."); return@launch }
            // موجود قبل كده؟ رجّعه لقائمة الانتظار عشان يتحلّل من أول وجديد
            runCatching { aiEngine.reset(id) }
            val choice = resolvePrompt(promptId)
            val result = runCatching { aiEngine.analyze(cfg, id, levels, choice, preserveLevel = true) }.getOrNull()
            _analyzing.value = 0
            loadKnowledge()
            loadProjectKnowledge()
            refreshSuggestions()
            onDone(
                when (result?.status) {
                    "DONE" -> { keyWorked(); "اتحلّل: " + result.title.ifBlank { file.name } }
                    "UNSUPPORTED" -> result.error.ifBlank { "نوع الملف مش مدعوم" }
                    "PENDING" -> "مستني الشبكة — هيتحلّل لوحده"
                    null -> "تعذّر التحليل"
                    else -> result.error.ifBlank { "فشل التحليل" }
                }
            )
        }
    }

    /** بينسخ ملف دور للمكتبة المشتركة ويحلّله بنطاق المشروع. */
    fun addFileToProjectKnowledge(file: java.io.File, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = files.projectKnowledgeDir()
                    if (files.copyInto(file, dir)) java.io.File(dir, file.name) else null
                }.getOrNull()
            }
            if (copied == null || !copied.exists()) { onDone("تعذّر نسخ الملف للمكتبة."); return@launch }
            runCatching { aiEngine.register(copied, com.corewall.qaqc.ai.KnowledgeScope.PROJECT) }
            loadProjectKnowledge()
            autoAnalyze()
            onDone("اتضاف لمعرفة المشروع — هيبقى متاح في كل الأدوار")
        }
    }

    // ------------------------------------------------- مرفقات الشات

    private val _chatAttachments = MutableStateFlow<List<java.io.File>>(emptyList())
    val chatAttachments: StateFlow<List<java.io.File>> = _chatAttachments

    /**
     * بيستورد مرفقات للسؤال الجاي.
     * بتتسجّل في ذاكرة الدور وبتتحلّل، فالمساعد بيقدر يستخدم محتواها
     * في الإجابة بدل ما يبقى عارف اسمها بس.
     */
    fun attachToChat(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val level = _currentLevel.value
            val copied = withContext(Dispatchers.IO) {
                files.importUris(uris, files.levelDir(level))
            }
            if (copied.isEmpty()) return@launch
            _chatAttachments.value = _chatAttachments.value + copied
            copied.forEach { runCatching { aiEngine.register(it, level) } }
            loadKnowledge()
            autoAnalyze()
        }
    }

    fun removeChatAttachment(file: java.io.File) {
        _chatAttachments.value = _chatAttachments.value.filterNot { it.absolutePath == file.absolutePath }
    }

    fun clearChatAttachments() { _chatAttachments.value = emptyList() }

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

    /**
     * إعادة محاولة مستند فشل أو معلّق.
     * [promptId] null معناه "استخدم اللي اتحلّل بيه قبل كده" — مش "ارجع للعام".
     */
    fun reanalyzeDocument(docId: Long, promptId: Long? = null) {
        val cfg = settingsStore.aiConfig.value
        if (!cfg.isConfigured) return
        viewModelScope.launch {
            _analyzing.value = 1
            val remembered = _documents.value.firstOrNull { it.id == docId }?.promptName.orEmpty()
            val choice = if (promptId != null) resolvePrompt(promptId)
            else aiEngine.promptFor(remembered)
            runCatching { aiEngine.reset(docId) }
            runCatching { aiEngine.analyze(cfg, docId, levels, choice, preserveLevel = true) }
            _analyzing.value = 0
            loadKnowledge()
        }
    }

    // ------------------------------------------------- مكتبة البرومبت

    val prompts: StateFlow<List<com.corewall.qaqc.data.db.PromptEntity>> by lazy {
        repo.prompts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    private suspend fun resolvePrompt(id: Long?): com.corewall.qaqc.ai.PromptChoice {
        if (id == null) return com.corewall.qaqc.ai.PromptChoice.Default
        val p = repo.promptById(id) ?: return com.corewall.qaqc.ai.PromptChoice.Default
        repo.markPromptUsed(id)
        return com.corewall.qaqc.ai.PromptChoice(p.name, p.body)
    }

    /** بيحفظ برومبت جديد أو بيعدّل واحد موجود. بيرجّع رسالة للعرض. */
    fun savePrompt(id: Long, name: String, body: String, onDone: (String) -> Unit = {}) {
        val cleanName = name.trim()
        val cleanBody = body.trim()
        if (cleanName.isEmpty()) { onDone("لازم تدّي البرومبت اسم."); return }
        if (cleanBody.isEmpty()) { onDone("البرومبت فاضي."); return }
        viewModelScope.launch {
            // الاسم هو اللي بيتخزّن على المستند، فلازم يفضل مميّز
            val clash = repo.promptByName(cleanName)
            if (clash != null && clash.id != id) { onDone("فيه برومبت تاني بنفس الاسم."); return@launch }
            val now = System.currentTimeMillis()
            val existing = if (id != 0L) repo.promptById(id) else null
            repo.savePrompt(
                com.corewall.qaqc.data.db.PromptEntity(
                    id = id, name = cleanName, body = cleanBody,
                    usageCount = existing?.usageCount ?: 0,
                    lastUsedAt = existing?.lastUsedAt ?: 0L,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
            onDone(if (id == 0L) "اتحفظ \"$cleanName\"" else "اتعدّل \"$cleanName\"")
        }
    }

    fun deletePrompt(id: Long) {
        viewModelScope.launch { repo.deletePrompt(id) }
    }

    // ------------------------------------------------- استيراد أكواد الجدول

    val importedMarks: StateFlow<List<com.corewall.qaqc.data.db.ImportedMarkEntity>> =
        repo.importedMarks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** أكواد جدول المكتب — عشان الشاشة تعرف مين مستورد ومين أصلي. */
    fun officeMarks(): Set<String> = repo.baseSchedule.allMarks.toSet()

    fun importMarks(uri: Uri, onDone: (com.corewall.qaqc.data.ScheduleImport.Outcome) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<android.app.Application>()
            val outcome = runCatching {
                val name = files.displayNameOf(uri)
                val content = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("مقدرناش نقرا الملف")
                }
                repo.importMarks(content, name)
            }.getOrElse {
                com.corewall.qaqc.data.ScheduleImport.Outcome(fatal = it.message ?: "خطأ غير معروف")
            }
            onDone(outcome)
        }
    }

    fun deleteImportedMark(mark: String) {
        viewModelScope.launch { repo.deleteImportedMark(mark) }
    }

    fun deleteAllImportedMarks() {
        viewModelScope.launch { repo.deleteAllImportedMarks() }
    }

    fun importTemplate(): String = repo.importTemplate()

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
        if (!cfg.isConfigured) {
            _chatError.value =
                if (cfg.provider == com.corewall.qaqc.ai.AiProviderId.LOCAL)
                    "اختار ملف الموديل المحلي من إعدادات المساعد الذكي الأول."
                else "ضيف مفتاح API من إعدادات المساعد الذكي الأول."
            return
        }
        if (cfg.provider == com.corewall.qaqc.ai.AiProviderId.LOCAL) { askLocal(q, cfg); return }

        _chatBusy.value = true
        _chatError.value = null
        _agentStatus.value = "بيقرا حالة الدور…"
        // الطلب ممكن ياخد دقيقة، والمستخدم بيسيب التطبيق وهو مستني.
        // من غير التسجيل ده، النظام بيقتل العملية والرد بيضيع كأنه
        // معملش — وبتبدأ من الأول لما ترجع.
        com.corewall.qaqc.work.BackgroundWork.start(
            appContext, com.corewall.qaqc.work.BackgroundWork.JOB_AI, "المساعد بيشتغل…"
        )
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
            // المرفقات بتدخل السؤال كسياق صريح — الوكيل بيقدر يقراها بأداة
            val attached = _chatAttachments.value
            val question = if (attached.isEmpty()) q else buildString {
                appendLine(q)
                appendLine()
                appendLine("### ملفات مرفقة مع السؤال (اتسجّلت في ذاكرة الدور)")
                attached.forEach { appendLine("- ${it.name} — ${it.absolutePath}") }
            }
            _chatAttachments.value = emptyList()

            val knowledge = runCatching { aiEngine.knowledgeFor(level, q) }.getOrDefault("")
            val history = runCatching { aiEngine.historyDigest(level) }.getOrDefault("")
            val memory = runCatching { aiEngine.memoryDigest(level) }.getOrDefault("")

            runCatching {
                agentEngine.ask(cfg, question, appState, knowledge, history, memory) { thought ->
                    _agentStatus.value = thought
                    // نفس النص اللي في الشاشة بيروح للإشعار — فالمستخدم
                    // اللي في تطبيق تاني شايف إنه بيتقدّم، مش مستني بلا خبر.
                    com.corewall.qaqc.work.BackgroundWork.start(
                        appContext, com.corewall.qaqc.work.BackgroundWork.JOB_AI, thought
                    )
                }
            }.onSuccess { run ->
                keyWorked()
                aiEngine.saveTurn(level, q, run.answer)
                if (run.executed.isNotEmpty()) {
                    _actionLog.value = (run.executed.reversed() + _actionLog.value).take(60)
                    run.executed.forEach { action ->
                        agentExecutionStore.audit(
                            level = level,
                            tool = action.tool,
                            detail = action.detail,
                            result = if (action.ok) "تمت القراءة أو التنقل" else "تعذّر التنفيذ",
                            ok = action.ok,
                            auto = action.auto
                        )
                    }
                }
                val created = if (run.pending.isNotEmpty()) {
                    agentExecutionStore.createPlan(level, q, run.pending)
                } else null
                _pendingActions.value = created?.let { plan ->
                    run.pending.mapIndexed { index, pending ->
                        pending.copy(planId = plan.planId, stepId = plan.stepIds.getOrNull(index))
                    }
                } ?: emptyList()
                if (created != null) _agentStatus.value = "خطة تنفيذ جاهزة للمراجعة"
                loadAgentAudit()
            }.onFailure { e ->
                _chatError.value = e.aiMessage()
            }

            // الإنهاء في `finally`: أي استثناء بعد الحلقة كان هيسيب
            // الخدمة شغّالة وإشعار "المساعد بيشتغل…" معلّق للأبد.
            try {
                _chat.value = aiEngine.history(level)
                _agentStatus.value = null
                _chatBusy.value = false
                refreshSuggestions()
            } finally {
                com.corewall.qaqc.work.BackgroundWork.finish(
                    appContext, com.corewall.qaqc.work.BackgroundWork.JOB_AI
                )
            }
        }
    }

    /**
     * سؤال على الموديل المحلي.
     *
     * مسار منفصل عن الوكيل عن قصد، مش اختصار: حلقة الوكيل ببرومبتها
     * ٦٣٠٠ توكن وأربع جولات JSON مابتشتغلش على موديل بحجم اللي بيتحمّل
     * على تليفون. تشغيلها عليه كان هيدّي أدوات بتضيع بصمت وانتظار طويل
     * قبل أول حرف — يعني تجربة أسوأ من إن الأدوات تكون مقفولة بوضوح.
     *
     * السياق هنا سطور معدودة: الدور، وأعداد العناصر، والسؤال.
     */
    private fun askLocal(q: String, cfg: com.corewall.qaqc.ai.AiConfig) {
        _chatBusy.value = true
        _chatError.value = null
        _agentStatus.value = "الموديل المحلي بيتحمّل…"
        com.corewall.qaqc.work.BackgroundWork.start(
            appContext, com.corewall.qaqc.work.BackgroundWork.JOB_AI, "الموديل المحلي بيشتغل…"
        )
        viewModelScope.launch {
            val level = _currentLevel.value
            _chat.value = _chat.value + com.corewall.qaqc.data.db.ChatMessageEntity(
                level = level, role = "user", content = q, createdAt = System.currentTimeMillis()
            )
            try {
                val prompt = buildString {
                    appendLine("إنت مساعد مهندس تنفيذ في موقع بناء. جاوب بالعربي، مختصر وعملي.")
                    appendLine("لو مش متأكد من رقم، قول إنك مش متأكد — متخمّنش أرقام.")
                    appendLine()
                    appendLine("الدور الشغّال: $level")
                    appendLine("عدد عناصر المسقط: ${planData.elements.size}")
                    appendLine()
                    appendLine("السؤال: $q")
                }
                val answer = com.corewall.qaqc.ai.local.LocalLlm.generate(
                    cfg.localModelPath, prompt
                ) { partial -> _agentStatus.value = partial.takeLast(120) }

                aiEngine.saveTurn(
                    level, q,
                    com.corewall.qaqc.ai.model.ChatAnswer(
                        blocks = listOf(
                            com.corewall.qaqc.ai.model.AnswerBlock(
                                type = "TEXT", body = answer.trim()
                            )
                        )
                    )
                )
                _chat.value = aiEngine.history(level)
            } catch (e: Throwable) {
                _chatError.value = e.message ?: "الموديل المحلي فشل"
            } finally {
                _agentStatus.value = null
                _chatBusy.value = false
                com.corewall.qaqc.work.BackgroundWork.finish(
                    appContext, com.corewall.qaqc.work.BackgroundWork.JOB_AI
                )
            }
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

        override suspend fun completeTask(id: Long): Boolean = runCatching {
            val task = this@MainViewModel.tasks.value.firstOrNull { it.id == id } ?: return@runCatching false
            repo.upsertTask(task.copy(done = true, completedAt = System.currentTimeMillis()))
            true
        }.getOrDefault(false)

        override suspend fun addNote(title: String, body: String, level: String): Boolean = runCatching {
            val now = System.currentTimeMillis()
            repo.saveNote(
                NoteEntity(
                    elementId = FLOOR_NOTE_ID,
                    level = level,
                    title = title,
                    body = body,
                    createdAt = now,
                    updatedAt = now
                )
            )
            true
        }.getOrDefault(false)

        override suspend fun createCreativeDocument(title: String, template: String, level: String): Long? = runCatching {
            creativeDocumentStore.create(level, template, title)
        }.getOrNull()

        override suspend fun exportCreativeDocumentPdf(documentId: Long): String? = runCatching {
            val document = creativeDocumentStore.document(documentId) ?: return@runCatching null
            CreativePdfExporter.export(appContext, document.id, creativeDocumentStore.decode(document)).getOrThrow().also { file ->
                creativeDocumentStore.recordExport(document.id, "PDF", file.absolutePath)
            }.absolutePath
        }.getOrNull()

        override suspend fun addComment(elementId: String, text: String, level: String): Boolean = runCatching {
            repo.addComment(elementId, level, text)
            }.isSuccess

        override suspend fun setInspection(elementId: String, status: String, level: String): Boolean = runCatching {
            repo.setInspection(elementId, level, status)
        }.isSuccess

        override suspend fun deleteTask(id: Long): Boolean = runCatching { repo.deleteTask(id) }.isSuccess

        override fun elementIdForMark(mark: String): String? =
            names.entries.firstOrNull { it.value.equals(mark.trim(), ignoreCase = true) }?.key
    }

    private val agentEngine by lazy {
        com.corewall.qaqc.ai.agent.AgentEngine(
            com.corewall.qaqc.ai.agent.AgentExecutor(agentHost, files, aiEngine) { aiConfig.value }
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

    /** خطط قابلة للمراجعة تعيش في Room ولا تختفي عند إغلاق التطبيق. */
    val executionPlans by lazy {
        currentLevel
            .flatMapLatest { agentExecutionStore.plans(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    fun executionSteps(planId: Long) = agentExecutionStore.steps(planId)

    private val _agentAudit = MutableStateFlow<List<com.corewall.qaqc.data.db.AgentActionAuditEntity>>(emptyList())
    val agentAudit: StateFlow<List<com.corewall.qaqc.data.db.AgentActionAuditEntity>> = _agentAudit

    fun loadAgentAudit() {
        viewModelScope.launch {
            _agentAudit.value = withContext(Dispatchers.IO) {
                agentExecutionStore.latestAudit(_currentLevel.value)
            }
        }
    }

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
        if (p.planId != null && p.stepId != null) {
            executePlanStep(p.planId, p.stepId)
            return
        }
        viewModelScope.launch {
            val executor = com.corewall.qaqc.ai.agent.AgentExecutor(agentHost, files, aiEngine) { aiConfig.value }
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

    /** موافقة صريحة على خطوة واحدة؛ الحذف لا يدخل في تنفيذ جماعي. */
    fun executePlanStep(planId: Long, stepId: Long) {
        viewModelScope.launch {
            executePersistedStep(planId, stepId)
            loadKnowledge(); loadAgentAudit(); refreshSuggestions()
        }
    }

    /** اعتماد الخطة ينفذ الأوامر الكتابية فقط؛ الأوامر الحساسة والحذف تبقى منفصلة. */
    fun executePlan(planId: Long) {
        viewModelScope.launch {
            val steps = agentExecutionStore.stepsForPlan(planId)
            val eligible = steps.filter { it.status == "PENDING" && it.risk == com.corewall.qaqc.ai.agent.ToolRisk.WRITE.name }
            agentExecutionStore.markPlan(planId, "APPROVED")
            eligible.forEach { step -> executePersistedStep(planId, step.id) }
            if (eligible.isEmpty()) finishPlan(planId)
            loadKnowledge(); loadAgentAudit(); refreshSuggestions()
        }
    }

    fun dismissPlan(planId: Long) {
        viewModelScope.launch {
            agentExecutionStore.markPlan(planId, "DISMISSED")
            agentExecutionStore.stepsForPlan(planId).filter { it.status == "PENDING" }.forEach {
                agentExecutionStore.markStep(it.id, "DISMISSED", "ألغى المستخدم الخطة")
            }
            loadAgentAudit()
        }
    }

    private suspend fun finishPlan(planId: Long) {
        val steps = agentExecutionStore.stepsForPlan(planId)
        val status = when {
            steps.isEmpty() || steps.all { it.status == "DONE" || it.status == "DISMISSED" } -> "DONE"
            steps.any { it.status == "FAILED" } -> "PARTIAL"
            else -> "APPROVED"
        }
        agentExecutionStore.markPlan(planId, status)
    }

    private suspend fun executePersistedStep(planId: Long, stepId: Long) {
        val action = agentExecutionStore.actionForStep(stepId) ?: return
        val tool = com.corewall.qaqc.ai.agent.AgentTools.find(action.tool) ?: return
        agentExecutionStore.markPlan(planId, "RUNNING")
        agentExecutionStore.markStep(stepId, "RUNNING", "جاري التنفيذ")
        val outcome = com.corewall.qaqc.ai.agent.AgentExecutor(agentHost, files, aiEngine) { aiConfig.value }.run(action)
        agentExecutionStore.markStep(stepId, if (outcome.ok) "DONE" else "FAILED", outcome.userMessage.ifBlank { outcome.observation })
        agentExecutionStore.audit(
            level = _currentLevel.value,
            tool = tool.name,
            detail = action.describe(),
            result = outcome.userMessage.ifBlank { outcome.observation },
            ok = outcome.ok,
            auto = false,
            planId = planId,
            stepId = stepId
        )
        finishPlan(planId)
        _actionLog.value = listOf(
            com.corewall.qaqc.ai.agent.ActionLogEntry(
                at = System.currentTimeMillis(), tool = tool.name, detail = action.describe(), ok = outcome.ok, auto = false
            )
        ) + _actionLog.value
        _agentStatus.value = outcome.userMessage.ifBlank { null }
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
    private fun currentScreenName(): String {
        val st = navState.value
        val base = when (st.tab) {
            Dest.Plan -> "المسقط — عدسة ${_lens.value.label}"
            Dest.Data -> "الداتا — ${_dataSection.value.label}"
            else -> st.tab.title
        }
        val top = st.stack.lastOrNull() ?: return base
        val detail = if (top == Dest.Manpower) "العمالة — ${_manpowerSection.value.label}" else top.title
        return "$base › $detail"
    }

    /**
     * بيفتح وجهة بالاسم — دي الأسماء اللي الوكيل بيعرفها.
     *
     * قبل كده الوكيل كان يقدر يوصل ١٣ وجهة بس من أصل ٣٥، فنموذج التنقّل نفسه
     * كان هو السقف على قدرته. دلوقتي كل وجهة متاحة.
     */
    private fun navigateTo(screen: String): Boolean {
        val dest: Dest = when (screen.trim().uppercase()) {
            "HOME", "DASHBOARD", "TODAY" -> Dest.Today
            "PLAN", "SCHEDULE" -> { goToLens(Lens.REINF); return true }
            "COUNTING" -> { goToLens(Lens.COUNT); return true }
            "FILES" -> { goToData(DataSection.FILES); return true }
            "TASKS" -> { goToData(DataSection.TASKS); return true }
            "NOTES" -> { goToData(DataSection.NOTES); return true }
            "PHOTOS" -> { goToData(DataSection.PHOTOS); return true }
            "MANPOWER" -> { goToManpower(); return true }
            "CHECKS", "ANALYSIS" -> Dest.Checks
            "ATTENTION", "GAPS" -> Dest.Gaps
            "COUNTING_REPORT" -> Dest.CountingReport
            "TOOLS" -> Dest.Tools
            "FLOOR_ANALYSIS", "AI_ANALYSIS" -> Dest.FloorAnalysis
            "POUR", "POUR_READINESS" -> Dest.PourReadiness
            "KNOWLEDGE" -> Dest.FloorKnowledge
            "PROJECT_KNOWLEDGE", "LIBRARY" -> Dest.ProjectKnowledge
            "REPORTS", "DOCUMENTS" -> Dest.DocumentGen
            "CHAT", "ASSISTANT" -> Dest.Assistant
            "AI_SETTINGS" -> Dest.AiSettings
            "NOTIFICATIONS" -> Dest.Notifications
            "SETTINGS" -> Dest.Settings
            "SYNC" -> Dest.Sync
            "ABOUT" -> Dest.About
            "FLOOR_NOTES" -> Dest.FloorNotes
            "SITE_PHOTOS" -> Dest.SitePhotos
            else -> return false
        }
        go(dest)
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

    // ------------------------------------------------- تحديث التطبيق

    private val _updateState =
        MutableStateFlow<com.corewall.qaqc.update.UpdateUi>(com.corewall.qaqc.update.UpdateUi.Idle)
    val updateState: StateFlow<com.corewall.qaqc.update.UpdateUi> = _updateState

    /**
     * فحص التحديث. بيتنده لما تتفتح شاشة "عن التطبيق".
     *
     * مابيعيدش الفحص لو فيه تحميل شغّال — الفحص وقتها مالوش لازمة
     * وبيصفّر شريط التقدّم قدام المستخدم.
     */
    fun checkForUpdate() {
        val current = _updateState.value
        if (current is com.corewall.qaqc.update.UpdateUi.Downloading ||
            current is com.corewall.qaqc.update.UpdateUi.Checking
        ) return
        viewModelScope.launch {
            _updateState.value = com.corewall.qaqc.update.UpdateUi.Checking
            val found = runCatching { com.corewall.qaqc.update.AppUpdater.check() }.getOrNull()
            _updateState.value = found?.let { com.corewall.qaqc.update.UpdateUi.Available(it) }
                ?: com.corewall.qaqc.update.UpdateUi.UpToDate
        }
    }

    /**
     * تحميل التحديث وفتح شاشة التثبيت.
     *
     * في `viewModelScope` مش في نطاق الشاشة: التحميل ٧٠ ميجا وبياخد
     * دقايق، والمستخدم طبيعي يسيب الشاشة أو التطبيق وهو شغّال. ومسجّل
     * كشغلانة خلفية عشان النظام مايقتلش العملية في نصّه.
     */
    fun downloadAndInstall(context: android.content.Context) {
        val available = _updateState.value as? com.corewall.qaqc.update.UpdateUi.Available ?: return
        val app = context.applicationContext
        viewModelScope.launch {
            _updateState.value = com.corewall.qaqc.update.UpdateUi.Downloading(0f)
            com.corewall.qaqc.work.BackgroundWork.start(
                app, com.corewall.qaqc.work.BackgroundWork.JOB_UPDATE, "بينزّل التحديث…"
            )
            try {
                val file = com.corewall.qaqc.update.AppUpdater.download(app, available.update) { p ->
                    _updateState.value = com.corewall.qaqc.update.UpdateUi.Downloading(p)
                }
                _updateState.value = when {
                    file == null -> com.corewall.qaqc.update.UpdateUi.Failed("التحميل ماكملش")
                    !com.corewall.qaqc.update.AppUpdater.canInstall(app) ->
                        com.corewall.qaqc.update.UpdateUi.NeedsPermission(file)
                    com.corewall.qaqc.update.AppUpdater.install(app, file) ->
                        com.corewall.qaqc.update.UpdateUi.Installing
                    else -> com.corewall.qaqc.update.UpdateUi.Failed("مقدرناش نفتح شاشة التثبيت")
                }
            } finally {
                com.corewall.qaqc.work.BackgroundWork.finish(
                    app, com.corewall.qaqc.work.BackgroundWork.JOB_UPDATE
                )
            }
        }
    }

    /** تثبيت ملف اتنزّل خلاص — بعد ما المستخدم يدّي الإذن. */
    fun installDownloaded(context: android.content.Context, file: java.io.File) {
        _updateState.value =
            if (com.corewall.qaqc.update.AppUpdater.install(context, file))
                com.corewall.qaqc.update.UpdateUi.Installing
            else com.corewall.qaqc.update.UpdateUi.Failed("مقدرناش نفتح شاشة التثبيت")
    }

    // ------------------------------------------------- الموديل المحلي

    /**
     * بينسخ ملف الموديل جوّه التطبيق ويسجّل مساره.
     *
     * النسخ مش تبذير: الإذن اللي منتقي الملفات بيدّيه مؤقت وبيروح مع
     * إعادة التشغيل، والمكتبة الأصلية محتاجة **مسار ملف حقيقي** مش
     * `content://`. القراءة من المكان الأصلي كانت هتشتغل مرة وتقع بعدها،
     * والمستخدم مش هيعرف ليه.
     *
     * التمن نسخة تانية من ملف حجمه جيجابايت. مقبول لأن البديل ميزة
     * بتقع من غير سبب واضح — والملف القديم بيتمسح قبل الجديد.
     */
    fun importLocalModel(uri: android.net.Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            com.corewall.qaqc.work.BackgroundWork.start(
                appContext, "local-model", "بينسخ ملف الموديل…"
            )
            val result = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = java.io.File(appContext.filesDir, "models").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val name = queryFileName(uri) ?: "model.litertlm"
                    val target = java.io.File(dir, name)
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("مقدرناش نفتح الملف")
                    target.absolutePath
                }
            }
            com.corewall.qaqc.work.BackgroundWork.finish(appContext, "local-model")
            result
                .onSuccess { path ->
                    com.corewall.qaqc.ai.local.LocalLlm.release()
                    settingsStore.updateAiConfig { it.copy(localModelPath = path) }
                }
                .onFailure { e ->
                    _chatError.value = "مقدرناش ننسخ ملف الموديل: ${e.message.orEmpty()}"
                }
            onDone()
        }
    }

    /** بيشيل الموديل المحلي وملفه. */
    fun clearLocalModel() {
        com.corewall.qaqc.ai.local.LocalLlm.release()
        runCatching { java.io.File(appContext.filesDir, "models").deleteRecursively() }
        settingsStore.updateAiConfig { it.copy(localModelPath = "") }
    }

    private fun queryFileName(uri: android.net.Uri): String? = runCatching {
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val i = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && cursor.moveToFirst()) cursor.getString(i) else null
        }
    }.getOrNull()

    fun switchAiProvider(provider: com.corewall.qaqc.ai.AiProviderId) =
        settingsStore.switchAiProvider(provider)

    // ------------------------------------------------- خزنة المفاتيح

    val savedKeys: StateFlow<List<com.corewall.qaqc.data.SavedKey>> = settingsStore.savedKeys

    /**
     * بيتنده بعد أي طلب AI نجح. المفتاح اللي اشتغل بيتحفظ لوحده، فالمستخدم
     * مش محتاج يفتكر يضغط "احفظ" ولا يكتبه تاني بعد ما يبدّل مزوّد.
     */
    private fun keyWorked() { settingsStore.rememberWorkingKey() }

    fun useSavedKey(id: String) = settingsStore.activateKey(id)
    fun renameSavedKey(id: String, label: String) = settingsStore.renameKey(id, label)
    fun deleteSavedKey(id: String) = settingsStore.deleteKey(id)

    /**
     * بيتأكد إن المفتاح شغّال فعلاً بأصغر طلب ممكن، وبيحفظه لو نجح.
     * الاختبار ده هو الفرق بين "كتبت مفتاح" و"عندي مفتاح شغّال" — من غيره
     * المستخدم بيكتشف إن المفتاح غلط بعد ما يستنى تحليل ملف كامل.
     */
    fun testAndSaveKey(onDone: (String) -> Unit) {
        val cfg = settingsStore.aiConfig.value
        if (!cfg.isConfigured) { onDone("اكتب المفتاح الأول."); return }
        viewModelScope.launch {
            _testingKey.value = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    com.corewall.qaqc.ai.remote.providerFor(cfg.provider).complete(
                        cfg,
                        "رُدّ بـJSON فقط.",
                        "رُدّ بالضبط بـ {\"ok\":true} من غير أي نص تاني.",
                        expectJson = true
                    )
                }
            }
            _testingKey.value = false
            onDone(
                result.fold(
                    onSuccess = {
                        val fresh = settingsStore.rememberWorkingKey()
                        if (fresh) "المفتاح شغّال ✓ واتحفظ في الخزنة"
                        else "المفتاح شغّال ✓ (محفوظ عندك من قبل)"
                    },
                    onFailure = { it.aiMessage() }
                )
            )
        }
    }

    private val _testingKey = MutableStateFlow(false)
    val testingKey: StateFlow<Boolean> = _testingKey

    /** شاشة ملء-الشاشة الحالية (إشعارات/إعدادات/مزامنة/عن) — من القائمة الجانبية. */


    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications
    fun setUnreadNotifications(n: Int) { _unreadNotifications.value = n }

    /**
     * كل الأكواد — **مصدر واحد مراقَب**.
     *
     * قبل كده كل شاشة كانت بتقرا `repo.baseSchedule.allMarks` بنفسها، وده
     * جدول المكتب اللي في الأصول. لما بقى فيه أكواد مستوردة، الشاشات دي
     * فضلت شايفة الجدول الأصلي بس — الكمرة تتستورد بنجاح وماتظهرش في
     * التسمية ولا البحث. الحل إن كل حاجة تقرا من هنا، والقايمة دي بتتحدّث
     * لوحدها مع أي استيراد أو حذف.
     */
    /**
     * حالة كل عنصر في المسقط بالنسبة للدور الشغّال.
     *
     * كان بيتحسب في `PlanScreen` جوّه `remember` — يعني `activeRange`
     * بتتنادى لكل عنصر من الـ٦٣ **على خيط الواجهة** وقت تركيب الشاشة
     * الرئيسية، ومع كل تغيير دور أو اسم أو جدول. ده كان بيتحوّل مباشرة
     * لتأخير ظاهر عند فتح الشاشة وعند تبديل الدور.
     *
     * دلوقتي بيتحسب في الخلفية، والشاشة بتقرا خريطة جاهزة.
     */
    val elementStates: StateFlow<Map<String, ActiveRangeResult?>> =
        combine(schedule, names, _currentLevel) { sched, nm, level ->
            planData.elements.associate { el ->
                el.id to nm[el.id]?.let { logic.activeRange(sched, it, level) }
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val marks: StateFlow<List<String>> = schedule
        .map { it.allMarks }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.baseSchedule.allMarks)

    fun allMarks(): List<String> = marks.value

    /** الصف الأصلي قبل التعديلات — شامل الأكواد المستوردة، مش المكتب بس. */
    fun originalSchedule(): ScheduleData = repo.originalSchedule(importedMarks.value)

    val attendanceFiles: StateFlow<List<AttendanceFileEntity>> =
        combine(repo.attendanceFiles, _currentLevel) { all, level ->
            all.filter { it.level == level }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dailyAttendance: StateFlow<List<DailyAttendanceEntity>> = repo.dailyAttendance
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun openAttendanceFile(id: Long) { navigator.push(Dest.AttendanceFile(id)) }
    fun closeAttendanceFile() { back() }

    fun saveAttendanceFile(file: AttendanceFileEntity) {
        val bound = if (file.id == 0L) file.copy(level = _currentLevel.value) else file
        viewModelScope.launch { repo.saveAttendanceFile(bound) }
    }

    fun deleteAttendanceFile(id: Long) {
        viewModelScope.launch {
            repo.deleteAttendanceFile(id)
            if ((navigator.current as? Dest.AttendanceFile)?.id == id) back()
        }
    }

    fun saveDaily(day: DailyAttendanceEntity) {
        viewModelScope.launch { repo.saveDaily(day.copy(updatedAt = System.currentTimeMillis())) }
    }

    fun deleteDaily(id: Long) {
        viewModelScope.launch { repo.deleteDaily(id) }
    }

    // -------- عارض PDF الداخلي --------


    fun openPdf(path: String) { navigator.push(Dest.PdfViewer(path)) }
    fun openPdfOrganizer(path: String) { navigator.push(Dest.PdfOrganizer(path)) }
    fun closePdf() { back() }

    /**
     * بيانات رسمة واحدة — تدفّق لكل ملف مش تدفّق للجدول كله.
     *
     * قبل كده كان فيه `StateFlow` واحد بكل تعليقات كل الملفات، والشاشة
     * بتفلتره في Kotlin. يعني فتح رسمة = قراية الجدول كله، وأي تعديل على
     * أي ملف تانٍ = إعادة بناء القايمة. دلوقتي الاستعلام بيفلتر في SQL
     * (وفيه فهرس على `filePath`)، والاشتراك بيعيش مع الشاشة بس.
     */
    fun pdfAnnotationsFor(filePath: String) = repo.pdfAnnotationsFor(filePath)

    fun addPdfAnnotation(entity: PdfAnnotationEntity) {
        viewModelScope.launch { repo.addPdfAnnotation(entity) }
    }

    fun undoLastPdfAnnotation(filePath: String, page: Int) {
        viewModelScope.launch { repo.undoLastPdfAnnotation(filePath, page) }
    }

    /** حذف علامة بعينها — التراجع بيحتاجه عشان يقدر يرجّعها بعدين. */
    fun deletePdfAnnotation(id: Long) {
        viewModelScope.launch { repo.deletePdfAnnotation(id) }
    }

    fun clearPdfPage(filePath: String, page: Int) {
        viewModelScope.launch { repo.clearPdfPage(filePath, page) }
    }

    /** علامات مرجعية على صفحات الـPDF — بيكتبها المستخدم بنفسه. */
    fun pdfBookmarksFor(filePath: String) = repo.pdfBookmarksFor(filePath)

    fun addPdfBookmark(filePath: String, page: Int, label: String) {
        val title = label.trim().ifBlank { "صفحة ${page + 1}" }
        viewModelScope.launch {
            repo.addPdfBookmark(
                PdfBookmarkEntity(
                    filePath = filePath,
                    page = page,
                    label = title,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deletePdfBookmark(id: Long) {
        viewModelScope.launch { repo.deletePdfBookmark(id) }
    }

    // -------- القياس على الرسمة --------

    fun pdfMeasurementsFor(filePath: String) = repo.pdfMeasurementsFor(filePath)

    fun pdfScalesFor(filePath: String) = repo.pdfScalesFor(filePath)

    fun addPdfMeasurement(entity: PdfMeasurementEntity) {
        viewModelScope.launch { repo.addPdfMeasurement(entity) }
    }

    fun deletePdfMeasurement(id: Long) {
        viewModelScope.launch { repo.deletePdfMeasurement(id) }
    }

    fun clearPdfMeasurements(filePath: String, page: Int) {
        viewModelScope.launch { repo.clearPdfMeasurements(filePath, page) }
    }

    /**
     * بيسجّل معايرة. [page] = −١ يعني للمستند كله.
     *
     * المعايرة بتتخزّن في القاعدة مش في تفضيلات العرض عن قصد: هي **بيانات
     * هندسية**. لو ضاعت، كل قياس متسجّل في الملف بيبقى رقم بلا معنى.
     */
    fun setPdfScale(filePath: String, page: Int, unitsPerPoint: Double, unit: String, note: String) {
        viewModelScope.launch {
            repo.setPdfScale(
                PdfScaleEntity(
                    filePath = filePath,
                    page = page,
                    unitsPerPoint = unitsPerPoint,
                    unit = unit,
                    note = note,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun clearPdfScale(filePath: String, page: Int) {
        viewModelScope.launch { repo.clearPdfScale(filePath, page) }
    }

    // -------- عارض CAD (DXF/DWG قياس) --------


    fun openCad(path: String) { navigator.push(Dest.CadViewer(path)) }
    fun closeCad() { back() }

    // ---------- Derived ----------

    // ------------------------------------------------------- مكتبة الملفات

    /** بيانات الملفات: وسوم، مفضّلة، نصّ مستخرج، روابط. */
    val fileLibrary: FileLibrary = (app as CoreWallApp).fileLibrary

    val fileMeta: StateFlow<Map<String, FileMetaEntity>> by lazy {
        fileLibrary.allMeta.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    }

    val fileFavourites: StateFlow<List<FileMetaEntity>> by lazy {
        fileLibrary.favourites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    val fileRecent: StateFlow<List<FileMetaEntity>> by lazy {
        fileLibrary.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    val fileTags: StateFlow<List<String>> by lazy {
        fileLibrary.allTags.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    private val _fileQuery = MutableStateFlow("")
    val fileQuery: StateFlow<String> = _fileQuery

    private val _fileResults = MutableStateFlow<List<FileSearchHit>>(emptyList())
    val fileResults: StateFlow<List<FileSearchHit>> = _fileResults

    private var searchJob: kotlinx.coroutines.Job? = null

    /** بحث مع تهدئة — الكتابة بتلغي البحث اللي قبله بدل ما تكدّس استعلامات. */
    fun setFileQuery(q: String) {
        _fileQuery.value = q
        searchJob?.cancel()
        if (q.trim().length < 2) {
            _fileResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(220)
            _fileResults.value = fileLibrary.search(q)
        }
    }

    fun toggleFileFavourite(path: String) =
        viewModelScope.launch { fileLibrary.toggleFavourite(path) }

    fun addFileTag(path: String, tag: String) =
        viewModelScope.launch { fileLibrary.addTag(path, tag) }

    fun removeFileTag(path: String, tag: String) =
        viewModelScope.launch { fileLibrary.removeTag(path, tag) }

    fun noteFileOpened(path: String) =
        viewModelScope.launch { fileLibrary.markOpened(path) }

    // ------------------------------------------------------- ملخّص الدور

    private data class FloorCore(
        val level: String,
        val names: Map<String, String>,
        val inspections: Map<Pair<String, String>, String>,
        val schedule: ScheduleData
    )

    private data class FloorWork(val openTasks: Int, val doneTasks: Int, val notes: Int, val photos: Int)

    private data class Crew(val workers: Int, val foremen: Int, val engineers: Int)

    /**
     * ملخّص الدور — محسوب هنا مرّة واحدة وكل الشاشات بتقراه.
     * القاعدة: احسب في الـViewModel، الشاشة تعرض بس.
     */
    val floorSummary: StateFlow<FloorSummary> by lazy {
        val core = combine(_currentLevel, names, inspections, schedule) { l, n, i, s ->
            FloorCore(l, n, i, s)
        }
        val work = combine(_currentLevel, tasks, notes, sitePhotos) { l, t, n, p ->
            FloorWork(
                openTasks = t.count { it.level == l && !it.done },
                doneTasks = t.count { it.level == l && it.done },
                notes = n.count { it.level == l },
                photos = p.count { it.level == l }
            )
        }
        val crew = combine(dailyAttendance, attendanceFiles) { daily, files ->
            val ids = files.map { it.id }.toSet()
            val today = startOfToday()
            val rows = daily.filter { it.fileId in ids && startOfDay(it.date) == today }
            Crew(
                workers = rows.sumOf { it.workers },
                foremen = rows.sumOf { it.foremen },
                engineers = rows.sumOf { it.engineers }
            )
        }
        combine(core, work, crew) { c, w, k ->
            // أغلى حساب في التطبيق: بيمرّ على كل عناصر المسقط ويقاطعها
            // بالجدول والفحوصات. كان بيتنفّذ على خيط الواجهة مع كل تغيير
            // في أي جدول من سبعة.
            FloorSummary.compute(
                level = c.level,
                elements = planData.elements,
                names = c.names,
                inspections = c.inspections,
                schedule = c.schedule,
                logic = logic,
                openTasks = w.openTasks,
                doneTasks = w.doneTasks,
                notes = w.notes,
                photos = w.photos,
                workers = k.workers,
                foremen = k.foremen,
                engineers = k.engineers
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, FloorSummary.EMPTY)
    }

    // ---------------------------------------------------------------- جاهزية الصبّ

    /**
     * جاهزية الدور للصبّ — محسوبة من كل مصادر البيانات مع بعض.
     * بتتحدّث لوحدها مع أي تغيير في الفحوصات أو العدّ أو الصور أو المهام،
     * فالحكم اللي على الشاشة عمره ما يكون قديم.
     */
    val pourReadiness: StateFlow<PourReadiness.Result> =
        combine(
            _currentLevel, inspections, names, schedule, barCounts
        ) { level, insp, nm, sched, counts ->
            PourReadinessInputs(level, insp, nm, sched, counts)
        }.combine(sitePhotos) { a, photos -> a to photos }
            .combine(tasks) { (a, photos), tsk ->
                PourReadiness.evaluate(
                    level = a.level,
                    elements = planData.elements,
                    names = a.names,
                    inspections = a.inspections,
                    schedule = a.schedule,
                    logic = logic,
                    barCounts = a.barCounts,
                    photoCount = photos.count { it.level == a.level },
                    openTasks = tsk.count { it.level == a.level && !it.done }
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope, SharingStarted.Eagerly,
                PourReadiness.Result(_currentLevel.value, 0, 0, 0, emptyList())
            )

    /** بيحفظ ملخّص الجاهزية كملف نصّي ويشاركه. */
    fun sharePourReadiness() {
        viewModelScope.launch {
            val r = pourReadiness.value
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = files.levelDir(r.level)
                    java.io.File(dir, "جاهزية-الصب-${r.level}-${System.currentTimeMillis()}.txt")
                        .apply { writeText(PourReadiness.summarize(r)) }
                }.getOrNull()
            }
            file?.let { files.share(it) }
        }
    }

    fun attentionFor(level: String): List<AttentionItem> =
        AttentionDiff.attentionFor(schedule.value, logic, level)

    fun markFor(elementId: String): String? = names.value[elementId]

    fun elementForMark(mark: String): PlanElement? {
        val id = names.value.entries.firstOrNull { it.value.equals(mark, ignoreCase = true) }?.key
        return planData.elements.firstOrNull { it.id == id }
    }

    fun availableMarks(exceptElementId: String?): List<String> {
        val used = names.value.filterKeys { it != exceptElementId }.values.toSet()
        return schedule.value.allMarks.filter { it !in used }
    }
}
