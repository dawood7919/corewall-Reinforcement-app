package com.corewall.qaqc.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.domain.NotesLayout
import com.corewall.qaqc.domain.NotesView
import com.corewall.qaqc.notes.NotesBlock
import com.corewall.qaqc.notes.NotesDocumentCodec
import com.corewall.qaqc.notes.NotesStore
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import java.io.File

/** واجهة ملاحظات بطاقية موحدة: بحث محلي، تحديد متعدد، وخلق سريع للمحتوى. */
@Composable
fun NotesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val store = vm.notesStore
    val notes by store.ordered.collectAsStateWithLifecycle()
    val selection by store.selection.collectAsStateWithLifecycle()
    val layout by store.layout.collectAsStateWithLifecycle()
    val query by store.query.collectAsStateWithLifecycle()
    val view by store.view.collectAsStateWithLifecycle()
    val c = LocalCwColors.current
    var searchOpen by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selection.isNotEmpty()) {
                SelectionBar(
                    count = selection.size,
                    onClose = store::clearSelection,
                    onSelectAll = store::selectAllVisible,
                    onArchive = store::archiveSelected,
                    onTrash = store::trashSelected
                )
            } else {
                Surface(color = c.surface, shadowElevation = 1.dp) {
                    Column(Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = c.accentContainer, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text("CW", style = MaterialTheme.typography.labelLarge, color = c.accent, fontWeight = FontWeight.Bold) }
                            }
                            Spacer(Modifier.width(Space.sm))
                            Text("ملاحظاتي", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { sortOpen = true }) { Icon(Icons.Filled.Sort, "ترتيب") }
                                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                    NotesStore.SortOrder.entries.forEach { order ->
                                        DropdownMenuItem(text = { Text(when (order) { NotesStore.SortOrder.UPDATED -> "آخر تعديل"; NotesStore.SortOrder.CREATED -> "تاريخ الإنشاء"; NotesStore.SortOrder.TITLE -> "العنوان" }) }, onClick = { store.setSortOrder(order); sortOpen = false })
                                    }
                                }
                            }
                            IconButton(onClick = store::toggleLayout) { Icon(if (layout == NotesLayout.GRID) Icons.Filled.List else Icons.Filled.GridView, "تبديل العرض") }
                            Box {
                                IconButton(onClick = { moreOpen = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                    DropdownMenuItem(text = { Text("الملاحظات") }, onClick = { store.setView(NotesView.ACTIVE); moreOpen = false })
                                    DropdownMenuItem(text = { Text("الأرشيف") }, onClick = { store.setView(NotesView.ARCHIVE); moreOpen = false })
                                    DropdownMenuItem(text = { Text("المهملات") }, onClick = { store.setView(NotesView.TRASH); moreOpen = false })
                                }
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = store::setQuery,
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            trailingIcon = if (query.isNotBlank()) ({ IconButton(onClick = { store.setQuery("") }) { Icon(Icons.Filled.Close, "مسح البحث") } }) else null,
                            placeholder = { Text(if (view == NotesView.ACTIVE) "ابحث في الملاحظات" else "ابحث في هذا القسم") },
                            shape = Radius.pill,
                            modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = spring())
                        )
                    }
                }
            }

            if (notes.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Icon(Icons.Filled.ChecklistRtl, null, tint = c.accent, modifier = Modifier.size(54.dp))
                        Text(if (query.isBlank()) "ابدأ أول ملاحظة ميدانية" else "لا توجد نتائج", style = MaterialTheme.typography.titleMedium)
                        Text("النصوص والصور والرسوم والصوت تُحفظ محلياً داخل المشروع.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            } else if (layout == NotesLayout.GRID) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.md, bottom = 118.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalItemSpacing = Space.sm,
                    modifier = Modifier.weight(1f)
                ) {
                    items(notes, key = { it.id }) { note ->
                        DocumentNoteCard(note, compact = true, selected = note.id in selection, onClick = {
                            if (selection.isNotEmpty()) store.toggleSelected(note.id) else vm.openNoteEditor(note.elementId, note)
                        }, onLongClick = { store.toggleSelected(note.id) })
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.md, bottom = 118.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    items(notes, key = { it.id }) { note ->
                        DocumentNoteCard(note, compact = false, selected = note.id in selection, onClick = {
                            if (selection.isNotEmpty()) store.toggleSelected(note.id) else vm.openNoteEditor(note.elementId, note)
                        }, onLongClick = { store.toggleSelected(note.id) })
                    }
                }
            }
        }

        if (view == NotesView.ACTIVE && selection.isEmpty()) {
            CreateMenu(
                expanded = createOpen,
                onToggle = { createOpen = !createOpen },
                onText = { createOpen = false; vm.createNote() },
                onChecklist = { createOpen = false; vm.createNote(NoteEntity.KIND_CHECKLIST) },
                onDrawing = { createOpen = false; vm.createNoteForCapture(NotesStore.CaptureAction.DRAWING) },
                onImage = { createOpen = false; vm.createNoteForCapture(NotesStore.CaptureAction.IMAGE) },
                onAudio = { createOpen = false; vm.createNoteForCapture(NotesStore.CaptureAction.AUDIO) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.lg)
            )
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onArchive: () -> Unit, onTrash: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "إلغاء التحديد") }
            Text("$count محددة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.Checklist, "تحديد الكل") }
            IconButton(onClick = onArchive) { Icon(Icons.Filled.Archive, "أرشفة") }
            IconButton(onClick = onTrash) { Icon(Icons.Filled.DeleteOutline, "حذف") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentNoteCard(note: NoteEntity, compact: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = LocalCwColors.current
    val document = remember(note.documentJson, note.body) { NotesDocumentCodec.decode(note) }
    val cover = remember(document) { document.blocks.firstOrNull { it.type == NotesBlock.IMAGE || it.type == NotesBlock.DRAWING } }
    val audio = remember(document) { document.blocks.any { it.type == NotesBlock.AUDIO } }
    val checklist = remember(document) { document.blocks.filter { it.type == NotesBlock.CHECKLIST } }
    val body = remember(document) { NotesDocumentCodec.summary(document).lineSequence().filter { it.isNotBlank() }.take(4).joinToString("\n") }
    val cardColor = if (note.colorArgb != 0L) Color(note.colorArgb) else c.surface
    Surface(
        color = cardColor,
        shape = Radius.shapeLg,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, c.accent) else null,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(Modifier.padding(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            cover?.let { DocumentImageBlock(it, compact = compact) }
            if (note.title.isNotBlank()) Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodySmall, maxLines = if (compact) 5 else 3, overflow = TextOverflow.Ellipsis)
            if (checklist.isNotEmpty()) {
                val complete = checklist.count { it.checked }
                Text("قائمة: $complete / ${checklist.size}", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            if (audio) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Mic, null, tint = c.accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(Space.xs)); Text("تسجيل صوتي", style = MaterialTheme.typography.labelSmall, color = c.textSecondary) }
            if (note.pinned) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Star, null, tint = c.accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(Space.xs)); Text("مثبّتة", style = MaterialTheme.typography.labelSmall, color = c.textSecondary) }
        }
    }
}

@Composable
private fun CreateMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onText: () -> Unit,
    onChecklist: () -> Unit,
    onDrawing: () -> Unit,
    onImage: () -> Unit,
    onAudio: () -> Unit,
    modifier: Modifier
) {
    val c = LocalCwColors.current
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        AnimatedVisibility(expanded) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                CreatePill(Icons.Filled.Mic, "تسجيل صوت", onAudio)
                CreatePill(Icons.Filled.Image, "صورة", onImage)
                CreatePill(Icons.Filled.Draw, "رسم", onDrawing)
                CreatePill(Icons.Filled.Checklist, "قائمة", onChecklist)
                CreatePill(Icons.Filled.Add, "نص", onText)
            }
        }
        FloatingActionButton(onClick = onToggle, containerColor = c.accent, contentColor = c.onAccent) { Icon(if (expanded) Icons.Filled.Close else Icons.Filled.Add, "إنشاء ملاحظة") }
    }
}

@Composable
private fun CreatePill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, shape = Radius.pill) {
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(Space.sm))
            Icon(icon, null, tint = LocalCwColors.current.accent, modifier = Modifier.size(18.dp))
        }
    }
}
