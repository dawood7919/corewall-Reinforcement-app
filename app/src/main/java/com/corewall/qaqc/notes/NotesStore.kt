package com.corewall.qaqc.notes

import android.content.Context
import com.corewall.qaqc.data.AppRepository
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.data.db.NoteLabelEntity
import com.corewall.qaqc.data.db.NoteLabelLinkEntity
import com.corewall.qaqc.domain.NotesLayout
import com.corewall.qaqc.domain.NotesLogic
import com.corewall.qaqc.domain.NotesView
import com.corewall.qaqc.notify.NoteReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * حالة نظام الملاحظات وأفعاله.
 *
 * كان كل ده جوّه `MainViewModel` وسط عشرين مسؤولية تانية. الفصل هنا مش
 * عشان الملف يصغر — هو عشان تلات حاجات ملموسة:
 *
 * 1. **مابيتبنيش أصلاً لحد ما الملاحظات تتفتح.** الـ`MainViewModel` بيمسكه
 *    `by lazy`، فمستخدم فتح التطبيق وفضل على المسقط مش دافع تمن أي حاجة
 *    هنا: لا استعلامات التصنيفات ولا خرايطها ولا الفلترة.
 * 2. **نطاق واحد واضح.** كل الأفعال بتشتغل على [scope] اللي جاي من
 *    الـViewModel، فبتموت معاه — مفيش كوروتين شارد.
 * 3. **حدود صريحة.** الحاجة الوحيدة اللي بتعدّي الحدّ هي [onMutated]،
 *    وهي موجودة لسبب واحد: الملاحظة المفتوحة في المحرّر لازم تتزامن مع
 *    أي تعديل من ورقة الخيارات، وإلا الحفظ التلقائي بيرجّع القيمة القديمة
 *    فوق الجديدة.
 *
 * [notes] بتيجي من برّه لأن `MainViewModel` محتاجها لحاجات تانية (سياق
 * المساعد الذكي، ملخّص الدور) — فبناء ثاني ليها هنا كان هيبقى اشتراك
 * زيادة على نفس الجدول.
 */
