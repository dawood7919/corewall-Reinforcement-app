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
 * نقطة التحكم الوحيدة في نظام الملاحظات. تحفظ التنظيم (وسوم وتذكيرات
 * وأرشفة) وتتعامل مع محتوى الملاحظة بصفته وثيقة كتل منظمة.
 */
class NotesStore(
    private val repo: AppRepository,
    private val scope: CoroutineScope,
    private val appContext: Context,
    val notes: StateFlow<List<NoteEntity>>,
    private val currentLevel: StateFlow<String>,
    private val onMutated: (NoteEntity) -> Unit
) {
    enum class SortOrder { UPDATED, CREATED, TITLE }
    enum class CaptureAction { IMAGE, DRAWING, AUDIO }

    val noteLabels: StateFlow<List<NoteLabelEntity>> =
        repo.noteLabels.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val noteLabelLinks: StateFlow<List<NoteLabelLinkEntity>> =
        repo.noteLabelLinks.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val labelsByNote: StateFlow<Map<Long, List<NoteLabelEntity>>> =
        combine(noteLabels, noteLabelLinks) { labels, links ->
            val byId = labels.associateBy { it.id }
            links.groupBy { it.noteId }.mapValues { (_, rows) -> rows.mapNotNull { byId[it.labelId] } }
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _view = MutableStateFlow(NotesView.ACTIVE)
    val view: StateFlow<NotesView> = _view
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _labelFilter = MutableStateFlow<Long?>(null)
    val labelFilter: StateFlow<Long?> = _labelFilter
    private val _layout = MutableStateFlow(NotesLayout.GRID)
    val layout: StateFlow<NotesLayout> = _layout
    private val _sortOrder = MutableStateFlow(SortOrder.UPDATED)
    val sortOrder: StateFlow<SortOrder> = _sortOrder
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection
    private var pendingCapture: CaptureAction? = null

    val visible: StateFlow<List<NoteEntity>> =
        combine(notes, _view, _labelFilter, _query, labelsByNote) { all, view, label, query, labels ->
            NotesLogic.visible(all, view, label, query) { id -> labels[id].orEmpty() }
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    val ordered: StateFlow<List<NoteEntity>> = combine(visible, _sortOrder) { rows, sort ->
        when (sort) {
            SortOrder.UPDATED -> rows.sortedByDescending { it.updatedAt }
            SortOrder.CREATED -> rows.sortedByDescending { it.createdAt }
            SortOrder.TITLE -> rows.sortedBy { it.title.lowercase() }
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    val counts: StateFlow<Map<NotesView, Int>> = notes.map { all ->
        mapOf(
            NotesView.ACTIVE to all.count { it.isActive },
            NotesView.ARCHIVE to all.count { it.archived && !it.isTrashed },
            NotesView.TRASH to all.count { it.isTrashed }
        )
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun setView(view: NotesView) {
        _view.value = view
        _labelFilter.value = null
        clearSelection()
    }
    fun setQuery(query: String) { _query.value = query }
    fun setLabelFilter(id: Long?) { _labelFilter.value = id }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun toggleLayout() { _layout.value = if (_layout.value == NotesLayout.GRID) NotesLayout.LIST else NotesLayout.GRID }
    fun requestCapture(action: CaptureAction) { pendingCapture = action }
    fun consumeCapture(): CaptureAction? = pendingCapture.also { pendingCapture = null }

    private fun mutate(note: NoteEntity, transform: (NoteEntity) -> NoteEntity) {
        val updated = transform(note).copy(updatedAt = System.currentTimeMillis())
        onMutated(updated)
        scope.launch {
            repo.saveNote(updated)
            NoteReminders.schedule(appContext, updated)
        }
    }

    fun documentOf(note: NoteEntity): NotesDocument = NotesDocumentCodec.decode(note)

    /** حفظ الوثيقة هو المسار الوحيد للمحرر الجديد؛ يلخّصها أيضاً للبحث المحلي. */
    fun saveDocument(note: NoteEntity, document: NotesDocument) = mutate(note) {
        val encoded = NotesDocumentCodec.encode(document)
        val stable = NotesDocumentCodec.decode(it.copy(documentJson = encoded))
        it.copy(
            documentJson = NotesDocumentCodec.encode(stable),
            body = NotesDocumentCodec.summary(stable),
            imagePathsJson = NotesDocumentCodec.mediaJson(stable),
            kind = NoteEntity.KIND_TEXT
        )
    }

    fun updateTitle(note: NoteEntity, title: String) = mutate(note) { it.copy(title = title.trim()) }
    fun togglePin(note: NoteEntity) = mutate(note) { it.copy(pinned = !it.pinned) }
    fun setArchived(note: NoteEntity, archived: Boolean) = mutate(note) { it.copy(archived = archived, pinned = false) }
    fun trash(note: NoteEntity) = mutate(note) { it.copy(deletedAt = System.currentTimeMillis(), pinned = false) }
    fun restore(note: NoteEntity) = mutate(note) { it.copy(deletedAt = null) }
    fun setColor(note: NoteEntity, argb: Long) = mutate(note) { it.copy(colorArgb = argb) }
    fun setReminder(note: NoteEntity, at: Long?) = mutate(note) { it.copy(reminderAt = at) }
    fun setMeta(note: NoteEntity, noteType: String, priority: Int) = mutate(note) { it.copy(noteType = noteType, priority = priority) }

    fun deleteForever(note: NoteEntity) = scope.launch {
        NoteReminders.cancel(appContext, note.id)
        repo.deleteNote(note)
    }
    fun emptyTrash() = scope.launch {
        notes.value.filter { it.isTrashed }.forEach { NoteReminders.cancel(appContext, it.id) }
        repo.emptyNoteTrash()
    }

    fun createLabel(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) scope.launch { repo.upsertNoteLabel(NoteLabelEntity(name = clean, createdAt = System.currentTimeMillis())) }
    }
    fun renameLabel(label: NoteLabelEntity, name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) scope.launch { repo.upsertNoteLabel(label.copy(name = clean)) }
    }
    fun deleteLabel(label: NoteLabelEntity) = scope.launch {
        repo.deleteNoteLabel(label.id)
        if (_labelFilter.value == label.id) _labelFilter.value = null
    }
    fun setLabel(noteId: Long, labelId: Long, attached: Boolean) = scope.launch { repo.setNoteLabel(noteId, labelId, attached) }

    fun toggleSelected(id: Long) {
        _selection.value = _selection.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }
    fun clearSelection() { _selection.value = emptySet() }
    fun selectAllVisible() { _selection.value = ordered.value.map { it.id }.toSet() }
    fun archiveSelected() {
        val selected = _selection.value
        notes.value.filter { it.id in selected }.forEach { setArchived(it, true) }
        clearSelection()
    }
    fun trashSelected() {
        val selected = _selection.value
        notes.value.filter { it.id in selected }.forEach { trash(it) }
        clearSelection()
    }

    /** مسوّدة جديدة بكتلة نص فارغة صالحة للكتابة فوراً. */
    fun draft(kind: String, elementId: String): NoteEntity {
        val now = System.currentTimeMillis()
        val document = if (kind == NoteEntity.KIND_CHECKLIST) NotesDocument(blocks = listOf(NotesBlock.checklist())) else NotesDocument()
        return NoteEntity(
            elementId = elementId,
            level = currentLevel.value,
            documentJson = NotesDocumentCodec.encode(document),
            body = NotesDocumentCodec.summary(document),
            imagePathsJson = NotesDocumentCodec.mediaJson(document),
            kind = NoteEntity.KIND_TEXT,
            createdAt = now,
            updatedAt = now
        )
    }
}
