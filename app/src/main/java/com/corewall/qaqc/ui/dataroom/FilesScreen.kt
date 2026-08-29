package com.corewall.qaqc.ui.dataroom

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.ai.AnalyzePromptSheet
import com.corewall.qaqc.ui.nav.Dest
import com.corewall.qaqc.data.FileSearchHit
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwListItem
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Motion
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fileDate = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)

/** الفلتر الفعّال على القايمة. */
private enum class FileFilter(val label: String) {
    ALL("الكل"),
    FAVOURITES("المفضّلة"),
    RECENT("الأخيرة")
}

/**
 * مركز الملفات.
 *
 * أهم تغييرين هنا مش شكليين:
 *
 * ١) الصور المصغّرة بقت من Coil. القديم كان بيفك ترميز كل صورة من الأول في
 *    كل تمرير من غير كاش ولا إلغاء — أكتر مكان في التطبيق معرّض للتهتهة.
 *
 * ٢) البحث بقى بيلف على **النصّ اللي جوّه الملفات** كمان، مش الأسماء بس.
 *    دي كانت أسوأ رحلة في التطبيق: تدوّر على جدول تسليح W12 يبقى قدامك
 *    قايمة مسطّحة تفتح منها PDF ورا PDF. دلوقتي النتيجة بتقولك **ليه**
 *    ظهرت — في الاسم ولا في الوسوم ولا جوّه الملف.
 */
