package com.corewall.qaqc.ui.notes

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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

/** مساحة عمل ملاحظات ميدانية: صفحات هادئة، خصائص واضحة، ووسائط مرئية. */
@Composable
fun NotesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val store = vm.notesStore
    val notes by store.ordered.collectAsStateWithLifecycle()
    val selection by store.selection.collectAsStateWithLifecycle()
    val layout by store.layout.collectAsStateWithLifecycle()
    val query by store.query.collectAsStateWithLifecycle()
    val view by store.view.collectAsStateWithLifecycle()
    val c = LocalCwColors.current
    var createOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selection.isNotEmpty()) {
                NotesSelectionBar(
                    count = selection.size,
                    onClose = store::clearSelection,
                    onSelectAll = store::selectAllVisible,
                    onArchive = store::archiveSelected,
                    onTrash = store::trashSelected
                )
            } else {
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = c.accentContainer, shape = Radius.shapeMd, modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("CW", style = MaterialTheme.typography.labelLarge, color = c.accent, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Spacer(Modifier.width(Space.sm))
                        Column(Modifier.weight(1f)) {
                            Text("ملاحظات الموقع", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("مساحة عمل المشروع", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        }
                        Box {
                            IconButton(onClick = { sortOpen = true }) { Icon(Icons.Filled.Sort, "ترتيب") }
                            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                                NotesStore.SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(when (order) { NotesStore.SortOrder.UPDATED -> "آخر تعديل"; NotesStore.SortOrder.CREATED -> "تاريخ الإنشاء"; NotesStore.SortOrder.TITLE -> "العنوان" }) },
                                        onClick = { store.setSortOrder(order); sortOpen = false }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = store::toggleLayout) { Icon(if (layout == NotesLayout.GRID) Icons.Filled.List else Icons.Filled.GridView, "تبديل العرض") }
                        Box {
                            IconButton(onClick = { moreOpen = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                DropdownMenuItem(text = { Text("الصفحات النشطة") }, onClick = { store.setView(NotesView.ACTIVE); moreOpen = false })
                                DropdownMenuItem(text = { Text("الأرشيف") }, onClick = { store.setView(NotesView.ARCHIVE); moreOpen = false })
                                DropdownMenuItem(text = { Text("المهملات") }, onClick = { store.setView(NotesView.TRASH); moreOpen = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    Surface(color = c.surfaceAlt, shape = Radius.shapeMd, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = store::setQuery,
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.textSecondary) },
                            trailingIcon = if (query.isNotBlank()) ({ IconButton(onClick = { store.setQuery("") }) { Icon(Icons.Filled.Close, "مسح البحث") } }) else null,
                            placeholder = { Text(if (view == NotesView.ACTIVE) "بحث في الصفحات والمحتوى" else "بحث في هذا القسم") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = c.textPrimary,
                                unfocusedTextColor = c.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (notes.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Surface(color = c.accentContainer, shape = CircleShape, modifier = Modifier.size(64.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.ChecklistRtl, null, tint = c.accent, modifier = Modifier.size(30.dp)) }
                        }
                        Text(if (query.isBlank()) "ابدأ صفحة ميدانية جديدة" else "لا توجد نتائج", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("أنشئ صفحة للنصوص والصور والرسوم والصوت في مكان واحد.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            } else if (layout == NotesLayout.GRID) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(170.dp),
                    contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.sm, bottom = 116.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalItemSpacing = Space.sm,
                    modifier = Modifier.weight(1f)
                ) {
                    item("caption") { NotesSectionCaption(notes.size, view) }
                    items(notes, key = { it.id }) { note ->
                        NotionNoteCard(note, compact = true, selected = note.id in selection, onClick = {
                            if (selection.isNotEmpty()) store.toggleSelected(note.id) else vm.openNoteEditor(note.elementId, note)
                        }, onLongClick = { store.toggleSelected(note.id) })
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.sm, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    modifier = Modifier.weight(1f)
                ) {
                    item("caption") { NotesSectionCaption(notes.size, view) }
                    items(notes, key = { it.id }) { note ->
                        NotionNoteCard(note, compact = false, selected = note.id in selection, onClick = {
                            if (selection.isNotEmpty()) store.toggleSelected(note.id) else vm.openNoteEditor(note.elementId, note)
                        }, onLongClick = { store.toggleSelected(note.id) })
                    }
                }
            }
        }

        if (view == NotesView.ACTIVE && selection.isEmpty()) {
            NotesCreateMenu(
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
private fun NotesSectionCaption(count: Int, view: NotesView) {
    val c = LocalCwColors.current
    val label = when (view) { NotesView.ACTIVE -> "الصفحات الأخيرة"; NotesView.ARCHIVE -> "الصفحات المؤرشفة"; NotesView.TRASH -> "المهملات" }
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
    }
}

@Composable
private fun NotesSelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onArchive: () -> Unit, onTrash: () -> Unit) {
    val c = LocalCwColors.current
    Surface(color = c.surface, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إلغاء التحديد") }
            Text("$count محددة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.Checklist, "تحديد الكل") }
            IconButton(onClick = onArchive) { Icon(Icons.Filled.Archive, "أرشفة") }
            IconButton(onClick = onTrash) { Icon(Icons.Filled.DeleteOutline, "حذف") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotionNoteCard(note: NoteEntity, compact: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = LocalCwColors.current
    val document = remember(note.documentJson, note.body) { NotesDocumentCodec.decode(note) }
    val cover = remember(document) { document.blocks.firstOrNull { it.type == NotesBlock.IMAGE || it.type == NotesBlock.DRAWING } }
    val checklist = remember(document) { document.blocks.filter { it.type == NotesBlock.CHECKLIST } }
    val audio = remember(document) { document.blocks.any { it.type == NotesBlock.AUDIO } }
    val preview = remember(document) { NotesDocumentCodec.summary(document).lineSequence().filter { it.isNotBlank() }.take(if (compact) 4 else 2).joinToString(" ") }
    val cardColor = if (note.colorArgb != 0L) Color(note.colorArgb) else c.surface
    Surface(
        color = cardColor,
        shape = Radius.shapeLg,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            cover?.let { block ->
                coil3.compose.AsyncImage(
                    model = File(block.mediaPath),
                    contentDescription = "غلاف الملاحظة",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(if (compact) 116.dp else 140.dp).clip(Radius.shapeMd)
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(note.title.ifBlank { "صفحة بلا عنوان" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (preview.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(preview, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = if (compact) 4 else 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (note.pinned) Icon(Icons.Filled.Star, "مثبتة", tint = c.accent, modifier = Modifier.size(17.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                NotionProperty("نص")
                if (checklist.isNotEmpty()) NotionProperty("${checklist.count { it.checked }}/${checklist.size}", Icons.Filled.Checklist)
                if (audio) NotionProperty("صوت", Icons.Filled.Mic)
            }
        }
    }
}

@Composable
private fun NotionProperty(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val c = LocalCwColors.current
    Surface(color = c.surfaceAlt, shape = Radius.pill) {
        Row(Modifier.padding(horizontal = Space.sm, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = c.textSecondary, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(3.dp)) }
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun NotesCreateMenu(
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
                NotesCreatePill(Icons.Filled.Mic, "تسجيل صوت", onAudio)
                NotesCreatePill(Icons.Filled.Image, "صورة", onImage)
                NotesCreatePill(Icons.Filled.Draw, "رسم", onDrawing)
                NotesCreatePill(Icons.Filled.Checklist, "قائمة تحقق", onChecklist)
                NotesCreatePill(Icons.Filled.Add, "صفحة جديدة", onText)
            }
        }
        FloatingActionButton(onClick = onToggle, containerColor = c.accent, contentColor = c.onAccent) {
            Icon(if (expanded) Icons.Filled.Close else Icons.Filled.Add, "إنشاء صفحة")
        }
    }
}

@Composable
private fun NotesCreatePill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(onClick = onClick, color = c.surface, shadowElevation = 3.dp, shape = Radius.pill) {
        Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}
