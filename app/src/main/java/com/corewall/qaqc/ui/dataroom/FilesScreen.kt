package com.corewall.qaqc.ui.dataroom

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.notes.rememberPdfThumb
import com.corewall.qaqc.ui.notes.rememberThumb
import com.corewall.qaqc.ui.theme.LocalAppGradients
import com.corewall.qaqc.ui.theme.LocalSrtColors
import com.corewall.qaqc.ui.theme.TowerNumberStyle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd/MM/yyyy · hh:mm a", Locale.ENGLISH)

private fun sizeText(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun isImage(f: File) = f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")
private fun isPdf(f: File) = f.extension.equals("pdf", ignoreCase = true)

/** يستخرج التخصص من اسم الملف (ARCH/STRUCT/MEP/CIVIL) — ميزة هندسية. */
private fun disciplineOf(name: String): String? {
    val u = name.uppercase()
    return when {
        "ARCH" in u -> "ARCH"
        "STRUCT" in u || Regex("(^|[^A-Z])STR([^A-Z]|$)").containsMatchIn(u) -> "STRUCT"
        "MEP" in u || "ELEC" in u || "MECH" in u || "PLUMB" in u || "HVAC" in u -> "MEP"
        "CIVIL" in u || "CIV" in u -> "CIVIL"
        else -> null
    }
}

private fun revisionOf(name: String): String? =
    Regex("(?i)rev[ _-]?(\\d+)").find(name)?.let { "Rev ${it.groupValues[1]}" }

@Composable
private fun disciplineColor(d: String): Color {
    val srt = LocalSrtColors.current
    return when (d) {
        "ARCH" -> srt.blue
        "STRUCT" -> srt.orange
        "MEP" -> srt.green
        "CIVIL" -> srt.purple
        else -> srt.text3
    }
}

private enum class SortMode(val label: String) { NAME("الاسم"), NEWEST("الأحدث"), SIZE("الحجم") }

/**
 * مركز الوثائق الهندسي: مكتبة مستندات مستقلة لكل دور — هيدر متدرّج بإحصائيات،
 * إجراءات سريعة، بحث/فرز/عرض شبكي، كروت مجلدات وملفات بمعاينات ومعلومات هندسية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val srt = LocalSrtColors.current
    val gradient = LocalAppGradients.current.header
    val level by vm.currentLevel.collectAsStateWithLifecycle()

    var subPath by remember(level) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var detailTarget by remember { mutableStateOf<File?>(null) }
    var actionTarget by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    // (ملف, هل نقل؟) — لاختيار الدور الهدف للنسخ/النقل
    var floorPick by remember { mutableStateOf<Pair<File, Boolean>?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var gridMode by remember { mutableStateOf(true) }
    var sortMode by remember { mutableStateOf(SortMode.NAME) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val currentDir = remember(level, subPath, refresh) {
        val base = vm.files.levelDir(level)
        if (subPath.isEmpty()) base else File(base, subPath)
    }
    val allEntries = remember(currentDir, refresh) { vm.files.list(currentDir) }
    val entries = remember(allEntries, query, sortMode) {
        allEntries
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(
                compareByDescending<File> { it.isDirectory }.thenComparator { a, b ->
                    when (sortMode) {
                        SortMode.NAME -> a.name.lowercase().compareTo(b.name.lowercase())
                        SortMode.NEWEST -> b.lastModified().compareTo(a.lastModified())
                        SortMode.SIZE -> vm.files.sizeOf(b).compareTo(vm.files.sizeOf(a))
                    }
                }
            )
    }
    val folders = entries.filter { it.isDirectory }
    val docs = entries.filter { it.isFile }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val copied = vm.files.importUris(uris, currentDir)
            refresh++
            Toast.makeText(context, "اترفع ${copied.size} ملف ✓", Toast.LENGTH_SHORT).show()
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) { refresh++; Toast.makeText(context, "اتصوّرت الصورة ✓", Toast.LENGTH_SHORT).show() }
    }
    fun capture() {
        val f = File(currentDir, "IMG_${System.currentTimeMillis()}.jpg")
        photoUri = vm.files.uriFor(f)
        runCatching { takePhoto.launch(photoUri!!) }
            .onFailure { Toast.makeText(context, "مفيش كاميرا متاحة", Toast.LENGTH_SHORT).show() }
    }

    // إحصائيات للهيدر
    val baseDir = remember(level, refresh) { vm.files.levelDir(level) }
    val topEntries = remember(baseDir, refresh) { vm.files.list(baseDir) }
    val folderCount = topEntries.count { it.isDirectory }
    val fileCount = remember(baseDir, refresh) { baseDir.walkTopDown().count { it.isFile } }
    val totalSize = remember(baseDir, refresh) { vm.files.sizeOf(baseDir) }
    val lastMod = remember(baseDir, refresh) { baseDir.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- الهيدر ----
        item(span = { GridItemSpan(maxLineSpan) }) {
            HeaderCard(gradient, level, folderCount, fileCount, totalSize, lastMod, subPath,
                onBack = { subPath = subPath.substringBeforeLast('/', "") })
        }
        // ---- إجراءات سريعة ----
        item(span = { GridItemSpan(maxLineSpan) }) {
            QuickActionsRow(
                onNewFolder = { newFolderDialog = true },
                onUpload = { pickFiles.launch(arrayOf("*/*")) },
                onCamera = { capture() },
                onRecent = { sortMode = SortMode.NEWEST }
            )
        }
        // ---- شريط الأدوات ----
        item(span = { GridItemSpan(maxLineSpan) }) {
            Toolbar(
                searchActive = searchActive, query = query,
                onToggleSearch = { searchActive = !searchActive; if (!searchActive) query = "" },
                onQuery = { query = it },
                sortMode = sortMode, onSort = { sortMode = it },
                gridMode = gridMode, onToggleView = { gridMode = !gridMode }
            )
        }

        if (entries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyFiles(onUpload = { pickFiles.launch(arrayOf("*/*")) }, onNewFolder = { newFolderDialog = true })
            }
        }

        if (folders.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("المجلدات", folders.size) }
            items(folders, span = { GridItemSpan(1) }, key = { "d-${it.name}" }) { f ->
                FolderCard(f, vm, onOpen = { subPath = if (subPath.isEmpty()) f.name else "$subPath/${f.name}" },
                    onMenu = { actionTarget = f })
            }
        }
        if (docs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("الملفات", docs.size) }
            items(
                docs,
                span = { GridItemSpan(if (gridMode) 1 else maxLineSpan) },
                key = { "f-${it.name}" }
            ) { f ->
                if (gridMode) FileGridCard(f, onOpen = { openFile(vm, context, f) }, onMenu = { actionTarget = f })
                else FileListRow(f, onOpen = { openFile(vm, context, f) }, onMenu = { actionTarget = f })
            }
        }
    }

    // ---- الشيتات والديالوجات ----
    if (newFolderDialog) {
        CreateFolderSheet(
            onDismiss = { newFolderDialog = false },
            onCreate = { name -> vm.files.createFolder(currentDir, name); refresh++; newFolderDialog = false }
        )
    }

    actionTarget?.let { f ->
        FileActionSheet(
            file = f,
            onDismiss = { actionTarget = null },
            onOpen = { actionTarget = null; if (f.isDirectory) { subPath = if (subPath.isEmpty()) f.name else "$subPath/${f.name}" } else openFile(vm, context, f) },
            onShare = { actionTarget = null; if (!f.isDirectory) vm.files.share(f) },
            onRename = { actionTarget = null; renameTarget = f },
            onDuplicate = { actionTarget = null; vm.files.duplicate(f); refresh++; Toast.makeText(context, "اتعمل نسخة ✓", Toast.LENGTH_SHORT).show() },
            onCopyFloor = { actionTarget = null; floorPick = f to false },
            onMoveFloor = { actionTarget = null; floorPick = f to true },
            onDetails = { actionTarget = null; detailTarget = f },
            onDelete = { actionTarget = null; deleteTarget = f }
        )
    }

    floorPick?.let { (f, isMove) ->
        com.corewall.qaqc.ui.LevelPickerDialog(
            levels = vm.levels,
            current = level,
            title = if (isMove) "نقل إلى دور" else "نسخ إلى دور",
            onPick = { target ->
                val dir = vm.files.levelDir(target)
                val ok = if (isMove) vm.files.moveInto(f, dir) else vm.files.copyInto(f, dir)
                refresh++
                floorPick = null
                Toast.makeText(context, if (ok) "تم ${if (isMove) "النقل" else "النسخ"} إلى $target ✓" else "فشلت العملية", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { floorPick = null }
        )
    }

    detailTarget?.let { f ->
        FileDetailSheet(
            file = f, vm = vm,
            onDismiss = { detailTarget = null },
            onOpen = { detailTarget = null; if (f.isDirectory) { subPath = if (subPath.isEmpty()) f.name else "$subPath/${f.name}" } else openFile(vm, context, f) },
            onShare = { if (!f.isDirectory) vm.files.share(f) },
            onRename = { detailTarget = null; renameTarget = f },
            onDelete = { detailTarget = null; deleteTarget = f }
        )
    }

    renameTarget?.let { f ->
        var name by remember(f) { mutableStateOf(f.nameWithoutExtension) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("إعادة تسمية") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("الاسم الجديد") }) },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    if (vm.files.rename(f, name)) { refresh++; renameTarget = null }
                    else Toast.makeText(context, "الاسم موجود بالفعل", Toast.LENGTH_SHORT).show()
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("إلغاء") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف ${target.name}؟") },
            text = { Text(if (target.isDirectory) "المجلد وكل اللي جوّاه هيتحذف نهائي." else "الملف هيتحذف نهائي.") },
            confirmButton = { Button(onClick = { vm.files.delete(target); refresh++; deleteTarget = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") } }
        )
    }
}

private fun openFile(vm: MainViewModel, context: android.content.Context, f: File) {
    if (isPdf(f)) vm.openPdf(f.absolutePath)
    else if (isImage(f)) vm.openImage(f.absolutePath)
    else if (!vm.files.openExternally(f)) Toast.makeText(context, "مفيش تطبيق يفتح الملف ده", Toast.LENGTH_SHORT).show()
}

// ---------------------------------------------------------------- الهيدر

@Composable
private fun HeaderCard(
    gradient: List<Color>, level: String, folders: Int, files: Int, size: Long, lastMod: Long?,
    subPath: String, onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(gradient))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (subPath.isNotEmpty()) {
                Surface(onClick = onBack, shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.2f)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "رجوع", tint = Color.White, modifier = Modifier.padding(8.dp).size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text("مركز الوثائق", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                Text(if (subPath.isEmpty()) level else subPath.substringAfterLast('/'), style = TowerNumberStyle.copy(fontSize = 34.sp), color = Color.White)
            }
        }
        Spacer(Modifier.height(16.dp))
        Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                HeaderStat("$folders", "مجلدات", Modifier.weight(1f))
                HeaderDivider()
                HeaderStat("$files", "ملفات", Modifier.weight(1f))
                HeaderDivider()
                HeaderStat(sizeText(size), "مستخدَم", Modifier.weight(1f))
                HeaderDivider()
                HeaderStat(if (lastMod != null) relTime(lastMod) else "—", "آخر تحديث", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun HeaderDivider() {
    Box(Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.25f)))
}