@Composable
fun FilesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val metaMap by vm.fileMeta.collectAsStateWithLifecycle()
    val query by vm.fileQuery.collectAsStateWithLifecycle()
    val results by vm.fileResults.collectAsStateWithLifecycle()
    val tags by vm.fileTags.collectAsStateWithLifecycle()
    val favourites by vm.fileFavourites.collectAsStateWithLifecycle()
    val recent by vm.fileRecent.collectAsStateWithLifecycle()
    val documents by vm.documents.collectAsStateWithLifecycle()
    val filesRevision by vm.filesRevision.collectAsStateWithLifecycle()

    var subPath by rememberSaveable(level) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var grid by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf(FileFilter.ALL) }
    var activeTag by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var newFolder by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    /** الملف اللي مستني اختيار برومبت قبل ما يتحلّل. */
    var analyzeTarget by remember { mutableStateOf<File?>(null) }
    var analyzingPath by remember { mutableStateOf<String?>(null) }
    val prompts by vm.prompts.collectAsStateWithLifecycle()

    val currentDir = remember(level, subPath, refresh) {
        val base = vm.files.levelDir(level)
        if (subPath.isEmpty()) base else File(base, subPath)
    }
    LaunchedEffect(level) { vm.loadKnowledge() }
    val analysisByPath = remember(documents) { documents.associateBy { it.filePath } }
    /**
     * قايمة المجلد — **بتتقري من القرص في الخلفية**.
     *
     * قبل كده كانت `remember { vm.files.list(dir) }`، يعني `listFiles()`
     * وفرز النتيجة كانوا بيتنفّذوا وسط التركيب على خيط الواجهة. على
     * التخزين الخارجي دي عملية قرص حقيقية، فكل دخول للملفات وكل فتح مجلد
     * كان بيوقّف الإطار — وده بالظبط الإحساس بإن "الشاشة بتاخد وقت تظهر".
     *
     * دلوقتي الشاشة بتظهر فوراً بقايمة فاضية والمحتوى بيوصل بعدها بإطار
     * أو اتنين. `emptyList()` كقيمة أولية مقصودة: الحالة الفاضية بتظهر
     * لجزء من الثانية بس، وأحسن من إطار ضايع.
     */
    val entries by produceState(initialValue = emptyList<File>(), currentDir, refresh, filesRevision) {
        value = withContext(Dispatchers.IO) { vm.files.list(currentDir) }
    }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val copied = vm.files.importUris(uris, currentDir)
            vm.registerFiles(copied)
            refresh++
        }
    }

    var lastPhoto by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            lastPhoto?.let { vm.registerFiles(listOf(it)) }
            refresh++
        }
    }

    // الفلتر بيتطبّق على المجلد الحالي؛ المفضّلة والأخيرة عابرة للمجلدات
    // لأن المستخدم اللي بيدوّر فيهم مش فاكر هما كانوا فين.
    // `exists()` على كل مفضّلة عملية قرص كمان — نفس السبب، نفس الحل.
    val shown: List<File> by produceState(
        initialValue = emptyList(),
        entries, filter, activeTag, metaMap, favourites, recent
    ) {
        value = withContext(Dispatchers.IO) {
            val base = when (filter) {
                FileFilter.ALL -> entries
                FileFilter.FAVOURITES -> favourites.map { File(it.path) }.filter { it.exists() }
                FileFilter.RECENT -> recent.map { File(it.path) }.filter { it.exists() }
            }
            if (activeTag == null) base
            else base.filter { activeTag in (metaMap[it.absolutePath]?.tagList ?: emptyList()) }
        }
    }

    val selectionMode = selection.isNotEmpty()

    fun toggleSelect(path: String) {
        selection = if (path in selection) selection - path else selection + path
    }

    fun open(f: File) {
        if (f.isDirectory) {
            subPath = if (subPath.isEmpty()) f.name else "$subPath/${f.name}"
        } else {
            vm.noteFileOpened(f.absolutePath)
            openFile(vm, f)
        }
    }

    Column(modifier.fillMaxSize()) {

        // ── البحث
        OutlinedTextField(
            value = query,
            onValueChange = { vm.setFileQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen, vertical = Space.sm),
            placeholder = { Text("دوّر في الأسماء والوسوم وجوّه الملفات") },
            leadingIcon = {
                androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    CwIconButton(Icons.Filled.Close, "امسح البحث", { vm.setFileQuery("") })
                }
            },
            singleLine = true,
            shape = com.corewall.qaqc.ui.design.Radius.shapeMd
        )

        if (query.trim().length >= 2) {
            SearchResults(results = results, onOpen = { path ->
                val f = File(path)
                if (f.exists()) { vm.noteFileOpened(path); openFile(vm, f) }
            })
            return@Column
        }

        // ── مسار المجلد
        if (subPath.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CwIconButton(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    "رجوع لمجلد أعلى",
                    { subPath = subPath.substringBeforeLast('/', "") }
                )
                Text(
                    subPath,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ── الفلاتر وطريقة العرض
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            FileFilter.entries.forEach { f ->
                CwChip(
                    label = f.label,
                    selected = filter == f,
                    onClick = { filter = f },
                    icon = if (f == FileFilter.FAVOURITES) Icons.Filled.Star else null
                )
            }
            Spacer(Modifier.weight(1f))
            CwIconButton(
                icon = if (grid) Icons.Filled.ViewList else Icons.Filled.GridView,
                contentDescription = if (grid) "اعرض كقايمة" else "اعرض كشبكة",
                onClick = { grid = !grid }
            )
        }

        if (tags.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen, vertical = Space.xxs),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                tags.take(6).forEach { tag ->
                    CwChip(
                        label = tag,
                        selected = activeTag == tag,
                        onClick = { activeTag = if (activeTag == tag) null else tag }
                    )
                }
            }
        }

        // ── شريط الاختيار المتعدّد
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn(Motion.standard()) + slideInVertically(Motion.enter()) { -it },
            exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.exit()) { -it }
        ) {
            SelectionBar(
                count = selection.size,
                single = selection.singleOrNull()?.let(::File),
                canMergeRevisions = selection.size >= 2 &&
                    selection.all { it.endsWith(".pdf", ignoreCase = true) },
                onClear = { selection = emptySet() },
                onFavourite = {
                    selection.forEach { vm.toggleFileFavourite(it) }
                    selection = emptySet()
                },
                onDelete = { confirmDelete = true },
                onShare = { f -> vm.files.share(f); selection = emptySet() },
                // البرومبت بيتختار الأول — التحليل من غير اختيار بيقرا كل
                // مستند بنفس التعليمات العامة، وده سبب التحليل الغلط.
                onAnalyze = { f -> analyzeTarget = f },
                onAddToProject = { f ->
                    vm.addFileToProjectKnowledge(f) { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                    selection = emptySet()
                },
                // الترتيب هنا هو ترتيب العرض في المجلد؛ الشاشة بترتّبهم
                // بالرقم اللي في آخر الاسم وبتسيب المستخدم يعدّل.
                onMergeRevisions = {
                    val ordered = shown.map { it.absolutePath }.filter { it in selection }
                    val picked = ordered + (selection - ordered.toSet())
                    selection = emptySet()
                    vm.openRevisionMerge(picked)
                }
            )
        }

        if (shown.isEmpty()) {
            CwEmptyState(
                icon = Icons.Filled.FolderOpen,
                title = when (filter) {
                    FileFilter.FAVOURITES -> "مفيش ملفات مفضّلة"
                    FileFilter.RECENT -> "مفيش ملفات مفتوحة قريّب"
                    FileFilter.ALL -> "المجلد فاضي"
                },
                detail = when (filter) {
                    FileFilter.FAVOURITES -> "دوس مطوّل على أي ملف واختار تفضيل عشان يظهر هنا."
                    FileFilter.RECENT -> "الملفات اللي تفتحها هتتجمّع هنا."
                    FileFilter.ALL -> "ملفات الدور $level بس — معزولة عن باقي الأدوار."
                },
                modifier = Modifier.weight(1f),
                action = {
                    CwButton("ضيف ملفات", { pickFiles.launch(arrayOf("*/*")) }, icon = Icons.Filled.Add)
                }
            )
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = Sizes.fileTile),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Space.screen, end = Space.screen,
                    top = Space.sm, bottom = Space.bottomInset
                ),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                items(shown, key = { it.absolutePath }) { f ->
                    FileGridTile(
                        file = f,
                        meta = metaMap[f.absolutePath],
                        selected = f.absolutePath in selection,
                        selectionMode = selectionMode,
                        onOpen = { open(f) },
                        onToggleSelect = { if (!f.isDirectory) toggleSelect(f.absolutePath) },
                        analysisLabel = pdfAnalysisLabel(
                            analysisByPath[f.absolutePath]?.status,
                            analyzingPath == f.absolutePath
                        ),
                        onAnalyze = if (isPdfFile(f)) ({ analyzeTarget = f }) else null
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Space.screen, end = Space.screen,
                    top = Space.sm, bottom = Space.bottomInset
                ),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                items(shown, key = { it.absolutePath }) { f ->
                    FileListRow(
                        file = f,
                        meta = metaMap[f.absolutePath],
                        subtitle = subtitleFor(f, vm),
                        selected = f.absolutePath in selection,
                        selectionMode = selectionMode,
                        onOpen = { open(f) },
                        onToggleSelect = { if (!f.isDirectory) toggleSelect(f.absolutePath) },
                        analysisLabel = pdfAnalysisLabel(
                            analysisByPath[f.absolutePath]?.status,
                            analyzingPath == f.absolutePath
                        ),
                        onAnalyze = if (isPdfFile(f)) ({ analyzeTarget = f }) else null
                    )
                }
            }
        }

        // ── الأفعال
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen, vertical = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            CwButton(
                "ملفات", { pickFiles.launch(arrayOf("*/*")) },
                icon = Icons.Filled.Add, modifier = Modifier.weight(1f)
            )
            CwButton(
                "صورة",
                {
                    val f = File(currentDir, "IMG_${System.currentTimeMillis()}.jpg")
                    lastPhoto = f
                    takePhoto.launch(vm.files.uriFor(f))
                },
                style = CwButtonStyle.Secondary,
                icon = Icons.Filled.PhotoCamera, modifier = Modifier.weight(1f)
            )
            CwButton(
                "مجلد", { newFolder = true },
                style = CwButtonStyle.Secondary,
                icon = Icons.Filled.CreateNewFolder, modifier = Modifier.weight(1f)
            )
        }
    }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text("مجلد جديد") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true, label = { Text("الاسم") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.files.createFolder(currentDir, name.trim())
                        refresh++; newFolder = false
                    }
                ) { Text("اعمل") }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text("إلغاء") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("تمسح ${selection.size} ملف؟") },
            text = { Text("الملفات هتتشال من الجهاز نهائي ومفيش تراجع.") },
            confirmButton = {
                TextButton(onClick = {
                    selection.forEach { p -> vm.files.delete(File(p)) }
                    selection = emptySet(); confirmDelete = false; refresh++
                    Toast.makeText(context, "اتمسحت ✓", Toast.LENGTH_SHORT).show()
                }) { Text("امسح", color = c.danger.fg) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("سيبها") } }
        )
    }

    // ── اختيار البرومبت قبل التحليل
    val target = analyzeTarget
    if (target != null) {
        AnalyzePromptSheet(
            file = target,
            prompts = prompts,
            onPick = { promptId ->
                analyzeTarget = null
                selection = emptySet()
                analyzingPath = target.absolutePath
                vm.analyzeFile(target, promptId) { msg ->
                    analyzingPath = null
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            },
            onManagePrompts = { analyzeTarget = null; vm.go(Dest.Prompts) },
            onDismiss = { analyzeTarget = null }
        )
    }
}

