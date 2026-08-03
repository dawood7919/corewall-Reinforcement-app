package com.corewall.qaqc.ui.notes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.NoteEntity
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.io.File

private enum class Mode { EDIT, PREVIEW, SPLIT }

/**
 * محرّر الملاحظات الهندسي — كامل الشاشة، أوضاع تحرير/معاينة/مقسّم، شريط
 * تنسيق ثابت فوق الكيبورد، تنسيق حيّ، حفظ تلقائي، صور وكاميرا وملفات inline،
 * وإكمال تلقائي للوسوم (#) والمنشن (@).
 */
@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(vm: MainViewModel, note: NoteEntity, onClose: () -> Unit) {
    val markName = vm.markFor(note.elementId) ?: note.elementId

    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(TextFieldValue(note.body)) }
    var savedNoteId by remember(note.id) { mutableIntStateOf(note.id.toInt()) }
    var mode by remember { mutableStateOf(Mode.EDIT) }
    var savedState by remember { mutableStateOf("محفوظ") }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var overflow by remember { mutableStateOf(false) }

    // Undo / Redo
    val undo = remember(note.id) { ArrayDeque<TextFieldValue>() }
    val redo = remember(note.id) { ArrayDeque<TextFieldValue>() }

    fun edit(newValue: TextFieldValue) {
        if (newValue.text != body.text) {
            undo.addLast(body); if (undo.size > 60) undo.removeFirst(); redo.clear()
            savedState = "بيحفظ…"
        }
        body = newValue
    }

    fun currentNote() = note.copy(
        id = savedNoteId.toLong(),
        title = title.trim(),
        body = body.text,
        imagePathsJson = encodeImagePaths(allMediaPaths(body.text))
    )

    // حفظ تلقائي بعد سكون بسيط
    LaunchedEffect(note.id) {
        snapshotFlow { title to body.text }.debounce(900).collect {
            vm.autosaveNote(currentNote()) { id -> savedNoteId = id.toInt() }
            savedState = "محفوظ ✓"
        }
    }

    // ---- إدراج ----
    fun insert(textToInsert: String) {
        val sel = body.selection
        val t = body.text
        val newText = t.substring(0, sel.min) + textToInsert + t.substring(sel.max)
        edit(TextFieldValue(newText, androidx.compose.ui.text.TextRange(sel.min + textToInsert.length)))
    }
    fun wrap(token: String) {
        val sel = body.selection; val t = body.text
        val selected = t.substring(sel.min, sel.max).ifEmpty { "نص" }
        val newText = t.substring(0, sel.min) + token + selected + token + t.substring(sel.max)
        edit(TextFieldValue(newText, androidx.compose.ui.text.TextRange(sel.min + token.length + selected.length + token.length)))
    }
    fun linePrefix(prefix: String) {
        val sel = body.selection; val t = body.text
        val ls = t.lastIndexOf('\n', (sel.min - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val newText = t.substring(0, ls) + prefix + t.substring(ls)
        edit(TextFieldValue(newText, androidx.compose.ui.text.TextRange(sel.min + prefix.length)))
    }
    fun insertBlock(block: String) = insert("\n$block\n")

    // ---- الصور / الملفات ----
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            vm.files.importNoteImages(uris, note.level, note.elementId).also { vm.registerFiles(it, note.level) }.forEach {
                insert("\n![](${it.absolutePath})\n")
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCamera
        if (ok && f != null && f.exists()) insert("\n![](${f.absolutePath})\n") else f?.delete()
        pendingCamera = null
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            vm.files.importNoteImages(uris, note.level, note.elementId).also { vm.registerFiles(it, note.level) }.forEach {
                insert("\n[[file:${it.absolutePath}]]\n")
            }
        }
    }

    val colors = rememberInlineColors()
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val transform = liveMarkdownTransformation(colors, accent, muted, if (showSearch) query else "")

    // إكمال تلقائي (@ / #)
    val trigger = activeTrigger(body)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // ===== الشريط العلوي =====
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { vm.saveNote(currentNote()); onClose() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "رجوع")
                        }
                        Column(Modifier.weight(1f)) {
                            Text("$markName · دور ${note.level}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(savedState, style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Filled.Search, contentDescription = "بحث")
                        }
                        Surface(
                            onClick = { vm.saveNote(currentNote()); onClose() },
                            color = accent, contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("حفظ", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                        }
                        Box {
                            IconButton(onClick = { overflow = true }) {
                                Text("⋮", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                if (note.id != 0L || savedNoteId != 0) {
                                    DropdownMenuItem(
                                        text = { Text("حذف الملاحظة", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = { overflow = false; vm.deleteNote(currentNote()); onClose() }
                                    )
                                }
                            }
                        }
                    }
                    // أوضاع العرض
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val modes = listOf(Mode.EDIT to "تحرير", Mode.PREVIEW to "معاينة", Mode.SPLIT to "مقسّم")
                        modes.forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = mode == m,
                                onClick = { mode = m },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                icon = {}
                            ) { Text(label) }
                        }
                    }
                    AnimatedVisibility(visible = showSearch) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = muted)
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = query, onValueChange = { query = it }, singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(accent),
                                    decorationBox = { inner -> Box(Modifier.padding(vertical = 12.dp)) { if (query.isEmpty()) Text("بحث داخل الملاحظة…", color = muted, style = MaterialTheme.typography.bodyMedium); inner() } },
                                    modifier = Modifier.weight(1f)
                                )
                                val count = if (query.isBlank()) 0 else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(body.text).count()
                                if (query.isNotBlank()) Text("$count", style = MaterialTheme.typography.labelMedium, color = accent)
                            }
                        }
                    }
                }
            }

            // ===== المحتوى =====
            Box(Modifier.weight(1f)) {
                Row(Modifier.fillMaxSize()) {
                    if (mode == Mode.EDIT || mode == Mode.SPLIT) {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            BasicTextField(
                                value = title, onValueChange = { title = it }, singleLine = true,
                                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold),
                                cursorBrush = SolidColor(accent),
                                decorationBox = { inner -> if (title.isEmpty()) Text("عنوان الملاحظة", style = MaterialTheme.typography.headlineSmall, color = muted, fontWeight = FontWeight.Bold); inner() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            BasicTextField(
                                value = body, onValueChange = { edit(it) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(accent),
                                visualTransformation = transform,
                                decorationBox = { inner -> if (body.text.isEmpty()) Text("ابدأ الكتابة… استخدم شريط الأدوات تحت للتنسيق والصور.", style = MaterialTheme.typography.bodyLarge, color = muted); inner() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (mode == Mode.SPLIT) 1000.dp else 1200.dp)
                            )
                        }
                    }
                    if (mode == Mode.SPLIT) {
                        Box(Modifier.width(1.dp).fillMaxSize().padding(vertical = 8.dp)) {
                            Surface(color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxSize()) {}
                        }
                    }
                    if (mode == Mode.PREVIEW || mode == Mode.SPLIT) {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            if (title.isNotBlank()) {
                                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(10.dp))
                            }
                            NoteContent(
                                markdown = body.text,
                                onToggleCheck = { line -> edit(TextFieldValue(toggleCheckLine(body.text, line))) },
                                onOpenImage = { vm.openImage(it) },
                                onOpenFile = { p ->
                                    val f = File(p)
                                    if (f.extension.equals("pdf", true)) vm.openPdf(f.absolutePath) else vm.files.openExternally(f)
                                }
                            )
                        }
                    }
                }
            }

            // ===== الإكمال التلقائي =====
            if (trigger != null && mode != Mode.PREVIEW) {
                val (symbol, partial) = trigger
                val suggestions = remember(symbol, partial, body.text) {
                    if (symbol == '@') vm.allMarks().filter { it.contains(partial, true) }.take(20)
                    else listOf("Concrete", "Rebar", "WIR", "Inspection", "Formwork", "Approved", "Rejected")
                        .filter { it.contains(partial, true) || partial.isEmpty() }
                }
                if (suggestions.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        LazyRow(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(suggestions) { s ->
                                Surface(
                                    onClick = { edit(applySuggestion(body, symbol, partial, s)) },
                                    color = if (symbol == '@') MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("$symbol$s", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // ===== شريط الأدوات (فوق الكيبورد) =====
            if (mode != Mode.PREVIEW) {
                var calloutMenu by remember { mutableStateOf(false) }
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Tb(Icons.AutoMirrored.Filled.Undo, "تراجع", enabled = undo.isNotEmpty()) {
                            undo.removeLastOrNull()?.let { redo.addLast(body); body = it }
                        }
                        Tb(Icons.AutoMirrored.Filled.Redo, "إعادة", enabled = redo.isNotEmpty()) {
                            redo.removeLastOrNull()?.let { undo.addLast(body); body = it }
                        }
                        TbDivider()
                        Tb(Icons.Filled.Title, "عنوان") { linePrefix("## ") }
                        Tb(Icons.Filled.FormatBold, "غامق") { wrap("**") }
                        Tb(Icons.Filled.FormatItalic, "مائل") { wrap("*") }
                        Tb(Icons.Filled.FormatUnderlined, "تحته خط") { wrap("__") }
                        Tb(Icons.Filled.FormatStrikethrough, "شطب") { wrap("~~") }
                        Tb(Icons.Filled.Edit, "تظليل") { wrap("==") }
                        TbDivider()
                        Tb(Icons.AutoMirrored.Filled.FormatListBulleted, "نقاط") { linePrefix("- ") }
                        Tb(Icons.Filled.FormatListNumbered, "ترقيم") { linePrefix("1. ") }
                        Tb(Icons.Filled.CheckBox, "تشيك ليست") { linePrefix("- [ ] ") }
                        Tb(Icons.Filled.FormatQuote, "اقتباس") { linePrefix("> ") }
                        Tb(Icons.Filled.Code, "كود") { insertBlock("```\n\n```") }
                        Tb(Icons.Filled.HorizontalRule, "فاصل") { insertBlock("---") }
                        Tb(Icons.Filled.TableChart, "جدول") { insertBlock("| عمود | عمود |\n| --- | --- |\n| قيمة | قيمة |") }
                        Box {
                            Tb(Icons.Filled.WarningAmber, "تنبيه") { calloutMenu = true }
                            DropdownMenu(expanded = calloutMenu, onDismissRequest = { calloutMenu = false }) {
                                CalloutKind.entries.forEach { k ->
                                    DropdownMenuItem(
                                        text = { Text(k.label) },
                                        onClick = { calloutMenu = false; insertBlock("> [!${k.token}] ${k.label}\n> ") }
                                    )
                                }
                            }
                        }
                        TbDivider()
                        Tb(Icons.Filled.PhotoLibrary, "صور", tint = accent) { gallery.launch(arrayOf("image/*")) }
                        Tb(Icons.Filled.PhotoCamera, "كاميرا", tint = accent) {
                            val f = vm.files.newImageFile(note.level, note.elementId)
                            pendingCamera = f; camera.launch(vm.files.uriFor(f))
                        }
                        Tb(Icons.Filled.AttachFile, "ملف", tint = accent) { filePicker.launch(arrayOf("*/*")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tb(icon: ImageVector, desc: String, enabled: Boolean = true, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = desc, tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

@Composable
private fun TbDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
    ) { Surface(color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxSize()) {} }
}

// ---------------------------------------------------------------- Helpers

/** كل مسارات الصور/الملفات المضمّنة في النص (للتنظيف عند الحذف). */
private fun allMediaPaths(md: String): List<String> {
    val imgs = Regex("""!\[.*?]\((.+?)\)""").findAll(md).map { it.groupValues[1] }
    val files = Regex("""\[\[file:(.+?)]]""").findAll(md).map { it.groupValues[1] }
    return (imgs + files).toList()
}

/** يبدّل حالة سطر تشيك ليست معيّن. */
private fun toggleCheckLine(md: String, lineIndex: Int): String {
    val lines = md.split("\n").toMutableList()
    if (lineIndex !in lines.indices) return md
    val l = lines[lineIndex]
    lines[lineIndex] = when {
        Regex("""\[ ]""").containsMatchIn(l) -> l.replaceFirst("[ ]", "[x]")
        Regex("""\[[xX]]""").containsMatchIn(l) -> l.replaceFirst(Regex("""\[[xX]]"""), "[ ]")
        else -> l
    }
    return lines.joinToString("\n")
}

/** يكتشف وسم/منشن نشط قبل المؤشر (للإكمال التلقائي). */
private fun activeTrigger(v: TextFieldValue): Pair<Char, String>? {
    val cursor = v.selection.min
    if (cursor == 0) return null
    val text = v.text.substring(0, cursor)
    var i = text.length - 1
    while (i >= 0) {
        val ch = text[i]
        if (ch == '@' || ch == '#') {
            val partial = text.substring(i + 1)
            if (partial.any { it.isWhitespace() }) return null
            return ch to partial
        }
        if (ch.isWhitespace()) return null
        i--
    }
    return null
}

private fun applySuggestion(v: TextFieldValue, symbol: Char, partial: String, picked: String): TextFieldValue {
    val cursor = v.selection.min
    val start = cursor - partial.length - 1 // -1 للرمز
    val newText = v.text.substring(0, start) + symbol + picked + " " + v.text.substring(cursor)
    val newCursor = start + 1 + picked.length + 1
    return TextFieldValue(newText, androidx.compose.ui.text.TextRange(newCursor))
}
