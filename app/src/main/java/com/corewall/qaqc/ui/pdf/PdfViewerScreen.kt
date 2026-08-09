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
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.runtime.mutableStateMapOf
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
import com.corewall.qaqc.data.db.PdfMeasurementEntity
import com.corewall.qaqc.data.db.PdfScaleEntity
import com.corewall.qaqc.pdfengine.OutlineEntry
import com.corewall.qaqc.pdfengine.PageLayout
import com.corewall.qaqc.pdfengine.PdfCanvas
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.ocr.OcrEngine
import com.corewall.qaqc.ocr.OcrPacks
import com.corewall.qaqc.pdfengine.MeasureKind
import com.corewall.qaqc.pdfengine.MeasureUnit
import com.corewall.qaqc.pdfengine.PdfImageExport
import com.corewall.qaqc.pdfengine.PdfOpenException
import com.corewall.qaqc.pdfengine.PdfOps
import com.corewall.qaqc.pdfengine.PdfSearchState
import com.corewall.qaqc.pdfengine.PdfSelectionState
import com.corewall.qaqc.pdfengine.PdfSessionStore
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.pdfengine.Scale
import com.corewall.qaqc.pdfengine.SearchHit
import com.corewall.qaqc.pdfengine.TextQuad
import com.corewall.qaqc.pdfengine.TileEngine
import com.corewall.qaqc.pdfengine.ViewMode
import com.corewall.qaqc.pdfengine.bounds
import com.corewall.qaqc.pdfengine.polylineLength
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
    // ── القياس
    val measure = remember(path) { MeasureSession() }
    var calibrationOpen by remember(path) { mutableStateOf(false) }

    val allMeasurements by vm.pdfMeasurements.collectAsStateWithLifecycle()
    val fileMeasurements = remember(allMeasurements, path) {
        allMeasurements.filter { it.filePath == path }
    }
    val allScales by vm.pdfScales.collectAsStateWithLifecycle()

    /**
     * معايرة الصفحة: الأخصّ الأول.
     *
     * صفحة معايَرة لوحدها بتكسب على معايرة المستند، عشان ملف تسليم فيه
     * مساقط ١:١٠٠ وتفاصيل ١:٢٠ يفضل قابل للقياس صفحة صفحة.
     */
    fun scaleFor(page: Int): Scale? {
        val row = allScales.firstOrNull { it.filePath == path && it.page == page }
            ?: allScales.firstOrNull {
                it.filePath == path && it.page == PdfScaleEntity.WHOLE_DOCUMENT
            }
            ?: return null
        return Scale(row.unitsPerPoint, MeasureUnit.fromId(row.unit), row.note)
    }

    val pageScale = scaleFor(state.currentPage)

    // ── الـOCR
    var ocrOpen by remember(path) { mutableStateOf(false) }
    var ocrRunning by remember { mutableStateOf(false) }
    var ocrResult by remember(path) { mutableStateOf<OcrEngine.Outcome?>(null) }
    var ocrImageSize by remember(path) { mutableStateOf(0 to 0) }
    val ocrLanguages = remember { mutableStateListOf<OcrPacks.Language>() }
    val packStates = remember { mutableStateMapOf<OcrPacks.Language, PackState>() }

    fun refreshPacks() {
        OcrPacks.Language.entries.forEach { language ->
            val current = packStates[language]
            if (current?.downloading != true) {
                packStates[language] = PackState(OcrPacks.isInstalled(context, language))
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshPacks()
        // العربي مختار مبدئياً — التطبيق عربي والمستندات الممسوحة
        // غالباً عربي أو مختلط.
        if (ocrLanguages.isEmpty()) ocrLanguages += OcrPacks.Language.ARABIC
    }

    var imagesOpen by remember(path) { mutableStateOf(false) }
    var watermarkOpen by remember(path) { mutableStateOf(false) }
    var mergeOpen by remember(path) { mutableStateOf(false) }
    var opRunning by remember(path) { mutableStateOf(false) }
    var opProgress by remember(path) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(Unit) { PdfOps.ensureInit(context) }

    /** ملفات PDF التانية في نفس المجلد — مرشّحات الدمج. */
    val mergeCandidates = remember(path, mergeOpen) {
        if (!mergeOpen) emptyList()
        else file.parentFile?.listFiles()
            ?.filter { it.isFile && it.extension.equals("pdf", true) && it.absolutePath != path }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

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
            opRunning = true
            val result = exportAnnotatedPdf(context, file, uri, fileAnnotations)
            opRunning = false
            Toast.makeText(
                context,
                result.fold(
                    onSuccess = { "اتصدّرت نسخة فيها $it تعليق ✓" },
                    onFailure = { "فشل التصدير: ${it.message}" }
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * بيرسم الصفحة عند دقّة الـOCR وبيقراها.
     *
     * ٣٠٠ نقطة/بوصة مش رقم عشوائي: Tesseract متدرّب على مسح بالدقّة دي.
     * أقل من كده الحروف الصغيرة بتضيع، وأكتر بيبطّئ من غير مكسب.
     */
    fun runOcr() {
        if (ocrRunning) return
        ocrRunning = true
        ocrResult = null
        scope.launch {
            val page = state.currentPage
            active.measure(page)
            val size = active.sizeOrEstimate(page)
            var scaleFactor = OCR_DPI / 72f
            var width = (size.width * scaleFactor).toInt().coerceAtLeast(1)
            var height = (size.height * scaleFactor).toInt().coerceAtLeast(1)
            while (width.toLong() * height > OCR_MAX_PIXELS && scaleFactor > 0.5f) {
                scaleFactor /= 2f
                width = (size.width * scaleFactor).toInt().coerceAtLeast(1)
                height = (size.height * scaleFactor).toInt().coerceAtLeast(1)
            }

            val bitmap = runCatching {
                active.renderTile(page, width, height, 0, 0, width, height)
            }.getOrNull()

            if (bitmap == null) {
                ocrRunning = false
                Toast.makeText(context, "مقدرناش نجهّز الصفحة للقراية", Toast.LENGTH_LONG).show()
                return@launch
            }

            ocrImageSize = bitmap.width to bitmap.height
            val outcome = OcrEngine.recognise(context, bitmap, ocrLanguages.toList())
            bitmap.recycle()
            ocrRunning = false
            outcome
                .onSuccess { ocrResult = it }
                .onFailure {
                    Toast.makeText(context, "فشل التعرّف: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    /** طول مسودة المعايرة بالنقط — بيغذّي ورقة المعايرة. */
    fun draftLengthPt(): Double? {
        val page = measure.draftPage
        if (page < 0 || measure.pointCount < 2) return null
        val slot = state.layout.slotAt(page) ?: return null
        return polylineLength(
            toPagePoints(measure.points(), slot.size.width, slot.size.height)
        )
    }

    fun commitMeasurement() {
        if (!measure.isComplete() || measure.draftPage < 0) return
        vm.addPdfMeasurement(
            PdfMeasurementEntity(
                filePath = path,
                page = measure.draftPage,
                kind = measure.kind.id,
                pointsJson = json.encodeToString(measure.points()),
                colorArgb = MEASURE_COLOR,
                createdAt = System.currentTimeMillis()
            )
        )
        measure.reset()
    }

    /**
     * بيشغّل عملية مستند ويعرض نتيجتها.
     *
     * كل العمليات بتمرّ من هنا عشان يبقى فيه مكان واحد بيقفل الأزرار وقت
     * الشغل وبيحوّل الفشل لرسالة. عملية على ملف ٣٠٠ صفحة بتاخد وقت،
     * وواجهة ساكتة وقتها بتخلّي المستخدم يضغط تاني ويشغّلها مرتين.
     */
    fun runDocOp(label: String, openAfter: Boolean, block: suspend () -> Result<File>) {
        if (opRunning) return
        opRunning = true
        scope.launch {
            val result = block()
            opRunning = false
            opProgress = null
            result
                .onSuccess { out ->
                    Toast.makeText(context, "$label ✓ — ${out.name}", Toast.LENGTH_LONG).show()
                    if (openAfter) vm.openPdf(out.absolutePath)
                }
                .onFailure { e ->
                    Toast.makeText(context, "فشل $label: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    // ── الشاشة
    Surface(Modifier.fillMaxSize(), color = c.surfaceAlt) {
        Box(Modifier.fillMaxSize()) {

            PdfCanvas(
                state = state,
                engine = engine,
                session = active,
                drawingActive = tool.isDrawing && !measure.enabled,
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
                onTap = { point ->
                    when {
                        // في وضع القياس النقرة بتحطّ نقطة. إخفاء الواجهة
                        // بيبقى على زرار الخروج بس — نقرة غامضة وسط قياس
                        // معناها رقم غلط ومحدش هيلاحظ.
                        measure.enabled -> {
                            val hit = state.pageHit(point)
                            if (hit != null) measure.addPoint(hit.page, hit.nx, hit.ny)
                        }
                        // أي نقرة بتلغي التحديد الأول. النقرة اللي بتخفي
                        // الواجهة وسايبة تحديد معلّق بتبان كأنها باج.
                        selection.isActive -> selection.clear()
                        else -> chromeVisible = !chromeVisible
                    }
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

                    // القياس فوق الكل: هو النتيجة اللي المستخدم بيقرأها،
                    // ولو تعليق غطّاه بيبقى الرقم موجود ومش مقروء.
                    drawMeasurements(
                        state = s,
                        items = fileMeasurements,
                        pointsOf = { m ->
                            runCatching { json.decodeFromString<List<Float>>(m.pointsJson) }
                                .getOrDefault(emptyList())
                        },
                        scaleOf = { page -> scaleFor(page) }
                    )
                    if (measure.enabled) {
                        drawMeasureDraft(s, measure, pageScale, Color(MEASURE_COLOR))
                    }
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
                    measuring = measure.enabled,
                    onToggleMeasure = {
                        measure.enabled = !measure.enabled
                        measure.reset()
                        measure.calibrating = false
                        if (measure.enabled) tool = PdfTool.PAN
                    },
                    onOrganize = { vm.openPdfOrganizer(path) },
                    onOcr = { refreshPacks(); ocrOpen = true },
                    onImages = { imagesOpen = true },
                    onWatermark = { watermarkOpen = true },
                    onMerge = { mergeOpen = true },
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
                    visible = chromeVisible && measure.enabled,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    PdfMeasureToolbar(
                        session = measure,
                        scale = pageScale,
                        canUndoPoint = measure.pointCount > 0,
                        canFinish = measure.isComplete(),
                        hasSaved = fileMeasurements.any { it.page == state.currentPage },
                        onKind = { kind ->
                            measure.calibrating = false
                            measure.kind = kind
                            measure.reset()
                        },
                        onUndoPoint = { measure.undoPoint() },
                        onFinish = {
                            if (measure.calibrating) calibrationOpen = true
                            else commitMeasurement()
                        },
                        onCancel = { measure.reset() },
                        onCalibrate = { calibrationOpen = true },
                        onClearPage = { vm.clearPdfMeasurements(path, state.currentPage) },
                        onExit = {
                            measure.enabled = false
                            measure.calibrating = false
                            measure.reset()
                        },
                        modifier = Modifier.padding(bottom = Space.md)
                    )
                }

                AnimatedVisibility(
                    visible = chromeVisible && !measure.enabled,
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

            if (imagesOpen) {
                ImageExportSheet(
                    currentPage = state.currentPage,
                    pageCount = active.pageCount,
                    running = opRunning,
                    progress = opProgress,
                    onExport = { pageScope, dpi, format, quality ->
                        val pages =
                            if (pageScope == PageScope.CURRENT) listOf(state.currentPage)
                            else (0 until active.pageCount).toList()
                        val dir = File(file.parentFile, "${file.nameWithoutExtension} — صور")
                        opProgress = 0 to pages.size
                        opRunning = true
                        scope.launch {
                            val result = PdfImageExport.export(
                                session = active,
                                pages = pages,
                                dpi = dpi,
                                format = format,
                                quality = quality,
                                dir = dir,
                                baseName = file.nameWithoutExtension
                            ) { done, total -> opProgress = done to total }
                            opRunning = false
                            opProgress = null
                            imagesOpen = false
                            result
                                .onSuccess { outcome ->
                                    val note = if (outcome.downscaled.isEmpty()) ""
                                    else " (اتخفّضت لـ${outcome.downscaled.values.max()} نقطة/بوصة عشان الذاكرة)"
                                    Toast.makeText(
                                        context,
                                        "اتصدّرت ${outcome.files.size} صورة في ${dir.name}$note",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .onFailure { e ->
                                    Toast.makeText(
                                        context, "فشل التصدير: ${e.message}", Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                    },
                    onDismiss = { if (!opRunning) imagesOpen = false }
                )
            }

            if (watermarkOpen) {
                WatermarkSheet(
                    currentPage = state.currentPage,
                    pageCount = active.pageCount,
                    running = opRunning,
                    onApply = { spec, pageScope, overwriteOriginal ->
                        val pages =
                            if (pageScope == PageScope.CURRENT) listOf(state.currentPage)
                            else emptyList()
                        val full = spec.copy(pages = pages)
                        watermarkOpen = false
                        if (overwriteOriginal) {
                            runDocOp("العلامة المائية", openAfter = false) {
                                overwrite(file) { temp -> PdfOps.watermark(file, temp, full) }
                            }
                        } else {
                            runDocOp("العلامة المائية", openAfter = true) {
                                val dest = uniqueSibling(file, "بعلامة")
                                PdfOps.watermark(file, dest, full).map { dest }
                            }
                        }
                    },
                    onDismiss = { if (!opRunning) watermarkOpen = false }
                )
            }

            if (mergeOpen) {
                MergeSheet(
                    candidates = mergeCandidates,
                    running = opRunning,
                    onMerge = { picked ->
                        mergeOpen = false
                        runDocOp("الدمج", openAfter = true) {
                            val dest = uniqueSibling(file, "مدموج")
                            PdfOps.merge(listOf(file) + picked, dest).map { dest }
                        }
                    },
                    onDismiss = { if (!opRunning) mergeOpen = false }
                )
            }

            if (ocrOpen) {
                OcrSheet(
                    currentPage = state.currentPage,
                    packs = packStates,
                    selected = ocrLanguages.toSet(),
                    running = ocrRunning,
                    result = ocrResult,
                    onToggleLanguage = { language ->
                        if (language in ocrLanguages) ocrLanguages.remove(language)
                        else ocrLanguages.add(language)
                    },
                    onDownload = { language ->
                        packStates[language] = PackState(installed = false, downloading = true)
                        scope.launch {
                            val outcome = OcrPacks.download(context, language) { done, total ->
                                packStates[language] = PackState(
                                    installed = false,
                                    downloading = true,
                                    progress = if (total > 0) done.toFloat() / total else 0f
                                )
                            }
                            packStates[language] = PackState(OcrPacks.isInstalled(context, language))
                            outcome.onFailure {
                                Toast.makeText(
                                    context, "فشل تحميل الحزمة: ${it.message}", Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onDelete = { language ->
                        OcrPacks.delete(context, language)
                        refreshPacks()
                    },
                    onRun = { runOcr() },
                    onCopy = {
                        clipboard.setText(AnnotatedString(ocrResult?.text.orEmpty()))
                        Toast.makeText(context, "اتنسخ ✓", Toast.LENGTH_SHORT).show()
                    },
                    onMakeSearchable = {
                        val outcome = ocrResult
                        val (imgW, imgH) = ocrImageSize
                        if (outcome != null && imgW > 0 && imgH > 0) {
                            val page = state.currentPage
                            ocrOpen = false
                            runDocOp("طبقة النص", openAfter = true) {
                                val dest = uniqueSibling(file, "قابل للبحث")
                                PdfOps.writeTextLayer(
                                    src = file,
                                    dest = dest,
                                    fontStream = {
                                        context.resources.openRawResource(
                                            com.corewall.qaqc.R.font.ibm_plex_sans_arabic_regular
                                        )
                                    },
                                    pages = mapOf(
                                        page to PdfOps.OcrPage(
                                            words = outcome.words.map { w ->
                                                PdfOps.OcrWord(
                                                    w.text, w.box.left, w.box.top,
                                                    w.box.right, w.box.bottom
                                                )
                                            },
                                            imageWidth = imgW.toFloat(),
                                            imageHeight = imgH.toFloat()
                                        )
                                    )
                                ).map { dest }
                            }
                        }
                    },
                    onDismiss = { if (!ocrRunning) ocrOpen = false }
                )
            }

            if (calibrationOpen) {
                MeasureCalibrationSheet(
                    current = pageScale,
                    referenceLengthPt = draftLengthPt(),
                    onRatio = { ratio ->
                        val built = Scale.fromRatio(ratio)
                        vm.setPdfScale(
                            path, state.currentPage,
                            built.unitsPerPoint, built.unit.id, built.note
                        )
                        calibrationOpen = false
                        measure.calibrating = false
                        measure.reset()
                    },
                    onReference = { realLength, unit ->
                        val length = draftLengthPt()
                        val built = length?.let { Scale.fromReference(it, realLength, unit) }
                        if (built == null) {
                            Toast.makeText(
                                context, "خط المعايرة قصير أوي", Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            vm.setPdfScale(
                                path, state.currentPage,
                                built.unitsPerPoint, built.unit.id, built.note
                            )
                            calibrationOpen = false
                            measure.calibrating = false
                            measure.reset()
                        }
                    },
                    onStartReference = {
                        // بنقفل الورقة عشان المستخدم يشوف الرسمة ويرسم
                        // عليها؛ الورقة بترجع لوحدها لما يضغط "خلّص".
                        calibrationOpen = false
                        measure.enabled = true
                        measure.calibrating = true
                        measure.reset()
                    },
                    onClear = {
                        vm.clearPdfScale(path, state.currentPage)
                        calibrationOpen = false
                    },
                    onDismiss = { calibrationOpen = false }
                )
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

/**
 * لون القياس ثابت وواحد.
 *
 * القياس مش تعليم — مالوش ألوان بيختارها المستخدم. لون واحد معروف بيخلّي
 * الرقم يتقري فوراً على إنه قياس مش رسمة، ولا يتلخبط مع علامات التعليم.
 */
private const val MEASURE_COLOR = 0xFF00897BL

/** دقّة رسم الصفحة للـOCR — Tesseract متدرّب على المسح عند الرقم ده. */
private const val OCR_DPI = 300f

/** سقف بكسل الصورة اللي بتتقرا — رسمة A0 عند ٣٠٠ بتفوق أي ذاكرة. */
private const val OCR_MAX_PIXELS = 40_000_000L

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
    measuring: Boolean,
    onToggleMeasure: () -> Unit,
    onOrganize: () -> Unit,
    onOcr: () -> Unit,
    onImages: () -> Unit,
    onWatermark: () -> Unit,
    onMerge: () -> Unit,
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
                Icons.Filled.Straighten, "قياس على الرسمة", onToggleMeasure,
                active = measuring
            )
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
                        text = { Text("تنظيم الصفحات") },
                        leadingIcon = { Icon(Icons.Filled.Reorder, contentDescription = null) },
                        onClick = { menuOpen = false; onOrganize() }
                    )
                    DropdownMenuItem(
                        text = { Text("استخراج النص (OCR)") },
                        leadingIcon = { Icon(Icons.Filled.TextFields, contentDescription = null) },
                        onClick = { menuOpen = false; onOcr() }
                    )
                    DropdownMenuItem(
                        text = { Text("تصدير صور") },
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                        onClick = { menuOpen = false; onImages() }
                    )
                    DropdownMenuItem(
                        text = { Text("علامة مائية") },
                        leadingIcon = { Icon(Icons.Filled.WaterDrop, contentDescription = null) },
                        onClick = { menuOpen = false; onWatermark() }
                    )
                    DropdownMenuItem(
                        text = { Text("دمج مع ملفات تانية") },
                        leadingIcon = { Icon(Icons.Filled.CallMerge, contentDescription = null) },
                        onClick = { menuOpen = false; onMerge() }
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
 * بيصدّر نسخة فيها التعليقات كـ**تعليقات PDF حقيقية**.
 *
 * النسخة القديمة كانت بترستر كل صفحة لصورة وتلزقها في ملف جديد. ده كان
 * بيخلّي الملف الناتج:
 *   • أكبر بمرّات (رسمة متّجهة بقت صورة ٢٠٠٠ بكسل)،
 *   • مش قابل للبحث (طبقة النص بتضيع خالص)،
 *   • والتعليق نفسه بيبقى محبوس جوّه الصورة — مش تقدر تشيله ولا ترد عليه.
 *
 * دلوقتي كل علامة بتتكتب ككائن `/Annots` قياسي بمظهره (`/AP`): بتتفتح في
 * Acrobat وFoxit وأي عارض، وتتحدّد وتتمسح، والمستند الأصلي بيفضل زي ما هو
 * تحتها.
 *
 * بنكتب في ملف مؤقّت الأول وبعدين ننسخه للـURI اللي المستخدم اختاره —
 * PDFBox محتاج `File` عشان يقدر يرجع للكتابة العشوائية، والـURI ممكن
 * يكون على Google Drive أو أي مزوّد مش على القرص أصلاً.
 */
private suspend fun exportAnnotatedPdf(
    context: android.content.Context,
    source: File,
    uri: android.net.Uri,
    annotations: List<PdfAnnotationEntity>
): Result<Int> = withContext(Dispatchers.IO) {
    val temp = File(context.cacheDir, "annotated-${System.currentTimeMillis()}.pdf")
    try {
        PdfOps.ensureInit(context)
        val written = PdfOps.writeAnnotations(
            src = source,
            dest = temp,
            byPage = annotations.groupBy { it.page }
        ) { entity ->
            runCatching { json.decodeFromString<List<Float>>(entity.pointsJson) }
                .getOrDefault(emptyList())
        }.getOrThrow()

        context.contentResolver.openOutputStream(uri)?.use { out ->
            temp.inputStream().use { input -> input.copyTo(out) }
        } ?: error("مقدرناش نفتح الملف للكتابة")

        Result.success(written)
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        temp.delete()
    }
}