private fun pdfAnalysisLabel(status: String?, activelyAnalyzing: Boolean): String? = when {
    activelyAnalyzing || status == "ANALYZING" -> "جاري التحليل…"
    status == "DONE" -> "محلّل ومحفوظ في ذاكرة الدور"
    status == "PENDING" -> "جاهز للتحليل"
    status == "FAILED" -> "تعذّر التحليل — اضغط تحليل للمحاولة"
    status == "UNSUPPORTED" -> "الملف يحتاج فتحاً صالحاً"
    else -> if (status == null) "اضغط رمز التحليل" else null
}

private fun subtitleFor(f: File, vm: MainViewModel): String =
    if (f.isDirectory) "مجلد · ${vm.files.list(f).size} عنصر"
    else "${humanSize(vm.files.sizeOf(f))} · ${fileDate.format(Date(f.lastModified()))}"

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

private fun openFile(vm: MainViewModel, f: File) {
    when {
        isPdfFile(f) -> vm.openPdf(f.absolutePath)
        isImageFile(f) -> vm.openImage(f.absolutePath)
        isCadFile(f) -> vm.openCad(f.absolutePath)
        else -> vm.files.openExternally(f)
    }
}

/**
 * نتايج البحث. كل نتيجة بتقول **ليه** ظهرت — ده الفرق بين بحث بيفيد وبحث
 * بيرمي عليك قايمة وتدوّر فيها تاني.
 */
