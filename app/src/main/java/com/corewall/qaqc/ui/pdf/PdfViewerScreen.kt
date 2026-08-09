package com.corewall.qaqc.ui.pdf

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.PdfAnnotationEntity
import com.corewall.qaqc.pdfengine.OutlineEntry
import com.corewall.qaqc.pdfengine.PageLayout
import com.corewall.qaqc.pdfengine.PdfCanvas
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.pdfengine.PdfOpenException
import com.corewall.qaqc.pdfengine.PdfSearchState
import com.corewall.qaqc.pdfengine.PdfSelectionState
import com.corewall.qaqc.pdfengine.PdfSessionStore
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.pdfengine.SearchHit
import com.corewall.qaqc.pdfengine.TextQuad
import com.corewall.qaqc.pdfengine.TileEngine
import com.corewall.qaqc.pdfengine.ViewMode
import com.corewall.qaqc.pdfengine.bounds
import com.corewall.qaqc.pdfengine.pageHit
import com.corewall.qaqc.pdfengine.pagePointToScreen
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json

/**
 * عارض الـPDF — مبني على محرّك المربّعات.
 *
 * التعليقات متخزّنة **منسّبة لصفحتها** (٠..١) وسُمكها بنقط الـPDF، فبتفضل
 * مظبوطة مع أي تكبير وفي التصدير. العلامات القديمة بتتقري زي ما هي —
 * الأدوات القديمة أسماؤها اتساب في [PdfAnnotationEntity].
 */
