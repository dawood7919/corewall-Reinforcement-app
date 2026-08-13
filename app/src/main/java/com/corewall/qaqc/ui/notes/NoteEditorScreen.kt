package com.corewall.qaqc.ui.notes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.notes.NotesBlock
import com.corewall.qaqc.notes.NotesDocument
import com.corewall.qaqc.notes.NotesStore
import com.corewall.qaqc.notes.TextSpan
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** محرر صفحات ميدانية: يركز على الكتابة أولاً، ثم الخصائص والوسائط عند الحاجة. */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NoteEditorScreen(vm: MainViewModel, note: NoteEntity, onClose: () -> Unit) {
    val store = vm.notesStore
    val liveNote by rememberUpdatedState(note)
    val c = LocalCwColors.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var document by remember(note.id) { mutableStateOf(store.documentOf(note)) }
    var focusedBlock by remember(note.id) { mutableStateOf<String?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var showInsert by remember { mutableStateOf(false) }
    var drawingFile by remember(note.id) { mutableStateOf<File?>(null) }
    var audioFile by remember(note.id) { mutableStateOf<File?>(null) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
    fun appendAndFocus(block: NotesBlock) { add(block); focusedBlock = block.id }
    fun remove(id: String) = apply(document.copy(blocks = document.blocks.filterNot { it.id == id }))
    fun move(id: String, direction: Int) {
        val index = document.blocks.indexOfFirst { it.id == id }
        val target = (index + direction).coerceIn(0, document.blocks.lastIndex)
        if (index < 0 || index == target) return
        val blocks = document.blocks.toMutableList()
        blocks.add(target, blocks.removeAt(index))
        apply(document.copy(blocks = blocks))
    }
    fun persist() = store.saveDocument(liveNote.copy(title = title), document)
    fun focusedOrFirst() = document.blocks.firstOrNull { it.id == focusedBlock }
        ?: document.blocks.firstOrNull { it.type in setOf(NotesBlock.TEXT, NotesBlock.HEADING, NotesBlock.QUOTE, NotesBlock.CHECKLIST) }
    fun styleFocused(transform: (NotesBlock) -> NotesBlock) = focusedOrFirst()?.let { block -> replace(transform(block)); focusedBlock = block.id }
    fun mutateSpan(transform: (TextSpan) -> TextSpan) = styleFocused { block ->
        val span = block.spans.firstOrNull { it.start == 0 && it.end == block.text.length } ?: TextSpan(0, block.text.length)
        block.copy(spans = listOf(transform(span)))
    }

    LaunchedEffect(note.id) { snapshotFlow { title to document }.debounce(650).collect { persist() } }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        vm.files.importNoteImages(uris, note.level, note.elementId).forEach { appendAndFocus(NotesBlock.image(it.absolutePath)) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        pendingCamera?.let { file -> if (captured && file.exists()) appendAndFocus(NotesBlock.image(file.absolutePath)) else file.delete() }
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

    val attachmentCount = remember(document) { document.blocks.count { it.type in setOf(NotesBlock.IMAGE, NotesBlock.DRAWING, NotesBlock.AUDIO) } }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        cursorColor = c.accent
    )

    Surface(Modifier.fillMaxSize(), color = c.background) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = c.background) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { persist(); onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                    Column(Modifier.weight(1f)) {
                        Text("صفحة ميدانية", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (exporting) "جارٍ إنشاء ملف PDF…" else "يُحفظ محلياً", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    }
                    IconButton(onClick = { undo.removeLastOrNull()?.let { redo.addLast(document); document = it } }, enabled = undo.isNotEmpty()) { Icon(Icons.Filled.Undo, "تراجع") }
                    IconButton(onClick = { redo.removeLastOrNull()?.let { undo.addLast(document); document = it } }, enabled = redo.isNotEmpty()) { Icon(Icons.Filled.Redo, "إعادة") }
                    IconButton(onClick = {
                        if (exporting) return@IconButton
                        exporting = true
                        val frozen = document
                        val name = title
                        scope.launch(Dispatchers.IO) {
                            val pdf = vm.files.newNotePdfFile(note.level, note.elementId)
                            val ok = NotesPdfExporter.export(pdf, name, frozen)
                            withContext(Dispatchers.Main) { exporting = false; if (ok) vm.files.share(pdf) }
                        }
                    }) { Icon(Icons.Filled.Share, "مشاركة PDF") }
                    IconButton(onClick = { showMore = true }) { Icon(Icons.Filled.MoreVert, "خيارات الصفحة") }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = Space.xl, end = Space.xl, top = Space.md, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                item("page-title") {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("بدون عنوان") },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        colors = fieldColors,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item("page-properties") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        PageProperty("الموقع", note.level.ifBlank { "المشروع الحالي" })
                        PageProperty("المحتوى", if (attachmentCount == 0) "نص فقط" else "$attachmentCount مرفق")
                    }
                }
                item("page-divider") { DocumentDivider() }
                items(document.blocks, key = { it.id }) { block ->
                    NotionDocumentBlock(
                        block = block,
                        focused = focusedBlock == block.id,
                        onFocus = { focusedBlock = block.id },
                        onChange = ::replace,
                        onToggle = { replace(block.copy(checked = !block.checked)) },
                        onDelete = { remove(block.id) },
                        onMoveUp = { move(block.id, -1) },
                        onMoveDown = { move(block.id, 1) },
                        onOpenImage = { vm.openImage(block.mediaPath) },
                        colors = fieldColors
                    )
                }
                item("continue-writing") {
                    Surface(onClick = { appendAndFocus(NotesBlock.text()) }, color = Color.Transparent, shape = Radius.shapeMd, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
                            Text("+", style = MaterialTheme.typography.titleLarge, color = c.textSecondary)
                            Spacer(Modifier.width(Space.sm))
                            Text("اكتب شيئاً أو أضف كتلة", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                        }
                    }
                }
            }

            BottomAppBar(Modifier.imePadding(), containerColor = c.surface) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    listOf(1, 2, 3).forEach { level ->
                        EditorTextTool("H$level", active = focusedOrFirst()?.style?.headingLevel == level) { styleFocused { it.copy(style = it.style.copy(headingLevel = level)) } }
                    }
                    EditorIconTool(Icons.Filled.FormatBold, "غامق") { mutateSpan { it.copy(bold = !it.bold) } }
                    EditorTextTool("مائل") { mutateSpan { it.copy(italic = !it.italic) } }
                    listOf(c.textPrimary, c.accent, c.danger.fg).forEach { swatch -> EditorColorTool(swatch) { mutateSpan { it.copy(foregroundArgb = swatch.value.toLong()) } } }
                    EditorIconTool(Icons.AutoMirrored.Filled.FormatListBulleted, "قائمة نقطية") { styleFocused { it.copy(style = it.style.copy(bullet = !it.style.bullet, numbered = false)) } }
                    EditorIconTool(Icons.Filled.Image, "إضافة صورة") { gallery.launch(arrayOf("image/*")) }
                    EditorIconTool(Icons.Filled.CheckBox, "قائمة تحقق") { appendAndFocus(NotesBlock.checklist()) }
                    EditorIconTool(Icons.Filled.MoreHoriz, "إضافة محتوى") { showInsert = true }
                }
            }
        }
    }

    if (showMore) NoteActionsSheet(note = liveNote.copy(title = title), vm = vm, onDismiss = { showMore = false }, onLabels = { showMore = false; showLabels = true }, onClose = onClose)
    if (showLabels) NoteLabelsSheetNew(note = liveNote, vm = vm, onDismiss = { showLabels = false })
    if (showInsert) {
        InsertContentSheet(
            onDismiss = { showInsert = false },
            onCamera = { showInsert = false; val file = vm.files.newImageFile(note.level, note.elementId); pendingCamera = file; camera.launch(vm.files.uriFor(file)) },
            onDrawing = { showInsert = false; drawingFile = vm.files.newNoteSketchFile(note.level, note.elementId) },
            onAudio = { showInsert = false; audioFile = vm.files.newNoteAudioFile(note.level, note.elementId) },
            onDivider = { showInsert = false; appendAndFocus(NotesBlock.divider()) }
        )
    }
    drawingFile?.let { file -> NotesDrawingSheet(file = file, onDismiss = { file.delete(); drawingFile = null }, onSaved = { saved -> appendAndFocus(NotesBlock.drawing(saved.absolutePath)); drawingFile = null }) }
    audioFile?.let { file -> NotesVoiceSheet(file = file, onDismiss = { file.delete(); audioFile = null }, onSaved = { saved, duration -> appendAndFocus(NotesBlock.audio(saved.absolutePath, duration)); audioFile = null }) }
}