class NotesStore(
    private val repo: AppRepository,
    private val scope: CoroutineScope,
    private val appContext: Context,
    val notes: StateFlow<List<NoteEntity>>,
    private val currentLevel: StateFlow<String>,
    /** بيتنده بعد أي تعديل — عشان محرّر الملاحظة يفضل شايف آخر نسخة. */
    private val onMutated: (NoteEntity) -> Unit
) {

    val noteLabels: StateFlow<List<NoteLabelEntity>> =
        repo.noteLabels.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val noteLabelLinks: StateFlow<List<NoteLabelLinkEntity>> =
        repo.noteLabelLinks.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * تصنيفات كل ملاحظة، مبنية مرة واحدة.
     *
     * لو الشاشة حسبتها لكل كارت، عرض ٥٠٠ ملاحظة معناه ٥٠٠ بحث في جدول
     * الروابط في كل إطار. الخريطة بتتبني مرة مع كل تغيير وبتتقري بمفتاح.
     */
    val labelsByNote: StateFlow<Map<Long, List<NoteLabelEntity>>> =
        combine(noteLabels, noteLabelLinks) { labels, links ->
            val byId = labels.associateBy { it.id }
            links.groupBy { it.noteId }
                .mapValues { (_, rows) -> rows.mapNotNull { byId[it.labelId] } }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _view = MutableStateFlow(NotesView.ACTIVE)
    val view: StateFlow<NotesView> = _view

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _labelFilter = MutableStateFlow<Long?>(null)
    val labelFilter: StateFlow<Long?> = _labelFilter

    private val _layout = MutableStateFlow(NotesLayout.GRID)
    val layout: StateFlow<NotesLayout> = _layout

    /**
     * الملاحظات المعروضة — الفلترة والبحث والترتيب في الخلفية.
     *
     * ده اللي بيخلّي البحث يفضل سلس على ألف ملاحظة: كل ضغطة زرار بتعيد
     * الفلترة على `Dispatchers.Default` والشاشة بتستلم النتيجة جاهزة.
     */
    val visible: StateFlow<List<NoteEntity>> =
        combine(notes, _view, _labelFilter, _query, labelsByNote) {
            all, view, label, query, labels ->
            NotesLogic.visible(all, view, label, query) { id -> labels[id].orEmpty() }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** عدّادات الأقسام — بتظهر جنب أسماءها في القايمة. */
    val counts: StateFlow<Map<NotesView, Int>> = notes
        .map { all ->
            mapOf(
                NotesView.ACTIVE to all.count { it.isActive },
                NotesView.ARCHIVE to all.count { it.archived && !it.isTrashed },
                NotesView.TRASH to all.count { it.isTrashed }
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun setView(view: NotesView) {
        _view.value = view
        _labelFilter.value = null
    }

    fun setQuery(q: String) { _query.value = q }
    fun setLabelFilter(id: Long?) { _labelFilter.value = id }
    fun toggleLayout() {
        _layout.value = if (_layout.value == NotesLayout.GRID) NotesLayout.LIST else NotesLayout.GRID
    }

    // ── أفعال على ملاحظة

    /**
     * تعديل ملاحظة واحدة.
     *
     * كله بيعدّي من هنا عشان تلاتة: `updatedAt` بيتحدّث دايماً (الترتيب
     * بيعتمد عليه)، والمنبّه بيتزامن، والمحرّر المفتوح بياخد آخر نسخة.
     */
    private fun mutate(note: NoteEntity, transform: (NoteEntity) -> NoteEntity) {
        val updated = transform(note).copy(updatedAt = System.currentTimeMillis())
        onMutated(updated)
        scope.launch {
            repo.saveNote(updated)
            // `schedule` نفسه بيلغي لو الملاحظة مابقتش تستاهل تذكير
            // (مؤرشفة، أو في المهملات، أو ميعادها عدّى).
            NoteReminders.schedule(appContext, updated)
        }
    }

    fun togglePin(note: NoteEntity) = mutate(note) { it.copy(pinned = !it.pinned) }

    // الأرشفة بتفكّ التثبيت: ملاحظة مثبّتة ومؤرشفة حالة متناقضة —
    // التثبيت معناه "خليها قدّامي" والأرشفة معناها "شيلها من قدّامي".
    fun setArchived(note: NoteEntity, archived: Boolean) =
        mutate(note) { it.copy(archived = archived, pinned = false) }

    /** الحذف بينقل للمهملات. المسح النهائي فعل منفصل ومقصود. */
    fun trash(note: NoteEntity) = mutate(note) {
        it.copy(deletedAt = System.currentTimeMillis(), pinned = false)
    }

    fun restore(note: NoteEntity) = mutate(note) { it.copy(deletedAt = null) }

    fun deleteForever(note: NoteEntity) {
        scope.launch {
            NoteReminders.cancel(appContext, note.id)
            repo.deleteNote(note)
        }
    }

    fun emptyTrash() {
        scope.launch {
            notes.value.filter { it.isTrashed }
                .forEach { NoteReminders.cancel(appContext, it.id) }
            repo.emptyNoteTrash()
        }
    }

    fun setColor(note: NoteEntity, argb: Long) = mutate(note) { it.copy(colorArgb = argb) }

    fun setReminder(note: NoteEntity, at: Long?) = mutate(note) { it.copy(reminderAt = at) }

    fun setKind(note: NoteEntity, kind: String) = mutate(note) {
        it.copy(
            kind = kind,
            body = when (kind) {
                NoteEntity.KIND_CHECKLIST -> NotesLogic.toChecklist(it.body)
                else -> NotesLogic.toPlainText(it.body)
            }
        )
    }

    fun setMeta(note: NoteEntity, noteType: String, priority: Int) =
        mutate(note) { it.copy(noteType = noteType, priority = priority) }

    // ── تصنيفات

    fun createLabel(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        scope.launch {
            repo.upsertNoteLabel(
                NoteLabelEntity(name = clean, createdAt = System.currentTimeMillis())
            )
        }
    }

    fun renameLabel(label: NoteLabelEntity, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        scope.launch { repo.upsertNoteLabel(label.copy(name = clean)) }
    }

    fun deleteLabel(label: NoteLabelEntity) {
        scope.launch {
            repo.deleteNoteLabel(label.id)
            if (_labelFilter.value == label.id) _labelFilter.value = null
        }
    }

    fun setLabel(noteId: Long, labelId: Long, attached: Boolean) {
        scope.launch { repo.setNoteLabel(noteId, labelId, attached) }
    }

    /** مسوّدة ملاحظة جديدة في الدور الشغّال — المحرّر بيفتح عليها. */
    fun draft(kind: String, elementId: String): NoteEntity {
        val now = System.currentTimeMillis()
        return NoteEntity(
            elementId = elementId,
            level = currentLevel.value,
            body = if (kind == NoteEntity.KIND_CHECKLIST) "- [ ] " else "",
            kind = kind,
            createdAt = now,
            updatedAt = now
        )
    }
}