@Composable
fun PdfViewerScreen(vm: MainViewModel, path: String, onClose: () -> Unit) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val file = remember(path) { File(path) }

    // ── فتح المستند
    var session by remember(path) { mutableStateOf<PdfDocumentSession?>(null) }
    var openError by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        val opened = withContext(Dispatchers.IO) {
            runCatching { PdfDocumentSession.open(context, file) }
        }
        opened.onSuccess { session = it }
        opened.onFailure { e ->
            openError = (e as? PdfOpenException)?.userMessage ?: "مقدرناش نفتح الملف ده"
        }
    }

    DisposableEffect(path) { onDispose { session?.close() } }

    val active = session
    if (openError != null || active == null) {
        LoadingOrError(openError, onClose)
        return
    }

    // ── المحرّك والحالة
    val engine = remember(active) { TileEngine(active, TileEngine.budgetFor(context)) }
    val thumbs = remember(active) { ThumbnailCache(active) }
    DisposableEffect(active) {
        onDispose { engine.clear(); thumbs.clear() }
    }

    val state = remember(active) { PdfViewerState(active.pageCount) }
    val measured by active.measuredCount.collectAsStateWithLifecycle()

    LaunchedEffect(measured, state.mode) {
        state.updateLayout(PageLayout.build(active.allSizes(), state.mode))
    }

    LaunchedEffect(state.currentPage) {
        val from = (state.currentPage - 4).coerceAtLeast(0)
        val to = (state.currentPage + 8).coerceAtMost(active.pageCount - 1)
        for (p in from..to) active.measure(p)
    }

    // ── طبقة النص: البحث والتحديد والفهرس
    val search = remember(active) { PdfSearchState(active) }
    val selection = remember(active) { PdfSelectionState(active) }
    DisposableEffect(active) {
        onDispose { search.dispose(); selection.dispose() }
    }

    var outline by remember(active) { mutableStateOf<List<OutlineEntry>>(emptyList()) }
    LaunchedEffect(active) { outline = active.outline() }

    /**
     * النتائج مجمّعة بالصفحة.
     *
     * الرسم بيحصل ٦٠ مرة في الثانية؛ لو فلترنا ألفين نتيجة في كل إطار عشان
     * نلاقي بتوع الصفحتين الظاهرين، البحث نفسه بيبقى سبب التهتهة. التجميع
     * بيتعمل مرة كل ما النتايج تتغيّر.
     */
    val hitsByPage = remember(search.hits.size, search.query) {
        search.hits.groupBy { it.page }
    }

    var searchOpen by remember(path) { mutableStateOf(false) }
    var navOpen by remember(path) { mutableStateOf(false) }

    val bookmarks by vm.pdfBookmarks.collectAsStateWithLifecycle()
    val fileBookmarks = remember(bookmarks, path) { bookmarks.filter { it.filePath == path } }

    /**
     * استرجاع آخر موقع — بيشتغل مرة واحدة عند فتح الملف.
     *
     * `snapshotFlow { … }.first { it }` مش تعقيد زيادة: الاسترجاع محتاج
     * الرصّ **والمشهد** يبقوا جاهزين، والاتنين بيوصلوا في أوقات مختلفة.
     * لو ربطنا التأثير بيهم كمفاتيح، أي قياس صفحة جديد كان هيلغي التأثير
     * وهو نصّه — والمستخدم كان هيفتح الملف من أوله بشكل عشوائي.
     */
    LaunchedEffect(active) {
        val spot = PdfSessionStore.load(context, path) ?: return@LaunchedEffect
        // بنقيس الصفحة وجيرانها الأول عشان الرصّ يبقى بمقاسات حقيقية،
        // وإلا الاسترجاع بيقع على تقدير ويطلع مزحلق شوية.
        for (p in (spot.page - 1).coerceAtLeast(0)..
            (spot.page + 1).coerceAtMost(active.pageCount - 1)) {
            active.measure(p)
        }
        snapshotFlow { state.layout.slots.isNotEmpty() && state.viewport.width > 0 }
            .first { it }
        state.restore(spot.page, spot.zoom)
    }

    DisposableEffect(path, active) {
        onDispose { PdfSessionStore.save(context, path, state.currentPage, state.zoom) }
    }

    /** بيودّي المستخدم لنتيجة: بيقيس صفحتها الأول عشان المكان يطلع مظبوط. */
    fun jumpTo(hit: SearchHit) = scope.launch {
        active.measure(hit.page)
        val rect = hit.bounds() ?: run { state.goToPage(hit.page); return@launch }
        val doc = state.pageRectToDoc(hit.page, rect.left, rect.top, rect.right, rect.bottom)
        if (doc == null) state.goToPage(hit.page) else state.revealRect(doc)
    }

    // ── التعليقات والأدوات
    val allAnnotations by vm.pdfAnnotations.collectAsStateWithLifecycle()
    val fileAnnotations = remember(allAnnotations, path) {
        allAnnotations.filter { it.filePath == path }
    }

    var tool by remember { mutableStateOf(PdfTool.PAN) }
    var style by remember { mutableStateOf(ToolStyle()) }
    var draft by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var draftPage by remember { mutableIntStateOf(-1) }
    var chromeVisible by remember { mutableStateOf(true) }
    var railVisible by remember { mutableStateOf(false) }

    /**
     * مكدّس الإعادة — بيشيل العلامات اللي اتراجعنا عنها.
     *
     * التراجع بيمسح من القاعدة، فالإعادة محتاجة نسخة من الكيان نفسه عشان
     * تقدر ترجّعه. بيتفضّى مع أول علامة جديدة: التاريخ بيتفرّع، واللي كان
     * "قدّام" بقى فرع مهجور.
     */
    val redoStack = remember(path) { mutableStateListOf<PdfAnnotationEntity>() }

    fun commitDraft() {
        val toolId = tool.id
        val page = draftPage
        val slot = state.layout.slotAt(page)
        if (toolId == null || page < 0 || draft.size < 2 || slot == null) {
            draft = emptyList(); draftPage = -1; return
        }
        val flat = ArrayList<Float>(draft.size * 2)
        draft.forEach { p ->
            val doc = state.screenToDoc(p)
            flat += ((doc.x - slot.left) / slot.size.width).coerceIn(0f, 1f)
            flat += ((doc.y - slot.top) / slot.size.height).coerceIn(0f, 1f)
        }
        vm.addPdfAnnotation(
            PdfAnnotationEntity(
                filePath = path, page = page, tool = toolId,
                color = style.colorArgb, pointsJson = json.encodeToString(flat),
                createdAt = System.currentTimeMillis(),
                strokeWidth = style.widthPt, opacity = style.opacity
            )
        )
        redoStack.clear()
        draft = emptyList()
        draftPage = -1
    }

    val pageAnnotations = fileAnnotations.filter { it.page == state.currentPage }

    fun undo() {
        val last = pageAnnotations.maxByOrNull { it.id } ?: return
        redoStack.add(last)
        vm.deletePdfAnnotation(last.id)
    }

    fun redo() {
        val entity = redoStack.removeLastOrNull() ?: return
        vm.addPdfAnnotation(entity.copy(id = 0))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                exportAnnotatedPdf(context, active, uri) { page ->
                    fileAnnotations.filter { it.page == page }
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "تم تصدير النسخة المعلّقة ✓"
                else "فشل التصدير: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── الشاشة
    Surface(Modifier.fillMaxSize(), color = c.surfaceAlt) {
        Box(Modifier.fillMaxSize()) {

            PdfCanvas(
                state = state,
                engine = engine,
                session = active,
                drawingActive = tool.isDrawing,
                onDrawStart = { p ->
                    state.pageHit(p)?.let { draftPage = it.page; draft = listOf(p) }
                },
                onDrawMove = { p ->
                    if (draftPage >= 0) {
                        draft = if (tool.freeform) draft + p
                        else listOf(draft.firstOrNull() ?: p, p)
                    }
                },
                onDrawEnd = { commitDraft() },
                onTap = {
                    // أي نقرة بتلغي التحديد الأول. النقرة اللي بتخفي
                    // الواجهة وسايبة تحديد معلّق بتبان كأنها باج.
                    if (selection.isActive) selection.clear() else chromeVisible = !chromeVisible
                },
                onLongPress = { point ->
                    val hit = state.pageHit(point)
                    val slot = hit?.let { state.layout.slotAt(it.page) }
                    if (hit != null && slot != null) {
                        selection.selectWordAt(
                            page = hit.page,
                            xPt = hit.nx * slot.size.width,
                            yPtFromTop = hit.ny * slot.size.height
                        ) { found ->
                            if (!found) {
                                Toast.makeText(context, "مفيش نص في المكان ده", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                },
                overlay = { s ->
                    // ترتيب الطبقات: البحث تحت، التعليقات فوقه، التحديد فوق
                    // الكل — التحديد حاجة لحظية والمستخدم لازم يشوف حدودها.
                    if (searchOpen) {
                        drawSearchLayer(s, hitsByPage, search.activeHit, c.warning.solid, c.accent)
                    }
                    drawAnnotations(s, fileAnnotations)
                    if (draft.size >= 2) {
                        drawAnnotation(
                            tool, Color(style.colorArgb), draft,
                            style.widthPt, style.opacity, s.zoom
                        )
                    }
                    if (selection.isActive) drawSelection(s, selection.quads, c.accent)
                }
            )

            SelectionHandles(
                state = state,
                selection = selection,
                onCopy = {
                    clipboard.setText(AnnotatedString(selection.text))
                    Toast.makeText(context, "اتنسخ ✓", Toast.LENGTH_SHORT).show()
                    selection.clear()
                },
                onSearch = { text ->
                    searchOpen = true
                    search.setQuery(text.trim().take(MAX_SEARCH_FROM_SELECTION), state.currentPage)
                    selection.clear()
                },
                onDismiss = { selection.clear() }
            )

            // ── الشريط العلوي
            AnimatedVisibility(
                visible = chromeVisible && !searchOpen,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopChrome(
                    name = file.name,
                    page = state.currentPage + 1,
                    pageCount = active.pageCount,
                    mode = state.mode,
                    railOpen = railVisible,
                    onClose = onClose,
                    onSearch = { searchOpen = true },
                    onNavigate = { navOpen = true },
                    onToggleRail = { railVisible = !railVisible },
                    onToggleMode = {
                        state.setMode(
                            if (state.mode == ViewMode.CONTINUOUS_VERTICAL) ViewMode.CONTINUOUS_HORIZONTAL
                            else ViewMode.CONTINUOUS_VERTICAL,
                            active.allSizes()
                        )
                    },
                    onExport = { exportLauncher.launch("${file.nameWithoutExtension}-معلّق.pdf") }
                )
            }

            AnimatedVisibility(
                visible = searchOpen,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                PdfSearchBar(
                    search = search,
                    pageCount = active.pageCount,
                    currentPage = state.currentPage,
                    onJump = { jumpTo(it) },
                    onClose = { searchOpen = false; search.clear() }
                )
            }

            // ── الخريطة المصغّرة: عند التكبير العالي بس
            AnimatedVisibility(
                visible = chromeVisible && state.zoom > MINIMAP_FROM_ZOOM,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = MINIMAP_TOP_PAD, end = Space.md)
            ) {
                MiniMap(state, active, thumbs)
            }

            // ── الأسفل: مؤشّر التكبير + الأدوات + شريط المصغّرات
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = chromeVisible && !railVisible,
                    enter = fadeIn(), exit = fadeOut()
                ) {
                    ZoomPill(
                        percent = state.zoomPercent(),
                        onFitWidth = { state.fitWidth() },
                        onFitPage = { state.fitPage() },
                        onActual = { state.actualSize() },
                        modifier = Modifier.padding(bottom = Space.sm)
                    )
                }

                AnimatedVisibility(
                    visible = chromeVisible && railVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    ThumbnailRail(
                        session = active,
                        cache = thumbs,
                        currentPage = state.currentPage,
                        onPick = { state.goToPage(it); railVisible = false },
                        modifier = Modifier.padding(bottom = Space.sm)
                    )
                }

                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    PdfToolbar(
                        tool = tool,
                        style = style,
                        onTool = { picked ->
                            tool = if (tool == picked) PdfTool.PAN else picked
                            // كل أداة بتفتح على إعداداتها الطبيعية: الماركر
                            // تخين وشفاف، والقلم رفيع وصامد. من غير كده
                            // المستخدم بيختار ماركر وبيلاقيه بيرسم زي القلم.
                            if (picked.isDrawing && tool == picked) {
                                style = style.copy(
                                    widthPt = picked.defaultWidthPt,
                                    opacity = picked.defaultOpacity
                                )
                            }
                        },
                        onStyle = { style = it },
                        canUndo = pageAnnotations.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        onUndo = { undo() },
                        onRedo = { redo() },
                        onClear = { vm.clearPdfPage(path, state.currentPage) },
                        modifier = Modifier.padding(bottom = Space.md)
                    )
                }
            }

            if (navOpen) {
                PdfOutlineSheet(
                    outline = outline,
                    bookmarks = fileBookmarks,
                    currentPage = state.currentPage,
                    onGoTo = { page -> state.goToPage(page); navOpen = false },
                    onAddBookmark = {
                        vm.addPdfBookmark(path, state.currentPage, "صفحة ${state.currentPage + 1}")
                    },
                    onDeleteBookmark = { vm.deletePdfBookmark(it) },
                    onDismiss = { navOpen = false }
                )
            }
        }
    }
}

/**
 * أكبر طول نأخده من نص محدَّد كاستعلام بحث.
 *
 * فقرة كاملة كاستعلام مش هتلاقي نفسها حتى — PDFium بيدوّر على تطابق حرفي،
 * وأي فرق في مسافة أو سطر بيلغي النتيجة. أول كام حرف هي اللي بتنفع.
 */
private const val MAX_SEARCH_FROM_SELECTION = 40

/** الخريطة بتظهر لما تبقى شايف جزء صغير من الصفحة فعلاً. */
private const val MINIMAP_FROM_ZOOM = 2.5f

/** تحت الشريط العلوي — عشان ماتغطّيهوش. */
private val MINIMAP_TOP_PAD = Space.huge + Space.xl

// ══════════════════════════════════════════════════════ الرسم فوق الصفحات

/** بيرسم تظليل نتائج البحث للصفحات الظاهرة بس. */
private fun DrawScope.drawSearchLayer(
    state: PdfViewerState,
    hitsByPage: Map<Int, List<SearchHit>>,
    activeHit: SearchHit?,
    base: Color,
    active: Color
) {
    if (hitsByPage.isEmpty()) return
    val rect = state.visibleDocRect()
    val visible = state.layout
        .visible(rect.left, rect.top, rect.right, rect.bottom)
        .map { it.index }

    val quads = ArrayList<TextQuad>()
    visible.forEach { page -> hitsByPage[page]?.forEach { quads += it.quads } }
    if (quads.isEmpty() && activeHit == null) return

    drawSearchHighlights(
        state = state,
        quads = quads,
        activeQuads = activeHit?.quads.orEmpty(),
        base = base,
        active = active
    )
}

/** بيرسم تعليقات كل صفحة مرئية في مكانها الصح. */
private fun DrawScope.drawAnnotations(
    state: PdfViewerState,
    annotations: List<PdfAnnotationEntity>
) {
    if (annotations.isEmpty()) return
    val rect = state.visibleDocRect()
    val visiblePages = state.layout
        .visible(rect.left, rect.top, rect.right, rect.bottom)
        .map { it.index }
        .toSet()

    for (a in annotations) {
        if (a.page !in visiblePages) continue
        val flat = runCatching { json.decodeFromString<List<Float>>(a.pointsJson) }.getOrNull() ?: continue
        val points = (flat.indices step 2).mapNotNull { i ->
            if (i + 1 >= flat.size) null
            else state.pagePointToScreen(a.page, flat[i], flat[i + 1])
        }
        drawAnnotation(
            PdfTool.fromId(a.tool), Color(a.color), points,
            a.strokeWidth, a.opacity, state.zoom
        )
    }
}

// ══════════════════════════════════════════════════════ الشرائط

@Composable
private fun LoadingOrError(error: String?, onClose: () -> Unit) {
    val c = LocalCwColors.current
    Surface(Modifier.fillMaxSize(), color = c.surface) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (error == null) {
                CircularProgressIndicator(color = c.accent)
                Spacer(Modifier.height(Space.md))
                Text("بيفتح الملف…", style = MaterialTheme.typography.bodyMedium, color = c.textTertiary)
            } else {
                Text(error, style = MaterialTheme.typography.titleSmall, color = c.danger.fg)
                Spacer(Modifier.height(Space.md))
                CwIconButton(Icons.Filled.Close, "إغلاق", onClose)
            }
        }
    }
}