@Composable
private fun SearchResults(results: List<FileSearchHit>, onOpen: (String) -> Unit) {
    val c = LocalCwColors.current
    if (results.isEmpty()) {
        CwEmptyState(
            icon = Icons.Filled.Search,
            title = "مفيش نتايج",
            detail = "البحث بيلف على أسماء الملفات والوسوم والنصّ المستخرج من " +
                "المستندات المحلّلة. لو الملف لسه ما اتحلّلش، محتواه مش هيظهر هنا."
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.sm, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        item(key = "count") { CwSectionHeader("نتايج", count = results.size) }
        items(results, key = { it.path }) { hit ->
            CwCard(onClick = { onOpen(hit.path) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        hit.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    CwStatusBadge(
                        hit.where.label,
                        when (hit.where) {
                            FileSearchHit.Where.NAME -> CwTone.Info
                            FileSearchHit.Where.TAG -> CwTone.Pending
                            FileSearchHit.Where.CONTENT -> CwTone.Success
                        },
                        compact = true
                    )
                }
                if (hit.snippet.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        hit.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * شريط الاختيار.
 *
 * أفعال الملف الواحد (تحليل، مشاركة، ضمّ لمعرفة المشروع) بتظهر بس لما
 * يكون فيه ملف واحد مختار — لأنها بمعناها على ملف واحد، والعرض الكسول
 * بتاعها على مجموعة كان هيبقى وعد كاذب.
 */
@Composable
private fun SelectionBar(
    count: Int,
    single: File?,
    /** فيه ملفين PDF أو أكتر مختارين — الدمج بالإصدارات ممكن. */
    canMergeRevisions: Boolean,
    onClear: () -> Unit,
    onFavourite: () -> Unit,
    onDelete: () -> Unit,
    onShare: (File) -> Unit,
    onAnalyze: (File) -> Unit,
    onAddToProject: (File) -> Unit,
    onMergeRevisions: () -> Unit
) {
    val c = LocalCwColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
    ) {
        CwIconButton(Icons.Filled.Close, "الغي الاختيار", onClear)
        Text(
            "$count مختار",
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (canMergeRevisions) {
            CwIconButton(Icons.Filled.Layers, "الإصدار النهائي", onMergeRevisions)
        }
        if (single != null) {
            CwIconButton(Icons.Filled.AutoAwesome, "حلّل الملف", { onAnalyze(single) })
            CwIconButton(Icons.Filled.Hub, "ضمّه لمعرفة المشروع", { onAddToProject(single) })
            CwIconButton(Icons.Filled.Share, "شارك", { onShare(single) })
        }
        CwIconButton(Icons.Filled.Star, "ضيف للمفضّلة", onFavourite)
        CwIconButton(Icons.Filled.Delete, "امسح المختار", onDelete, tint = c.danger.fg)
    }
}