// ---------------------------------------------------------------- إجراءات سريعة

@Composable
private fun QuickActionsRow(onNewFolder: () -> Unit, onUpload: () -> Unit, onCamera: () -> Unit, onRecent: () -> Unit) {
    val srt = LocalSrtColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction("مجلد جديد", Icons.Filled.CreateNewFolder, srt.blue, Modifier.weight(1f), onNewFolder)
        QuickAction("رفع ملف", Icons.Filled.UploadFile, srt.green, Modifier.weight(1f), onUpload)
        QuickAction("تصوير", Icons.Filled.PhotoCamera, srt.orange, Modifier.weight(1f), onCamera)
        QuickAction("الأحدث", Icons.Filled.Sort, srt.purple, Modifier.weight(1f), onRecent)
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- شريط الأدوات

@Composable
private fun Toolbar(
    searchActive: Boolean, query: String, onToggleSearch: () -> Unit, onQuery: (String) -> Unit,
    sortMode: SortMode, onSort: (SortMode) -> Unit, gridMode: Boolean, onToggleView: () -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(Icons.Filled.Search, "بحث", active = searchActive, onClick = onToggleSearch)
            Spacer(Modifier.width(8.dp))
            var sortOpen by remember { mutableStateOf(false) }
            Box {
                ToolIcon(Icons.Filled.Sort, "فرز", active = false, onClick = { sortOpen = true })
                androidx.compose.material3.DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    SortMode.entries.forEach { m ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(m.label) }, onClick = { onSort(m); sortOpen = false })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            ToolIcon(if (gridMode) Icons.Filled.ViewAgenda else Icons.Filled.GridView, "طريقة العرض", active = false, onClick = onToggleView)
        }
        AnimatedVisibility(visible = searchActive) {
            Surface(
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query, onValueChange = onQuery, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box(Modifier.padding(vertical = 12.dp)) {
                                if (query.isEmpty()) Text("ابحث باسم الملف…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, active: Boolean, onClick: () -> Unit) {
    val srt = LocalSrtColors.current
    Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = if (active) srt.blueTint else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Icon(icon, contentDescription = cd, tint = if (active) srt.blue else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(9.dp).size(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String, count: Int) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = LocalSrtColors.current.text3)
    }
}

// ---------------------------------------------------------------- كروت المجلدات والملفات

@Composable
private fun FolderCard(f: File, vm: MainViewModel, onOpen: () -> Unit, onMenu: () -> Unit) {
    val srt = LocalSrtColors.current
    val count = remember(f) { f.listFiles()?.size ?: 0 }
    val size = remember(f) { vm.files.sizeOf(f) }
    val accent = folderColor(f.name, srt)
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.weight(1f))
                MenuDot(onMenu)
            }
            Spacer(Modifier.height(10.dp))
            Text(f.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("مجلد مستندات", style = MaterialTheme.typography.labelSmall, color = srt.text3, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text("$count عنصر · ${sizeText(size)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileGridCard(f: File, onOpen: () -> Unit, onMenu: () -> Unit) {
    val srt = LocalSrtColors.current
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 1.dp
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                FileThumbnail(f, Modifier.fillMaxSize())
                // شارات هندسية
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    disciplineOf(f.name)?.let { Chip(it, disciplineColor(it)) }
                    revisionOf(f.name)?.let { Chip("$it · LATEST", srt.green) }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = RoundedCornerShape(10.dp)) {
                        MenuDot(onMenu)
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(f.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("${f.extension.uppercase().ifBlank { "FILE" }} · ${sizeText(f.length())}", style = MaterialTheme.typography.labelSmall, color = srt.text3)
            }
        }
    }
}

@Composable
private fun FileListRow(f: File, onOpen: () -> Unit, onMenu: () -> Unit) {
    val srt = LocalSrtColors.current
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                FileThumbnail(f, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${f.extension.uppercase()} · ${sizeText(f.length())}", style = MaterialTheme.typography.labelSmall, color = srt.text3)
                    disciplineOf(f.name)?.let { Spacer(Modifier.width(6.dp)); Chip(it, disciplineColor(it)) }
                }
            }
            MenuDot(onMenu)
        }
    }
}

