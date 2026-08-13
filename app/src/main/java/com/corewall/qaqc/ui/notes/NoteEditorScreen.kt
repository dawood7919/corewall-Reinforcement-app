package com.corewall.qaqc.ui.notes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.notes.NotesBlock
import com.corewall.qaqc.notes.NotesDocument
import com.corewall.qaqc.notes.NotesDocumentCodec
import com.corewall.qaqc.notes.NotesStore
import com.corewall.qaqc.notes.TextBlockStyle
import com.corewall.qaqc.notes.TextSpan
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NoteEditorScreen(vm: MainViewModel, note: NoteEntity, onClose: () -> Unit) {
    val store = vm.notesStore
    val liveNote by rememberUpdatedState(note)
    val c = LocalCwColors.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var document by remember(note.id) { mutableStateOf(store.documentOf(note)) }
    var focusedBlock by remember { mutableStateOf<String?>(null) }
    var showFormat by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var drawingFile by remember(note.id) { mutableStateOf<File?>(null) }
    var audioFile by remember(note.id) { mutableStateOf<File?>(null) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    val undo = remember(note.id) { ArrayDeque<NotesDocument>() }
    val redo = remember(note.id) { ArrayDeque<NotesDocument>() }

    fun apply(next: NotesDocument) {
        if (next == document) return
        undo.addLast(document)
        if (undo.size > 80) undo.removeFirst()
        redo.clear()
        document = next
    }
    fun replace(block: NotesBlock) = apply(document.copy(blocks = document.blocks.map { if (it.id == block.id) block else it }))
    fun add(block: NotesBlock) = apply(document.copy(blocks = document.blocks + block))
    fun remove(id: String) = apply(document.copy(blocks = document.blocks.filterNot { it.id == id }))
    fun move(id: String, direction: Int) {
        val index = document.blocks.indexOfFirst { it.id == id }
        val target = (index + direction).coerceIn(0, document.blocks.lastIndex)
        if (index < 0 || index == target) return
        val rows = document.blocks.toMutableList()
        val item = rows.removeAt(index)
        rows.add(target, item)
        apply(document.copy(blocks = rows))
    }
    fun persist() = store.saveDocument(liveNote.copy(title = title), document)

    LaunchedEffect(note.id) {
        snapshotFlow { title to document }.debounce(650).collect { persist() }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        vm.files.importNoteImages(uris, note.level, note.elementId).forEach { add(NotesBlock.image(it.absolutePath)) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        pendingCamera?.let { if (captured && it.exists()) add(NotesBlock.image(it.absolutePath)) else it.delete() }
        pendingCamera = null
    }
    LaunchedEffect(note.id) {
        when (store.consumeCapture()) {
            NotesStore.CaptureAction.IMAGE -> gallery.launch(arrayOf("image/*"))
            NotesStore.CaptureAction.DRAWING -> drawingFile = vm.files.newNoteSketchFile(note.level, note.elementId)
            NotesStore.CaptureAction.AUDIO -> audioFile = vm.files.newNoteAudioFile(note.level, note.elementId)
            null -> Unit
        }
    }

    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = c.surface, shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { persist(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { undo.removeLastOrNull()?.let { redo.addLast(document); document = it } }, enabled = undo.isNotEmpty()) { Icon(Icons.Filled.Undo, "تراجع") }
                    IconButton(onClick = { redo.removeLastOrNull()?.let { undo.addLast(document); document = it } }, enabled = redo.isNotEmpty()) { Icon(Icons.Filled.Redo, "إعادة") }
                    IconButton(onClick = { showMore = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.lg, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                item("title") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("العنوان") },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        singleLine = true,
                        shape = Radius.shapeMd,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(document.blocks, key = { it.id }) { block ->
                    DocumentEditorBlock(
                        block = block,
                        focused = focusedBlock == block.id,
                        onFocus = { focusedBlock = block.id },
                        onChange = ::replace,
                        onToggle = { replace(block.copy(checked = !block.checked)) },
                        onDelete = { remove(block.id) },
                        onMoveUp = { move(block.id, -1) },
                        onMoveDown = { move(block.id, 1) },
                        onOpenImage = { vm.openImage(block.mediaPath) }
                    )
                }
                item("append") {
                    Surface(onClick = { add(NotesBlock.text()) }, color = c.surfaceAlt, shape = Radius.pill) {
                        Row(Modifier.padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, null, tint = c.accent); Spacer(Modifier.width(Space.xs)); Text("إضافة فقرة", color = c.accent, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            BottomAppBar(Modifier.imePadding(), containerColor = c.surface) {
                IconButton(onClick = { gallery.launch(arrayOf("image/*")) }) { Icon(Icons.Filled.Image, "إضافة صورة") }
                IconButton(onClick = {
                    val f = vm.files.newImageFile(note.level, note.elementId)
                    pendingCamera = f
                    camera.launch(vm.files.uriFor(f))
                }) { Icon(Icons.Filled.CameraAlt, "التقاط صورة") }
                IconButton(onClick = { drawingFile = vm.files.newNoteSketchFile(note.level, note.elementId) }) { Icon(Icons.Filled.Draw, "رسم") }
                IconButton(onClick = { audioFile = vm.files.newNoteAudioFile(note.level, note.elementId) }) { Icon(Icons.Filled.Mic, "تسجيل صوت") }
                IconButton(onClick = { add(NotesBlock.checklist()) }) { Icon(Icons.Filled.CheckBox, "إضافة قائمة") }
                IconButton(onClick = { showFormat = true }) { Icon(Icons.Filled.FormatBold, "تنسيق") }
                IconButton(onClick = { showMore = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
            }
        }
    }

    if (showFormat) {
        DocumentFormatSheet(
            block = document.blocks.firstOrNull { it.id == focusedBlock },
            onDismiss = { showFormat = false },
            onStyle = { style, spans ->
                document.blocks.firstOrNull { it.id == focusedBlock }?.let { replace(it.copy(style = style, spans = spans)) }
            }
        )
    }
    if (showMore) {
        NoteActionsSheet(
            note = liveNote.copy(title = title),
            vm = vm,
            onDismiss = { showMore = false },
            onLabels = { showMore = false; showLabels = true },
            onClose = onClose
        )
    }
    if (showLabels) {
        NoteLabelsSheetNew(note = liveNote, vm = vm, onDismiss = { showLabels = false })
    }
    drawingFile?.let { file ->
        NotesDrawingSheet(
            file = file,
            onDismiss = { file.delete(); drawingFile = null },
            onSaved = { saved -> add(NotesBlock.drawing(saved.absolutePath)); drawingFile = null }
        )
    }
    audioFile?.let { file ->
        NotesVoiceSheet(
            file = file,
            onDismiss = { file.delete(); audioFile = null },
            onSaved = { saved, duration -> add(NotesBlock.audio(saved.absolutePath, duration)); audioFile = null }
        )
    }
}

@Composable
private fun DocumentEditorBlock(
    block: NotesBlock,
    focused: Boolean,
    onFocus: () -> Unit,
    onChange: (NotesBlock) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenImage: () -> Unit
) {
    val c = LocalCwColors.current
    val textStyle = when (block.type) {
        NotesBlock.HEADING -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        NotesBlock.QUOTE -> MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
        else -> MaterialTheme.typography.bodyLarge
    }
    Surface(color = if (focused) c.surfaceAlt else Color.Transparent, shape = Radius.shapeMd, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (focused) Space.sm else 0.dp), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            when (block.type) {
                NotesBlock.IMAGE, NotesBlock.DRAWING -> DocumentImageBlock(block, onOpen = onOpenImage)
                NotesBlock.AUDIO -> DocumentAudioBlock(block)
                NotesBlock.DIVIDER -> DocumentDivider()
                NotesBlock.CHECKLIST -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = block.checked, onCheckedChange = { onToggle() })
                    OutlinedTextField(value = block.text, onValueChange = { onChange(block.copy(text = it)) }, placeholder = { Text("عنصر قائمة") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                else -> OutlinedTextField(
                    value = block.text,
                    onValueChange = { onChange(block.copy(text = it)) },
                    placeholder = { Text("ملاحظة") },
                    textStyle = textStyle,
                    shape = Radius.shapeMd,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() }
                )
            }
            if (focused) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onMoveUp) { Icon(Icons.Filled.KeyboardArrowUp, "نقل لأعلى", tint = c.textSecondary) }
                IconButton(onClick = onMoveDown) { Icon(Icons.Filled.KeyboardArrowDown, "نقل لأسفل", tint = c.textSecondary) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, "حذف الكتلة", tint = c.danger.fg) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentFormatSheet(block: NotesBlock?, onDismiss: () -> Unit, onStyle: (TextBlockStyle, List<TextSpan>) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text("تنسيق الفقرة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("يطبّق التنسيق على الكتلة النشطة ويحفظه ضمن الوثيقة.", style = MaterialTheme.typography.bodySmall, color = LocalCwColors.current.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Surface(onClick = { block?.let { onStyle(it.style.copy(headingLevel = 1), it.spans); onDismiss() } }, shape = Radius.pill, color = MaterialTheme.colorScheme.surfaceVariant) { Text("عنوان", Modifier.padding(Space.md)) }
                Surface(onClick = { block?.let { onStyle(it.style.copy(bullet = true), it.spans); onDismiss() } }, shape = Radius.pill, color = MaterialTheme.colorScheme.surfaceVariant) { Text("نقاط", Modifier.padding(Space.md)) }
                Surface(onClick = { block?.let { onStyle(it.style, listOf(TextSpan(0, it.text.length, bold = true))); onDismiss() } }, shape = Radius.pill, color = MaterialTheme.colorScheme.surfaceVariant) { Text("غامق", Modifier.padding(Space.md)) }
                Surface(onClick = { block?.let { onStyle(it.style, listOf(TextSpan(0, it.text.length, italic = true))); onDismiss() } }, shape = Radius.pill, color = MaterialTheme.colorScheme.surfaceVariant) { Text("مائل", Modifier.padding(Space.md)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteActionsSheet(note: NoteEntity, vm: MainViewModel, onDismiss: () -> Unit, onLabels: () -> Unit, onClose: () -> Unit) {
    val store = vm.notesStore
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("خيارات الملاحظة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            NoteAction("${if (note.pinned) "إلغاء" else "تثبيت"} التثبيت", Icons.Filled.Add) { store.togglePin(note); onDismiss() }
            NoteAction("إضافة وسوم", Icons.Filled.Add) { onLabels() }
            NoteAction("أرشفة", Icons.Filled.Archive) { store.setArchived(note, true); onDismiss(); onClose() }
            NoteAction("حذف", Icons.Filled.DeleteOutline, danger = true) { store.trash(note); onDismiss(); onClose() }
        }
    }
}

@Composable
private fun NoteAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (danger) LocalCwColors.current.danger.fg else LocalCwColors.current.accent)
            Spacer(Modifier.width(Space.md)); Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteLabelsSheetNew(note: NoteEntity, vm: MainViewModel, onDismiss: () -> Unit) {
    val labels by vm.notesStore.noteLabels.collectAsStateWithLifecycle()
    val attached by vm.notesStore.labelsByNote.collectAsStateWithLifecycle()
    val selected = attached[note.id].orEmpty().map { it.id }.toSet()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text("الوسوم", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            labels.forEach { label ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = label.id in selected, onCheckedChange = { vm.notesStore.setLabel(note.id, label.id, it) })
                    Text(label.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