@Composable
private fun TopChrome(
    name: String,
    page: Int,
    pageCount: Int,
    mode: ViewMode,
    railOpen: Boolean,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onNavigate: () -> Unit,
    onToggleRail: () -> Unit,
    onToggleMode: () -> Unit,
    onExport: () -> Unit
) {
    val c = LocalCwColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), color = c.surface.copy(alpha = 0.94f)) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CwIconButton(Icons.Filled.Close, "إغلاق", onClose)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Space.sm)
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "صفحة $page من $pageCount",
                    style = CwText.codeSmall,
                    color = c.textTertiary,
                    maxLines = 1
                )
            }
            CwIconButton(Icons.Filled.Search, "دوّر في الملف", onSearch)
            CwIconButton(Icons.Filled.Bookmarks, "الفهرس والعلامات", onNavigate)
            CwIconButton(
                Icons.Filled.GridView, "الصفحات", onToggleRail,
                active = railOpen
            )

            // الباقي في قائمة: ستة زراير في شريط عرضه ٣٦٠dp معناها إن اسم
            // الملف مابقاش ليه مكان — واسم الملف هو أهم حاجة في الشريط.
            Box {
                CwIconButton(Icons.Filled.MoreVert, "خيارات أكتر", { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (mode == ViewMode.CONTINUOUS_HORIZONTAL) "تمرير رأسي"
                                else "تمرير أفقي"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (mode == ViewMode.CONTINUOUS_HORIZONTAL) Icons.Filled.SwapVert
                                else Icons.Filled.SwapHoriz,
                                contentDescription = null
                            )
                        },
                        onClick = { menuOpen = false; onToggleMode() }
                    )
                    DropdownMenuItem(
                        text = { Text("صدّر نسخة معلّقة") },
                        leadingIcon = {
                            Icon(Icons.Filled.IosShare, contentDescription = null)
                        },
                        onClick = { menuOpen = false; onExport() }
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════ التصدير

/**
 * بيصدّر نسخة كل صفحة فيها مرسومة بتعليقاتها.
 *
 * ملاحظة صريحة: التصدير ده **بيحوّل الصفحات لصور**، فالنسخة المصدَّرة مش
 * قابلة للبحث وحجمها أكبر. التصدير المتّجهي (اللي بيكتب التعليقات
 * كـ`/Annots` حقيقية تفتح في Acrobat) جاي في مرحلة عمليات المستندات.
 */
private suspend fun exportAnnotatedPdf(
    context: android.content.Context,
    session: PdfDocumentSession,
    uri: android.net.Uri,
    annotationsFor: (page: Int) -> List<PdfAnnotationEntity>
) = withContext(Dispatchers.IO) {
    val doc = android.graphics.pdf.PdfDocument()
    try {
        for (i in 0 until session.pageCount) {
            session.measure(i)
            val size = session.sizeOrEstimate(i)
            val width = EXPORT_WIDTH_PX
            val height = (width * size.height / size.width).toInt().coerceAtLeast(1)
            val pxPerPoint = width / size.width

            val bitmap = session.renderTile(
                page = i,
                gridWidth = width, gridHeight = height,
                originX = 0, originY = 0,
                tileWidth = width, tileHeight = height
            ) ?: continue

            val canvas = android.graphics.Canvas(bitmap)
            annotationsFor(i).forEach { a ->
                val flat = runCatching { json.decodeFromString<List<Float>>(a.pointsJson) }.getOrNull()
                if (flat != null) {
                    drawAnnotationAndroid(
                        canvas, a, flat,
                        bitmap.width.toFloat(), bitmap.height.toFloat(), pxPerPoint
                    )
                }
            }

            val pw = size.width.toInt().coerceAtLeast(1)
            val ph = size.height.toInt().coerceAtLeast(1)
            val pdfPage = doc.startPage(
                android.graphics.pdf.PdfDocument.PageInfo.Builder(pw, ph, i + 1).create()
            )
            pdfPage.canvas.drawBitmap(
                bitmap, null,
                android.graphics.RectF(0f, 0f, pw.toFloat(), ph.toFloat()),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            )
            doc.finishPage(pdfPage)
            bitmap.recycle()
        }
        context.contentResolver.openOutputStream(uri)?.use { os -> doc.writeTo(os) }
            ?: error("مقدرناش نفتح الملف للكتابة")
    } finally {
        doc.close()
    }
}

private const val EXPORT_WIDTH_PX = 2000