@Composable
private fun MenuDot(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = Color.Transparent) {
        Icon(
            Icons.Filled.MoreVert, contentDescription = "خيارات",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(6.dp).size(20.dp)
        )
    }
}

@Composable
private fun FileThumbnail(f: File, modifier: Modifier = Modifier) {
    when {
        isPdf(f) -> {
            val bmp = rememberPdfThumb(f.absolutePath)
            if (bmp != null) androidx.compose.foundation.Image(bmp.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
            else CenterIcon(Icons.Filled.PictureAsPdf, LocalSrtColors.current.red)
        }
        isImage(f) -> {
            val bmp = rememberThumb(f.absolutePath)
            if (bmp != null) androidx.compose.foundation.Image(bmp.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
            else CenterIcon(Icons.Filled.Image, LocalSrtColors.current.blue)
        }
        f.extension.lowercase() in listOf("xls", "xlsx", "csv") -> CenterIcon(Icons.Filled.TableChart, LocalSrtColors.current.green)
        else -> CenterIcon(Icons.Filled.InsertDriveFile, LocalSrtColors.current.text3)
    }
}

@Composable
private fun CenterIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(6.dp)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- شيت الإجراءات (⋮)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileActionSheet(
    file: File, onDismiss: () -> Unit, onOpen: () -> Unit, onShare: () -> Unit,
    onRename: () -> Unit, onDuplicate: () -> Unit, onCopyFloor: () -> Unit,
    onMoveFloor: () -> Unit, onDetails: () -> Unit, onDelete: () -> Unit
) {
    val srt = LocalSrtColors.current
    val isDir = file.isDirectory
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // رأس صغير بالملف
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (isDir) CenterIcon(Icons.Filled.Folder, srt.blue) else FileThumbnail(file, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (isDir) "مجلد" else "${file.extension.uppercase()} · ${sizeText(file.length())}", style = MaterialTheme.typography.labelSmall, color = srt.text3)
                }
            }
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp), color = srt.divider)

            ActionRow(Icons.Filled.OpenInNew, "فتح", if (isDir) "افتح المجلد" else "افتح الملف", srt.blue, onOpen)
            ActionRow(Icons.Filled.DriveFileRenameOutline, "إعادة تسمية", "غيّر اسم ${if (isDir) "المجلد" else "الملف"}", srt.orange, onRename)
            if (!isDir) ActionRow(Icons.Filled.Share, "مشاركة", "أرسل لتطبيق تاني", srt.green, onShare)
            ActionRow(Icons.Filled.ContentCopy, "تكرار", "اعمل نسخة في نفس المكان", srt.purple, onDuplicate)
            ActionRow(Icons.Filled.MoveToInbox, "نسخ إلى دور", "انسخه لمكتبة دور تاني", srt.blue, onCopyFloor)
            ActionRow(Icons.Filled.DriveFileMove, "نقل إلى دور", "انقله لمكتبة دور تاني", srt.orange, onMoveFloor)
            ActionRow(Icons.Filled.Info, "التفاصيل", "معاينة، الحجم، التاريخ، المالك", srt.text3, onDetails)
            ActionRow(Icons.Filled.Delete, "حذف", "امسح نهائي", srt.red, onDelete)
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- شيت التفاصيل

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileDetailSheet(
    file: File, vm: MainViewModel, onDismiss: () -> Unit, onOpen: () -> Unit,
    onShare: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit
) {
    val srt = LocalSrtColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            if (!file.isDirectory) {
                Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    FileThumbnail(file, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(file.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            DetailRow("النوع", if (file.isDirectory) "مجلد" else file.extension.uppercase().ifBlank { "ملف" })
            if (!file.isDirectory) DetailRow("الحجم", sizeText(file.length()))
            else DetailRow("المحتوى", "${file.listFiles()?.size ?: 0} عنصر · ${sizeText(vm.files.sizeOf(file))}")
            DetailRow("آخر تعديل", dateFormat.format(Date(file.lastModified())))
            disciplineOf(file.name)?.let { DetailRow("التخصص", it) }
            revisionOf(file.name)?.let { DetailRow("المراجعة", "$it (الأحدث)") }
            DetailRow("المالك", "م. أحمد حسن")
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailAction("فتح", Icons.Filled.OpenInNew, srt.blue, Modifier.weight(1f), onOpen)
                if (!file.isDirectory) DetailAction("مشاركة", Icons.Filled.Share, srt.green, Modifier.weight(1f), onShare)
                DetailAction("تسمية", Icons.Filled.DriveFileRenameOutline, srt.orange, Modifier.weight(1f), onRename)
                DetailAction("حذف", Icons.Filled.Delete, srt.red, Modifier.weight(1f), onDelete)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DetailAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.12f), modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------------------------------------------------------------- إنشاء مجلد

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateFolderSheet(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("مجلد جديد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المجلد") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(onClick = { if (name.isNotBlank()) onCreate(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("إنشاء")
            }
        }
    }
}

// ---------------------------------------------------------------- حالة فاضية

@Composable
private fun EmptyFiles(onUpload: () -> Unit, onNewFolder: () -> Unit) {
    val srt = LocalSrtColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(srt.blueTint), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = srt.blue, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("لسه مفيش ملفات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("ارفع شوب دروينج، BBS، دليفري نوت، أوتوكاد، صور…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onUpload, modifier = Modifier.height(50.dp)) {
            Icon(Icons.Filled.UploadFile, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("رفع ملفات")
        }
    }
}

// ---------------------------------------------------------------- مساعدات

private fun folderColor(name: String, srt: com.corewall.qaqc.ui.theme.SrtColors): Color {
    val palette = listOf(srt.blue, srt.green, srt.orange, srt.red, srt.purple)
    val idx = (name.hashCode() and 0x7fffffff) % palette.size
    return palette[idx]
}

private fun relTime(ts: Long): String {
    val min = (System.currentTimeMillis() - ts) / 60000
    return when {
        min < 1 -> "الآن"
        min < 60 -> "$min د"
        min < 1440 -> "${min / 60} س"
        else -> "${min / 1440} يوم"
    }
}