@Composable
private fun PageProperty(label: String, value: String) {
    val c = LocalCwColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.width(72.dp))
        Surface(color = c.surfaceAlt, shape = Radius.pill) { Text(value, Modifier.padding(horizontal = Space.sm, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = c.textPrimary) }
    }
}

@Composable
private fun NotionDocumentBlock(
    block: NotesBlock,
    focused: Boolean,
    onFocus: () -> Unit,
    onChange: (NotesBlock) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenImage: () -> Unit,
    colors: androidx.compose.material3.TextFieldColors
) {
    val c = LocalCwColors.current
    val fullSpan = block.spans.firstOrNull { it.start == 0 && it.end == block.text.length }
    val base = when {
        block.style.headingLevel == 1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
        block.style.headingLevel == 2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        block.style.headingLevel == 3 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        block.type == NotesBlock.QUOTE -> MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
        else -> MaterialTheme.typography.bodyLarge
    }
    val textStyle = base.copy(
        fontWeight = if (fullSpan?.bold == true) FontWeight.Bold else base.fontWeight,
        fontStyle = if (fullSpan?.italic == true) FontStyle.Italic else base.fontStyle,
        color = fullSpan?.foregroundArgb?.let { Color(it) } ?: c.textPrimary
    )
    Surface(color = if (focused) c.surfaceAlt else Color.Transparent, shape = Radius.shapeMd, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (focused) Space.sm else 0.dp), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            when (block.type) {
                NotesBlock.IMAGE, NotesBlock.DRAWING -> DocumentImageBlock(block, onOpen = onOpenImage)
                NotesBlock.AUDIO -> DocumentAudioBlock(block)
                NotesBlock.DIVIDER -> DocumentDivider()
                NotesBlock.CHECKLIST -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = block.checked, onCheckedChange = { onToggle() })
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { onChange(block.copy(text = it)) },
                        placeholder = { Text("عنصر قائمة") },
                        colors = colors,
                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onFocus() },
                        singleLine = true
                    )
                }
                else -> Row(verticalAlignment = Alignment.Top) {
                    if (block.style.bullet || block.style.numbered) {
                        Text(if (block.style.numbered) "1." else "•", style = textStyle, modifier = Modifier.padding(top = Space.sm, end = Space.sm))
                    }
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = { onChange(block.copy(text = it)) },
                        placeholder = { Text("اكتب شيئاً…") },
                        textStyle = textStyle,
                        colors = colors,
                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) onFocus() }
                    )
                }
            }
            if (focused) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.KeyboardArrowUp, "نقل لأعلى", tint = c.textSecondary) }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.KeyboardArrowDown, "نقل لأسفل", tint = c.textSecondary) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) { Icon(Icons.Filled.DeleteOutline, "حذف الكتلة", tint = c.danger.fg) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsertContentSheet(onDismiss: () -> Unit, onCamera: () -> Unit, onDrawing: () -> Unit, onAudio: () -> Unit, onDivider: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("إضافة إلى الصفحة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("اختر نوع المحتوى الذي تريد إدراجه.", style = MaterialTheme.typography.bodySmall, color = LocalCwColors.current.textSecondary)
            NoteAction("التقاط صورة", Icons.Filled.CameraAlt, onClick = onCamera)
            NoteAction("رسم ميداني", Icons.Filled.Draw, onClick = onDrawing)
            NoteAction("تسجيل صوتي", Icons.Filled.Mic, onClick = onAudio)
            NoteAction("فاصل", Icons.Filled.MoreHoriz, onClick = onDivider)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteActionsSheet(note: NoteEntity, vm: MainViewModel, onDismiss: () -> Unit, onLabels: () -> Unit, onClose: () -> Unit) {
    val store = vm.notesStore
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("خيارات الصفحة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            NoteAction(if (note.pinned) "إلغاء التثبيت" else "تثبيت الصفحة", Icons.Filled.Add) { store.togglePin(note); onDismiss() }
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
            Spacer(Modifier.width(Space.md))
            Text(label, style = MaterialTheme.typography.bodyLarge)
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
            Text("وسوم الصفحة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            labels.forEach { label ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = label.id in selected, onCheckedChange = { vm.notesStore.setLabel(note.id, label.id, it) })
                    Text(label.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
