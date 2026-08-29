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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.FactCheck
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.corewall.qaqc.pdfengine.PdfSessionHolder
import com.corewall.qaqc.stylus.PointerKind
import com.corewall.qaqc.stylus.PressureAverage
import com.corewall.qaqc.stylus.pressureWidthFactor
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
import com.corewall.qaqc.pdfengine.PdfPerfMetrics
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
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
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
    val holder = remember(path) { PdfSessionHolder() }
    var openError by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        val opened = withContext(Dispatchers.IO) {
            runCatching { PdfDocumentSession.open(context, file) }
        }
        // الماسك بيتصرّف لو الشاشة اتقفلت والمستند لسه بيتفتح — من غيره
        // المستند الأصلي وخيط الرندر بيفضلوا عايشين للأبد.
        opened.onSuccess { if (holder.accept(it)) session = it }
        opened.onFailure { e ->
            openError = (e as? PdfOpenException)?.userMessage ?: "مقدرناش نفتح الملف ده"
        }
    }

    DisposableEffect(path) { onDispose { holder.dispose() } }

    val active = session
    if (openError != null || active == null) {
        LoadingOrError(openError, onClose)
        return
    }

    // ── المحرّك والحالة
    val engine = remember(active) { TileEngine(active, TileEngine.budgetFor(context)) }
    val thumbs = remember(active) { ThumbnailCache(active) }
    var perfSnapshot by remember(active) { mutableStateOf(engine.performanceSnapshot()) }
    var perfVisible by remember(path) { mutableStateOf(true) }
    LaunchedEffect(engine, perfVisible) {
        if (!perfVisible) return@LaunchedEffect
        while (isActive) {
            delay(PERF_SAMPLE_INTERVAL_MS)
            perfSnapshot = engine.performanceSnapshot()
        }
    }
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

    // الاستعلام بقى مفلتر بالملف في SQL، فمفيش فلترة في Kotlin ومفيش
    // اشتراك في بيانات ملفات المستخدم مش فاتحها. `remember(path)` عشان
    // التدفّق ما يتبنيش من الأول مع كل إعادة تركيب.
    val fileMeasurements by remember(path) { vm.pdfMeasurementsFor(path) }
        .collectAsStateWithLifecycle(emptyList())
    val allScales by remember(path) { vm.pdfScalesFor(path) }
        .collectAsStateWithLifecycle(emptyList())

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
    var splitOpen by remember(path) { mutableStateOf(false) }
    var wirOpen by remember(path) { mutableStateOf(false) }
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

    val fileBookmarks by remember(path) { vm.pdfBookmarksFor(path) }
        .collectAsStateWithLifecycle(emptyList())

    /** طلبات الفحص المفتوحة — عشان الإرسال يختار من الموجود بدل ما يكتب. */
    val wirs by vm.wirs.collectAsStateWithLifecycle()

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
    val fileAnnotations by remember(path) { vm.pdfAnnotationsFor(path) }
        .collectAsStateWithLifecycle(emptyList())

    // فك نقاط التعليقات والقياسات كان يحصل من JSON داخل Canvas في كل إطار
    // تكبير. مستند فيه عشرات العناصر يحول السحب إلى مئات عمليات parsing في
    // الثانية؛ نخزنها هنا ولا نعيدها إلا عند تغير بيانات Room فعلاً.
    val annotationPoints = remember(fileAnnotations) {
        fileAnnotations.associate { item ->
            item.id to runCatching { json.decodeFromString<List<Float>>(item.pointsJson) }.getOrDefault(emptyList())
        }
    }
    val annotationsByPage = remember(fileAnnotations) { fileAnnotations.groupBy { it.page } }
    val measurementPoints = remember(fileMeasurements) {
        fileMeasurements.associate { item ->
            item.id to runCatching { json.decodeFromString<List<Float>>(item.pointsJson) }.getOrDefault(emptyList())
        }
    }
    val measurementsByPage = remember(fileMeasurements) { fileMeasurements.groupBy { it.page } }

    val settings by vm.settings.collectAsStateWithLifecycle()

    var tool by remember { mutableStateOf(PdfTool.PAN) }
    var style by remember { mutableStateOf(ToolStyle()) }
    /**
     * الخط اللي بيتبني دلوقتي.
     *
     * `SnapshotStateList` مش `List` في `mutableStateOf`: القديم كان بيعمل
     * نسخة جديدة من القايمة مع **كل نقطة** — يعني تكلفة تربيعية على طول
     * الخط. ومع القلم النقط أكتر بكتير (بناخد العيّنات التاريخية كمان)،
     * فالفرق ده بيبان كتأخير في طرف القلم.
     */
    val draft = remember(path) { mutableStateListOf<Offset>() }
    var draftPage by remember { mutableIntStateOf(-1) }

    /** متوسّط ضغط الخط الحالي — بيحدّد سُمكه عند الحفظ. */
    val draftPressure = remember(path) { PressureAverage() }
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

    // ── تحديد الأشكال وتعديلها
    /** العلامات المحدّدة دلوقتي. صفحة واحدة بس — التحديد عبر صفحات مالوش معنى. */
    val selectedIds = remember(path) { mutableStateListOf<Long>() }
    var selectPage by remember(path) { mutableIntStateOf(-1) }
    /** مستطيل التحديد أثناء السحب — بالإحداثيات المنسّبة للصفحة. */
    var marquee by remember(path) { mutableStateOf<Rect?>(null) }
    /** المقبض اللي ماسكه دلوقتي، وصندوق البداية والصندوق الحيّ. */
    var grabbed by remember(path) { mutableStateOf<BoxHandle?>(null) }
    var grabFrom by remember(path) { mutableStateOf<Rect?>(null) }
    var liveBox by remember(path) { mutableStateOf<Rect?>(null) }
    var dragLast by remember(path) { mutableStateOf(Offset.Zero) }
    /** نقطة بداية مستطيل التحديد — لازم تتحفظ لوحدها، لأن المستطيل
     *  المرتّب بيفقد معلومة "بدأنا من أنهي ركن" أول ما تسحب لفوق أو لليسار. */
    var marqueeAnchor by remember(path) { mutableStateOf(Offset.Zero) }

    fun clearSelection() {
        selectedIds.clear()
        selectPage = -1
        marquee = null
        grabbed = null
        grabFrom = null
        liveBox = null
    }

    /** بيرمي الخط الحالي من غير ما يحفظه — للإلغاء ولخط قصير مالوش معنى. */
    fun discardDraft() {
        draft.clear()
        draftPage = -1
        draftPressure.reset()
    }

    fun commitDraft() {
        val toolId = tool.id
        val page = draftPage
        val slot = state.layout.slotAt(page)
        if (toolId == null || page < 0 || draft.size < 2 || slot == null) {
            discardDraft(); return
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
                // الضغط بيكبّر أو يصغّر سُمك الخط كله. الجهاز اللي مابيقيسش
                // ضغط بيدّي معامل ١ فالسُمك بيفضل زي ما المستخدم ظابطه.
                strokeWidth = style.widthPt * pressureWidthFactor(draftPressure.value),
                opacity = style.opacity
            )
        )
        redoStack.clear()
        discardDraft()
    }

    /**
     * وضع "الرسم بالقلم بس".
     *
     * القاعدة بسيطة ومطبّقة في مكان واحد: **الصباع بيحرّك الصفحة، والقلم
     * بيشتغل بالأداة المختارة.** والأداة هنا مش الرسم بس — القياس ومعايرة
     * المقياس كمان، لأن دول بيتحطّوا بالنقر وكان الصباع بيقدر يحطّهم.
     *
     * لما الأداة تكون "تنقّل" مافيش شغل للقلم، فبنسيبه يمرّر عادي بدل ما
     * يبقى ميت في إيد المستخدم.
     */
    val penHasJob = settings.stylusOnly && (tool.isDrawing || measure.enabled)

    /** طبقة الحبر: القلم بس، ولمّا يكون فيه أداة رسم فعلاً. */
    val inkOn = settings.stylusOnly && tool.isDrawing && !measure.enabled

    val inkAccept: ((PointerKind) -> Boolean)? =
        if (inkOn) ({ kind: PointerKind -> kind.isPen }) else null

    /** التنقّل: كل حاجة، إلا القلم لما يكون ليه شغل. */
    val gestureAccept: (PointerKind) -> Boolean =
        remember(penHasJob) { { kind: PointerKind -> !(penHasJob && kind.isPen) } }

    /** النقرة دي مسموح لها تحطّ نقطة قياس؟ في وضع القلم: القلم بس. */
    fun tapCanMeasure(kind: PointerKind) = !settings.stylusOnly || kind.isPen

    // فلترة بتتعاد مع كل إعادة تركيب لو مااتحفظتش — و`currentPage` بيتغيّر
    // مع التمرير، يعني الشاشة دي بتعيد التركيب كتير وهي فاتحة.
    val pageAnnotations = remember(fileAnnotations, state.currentPage) {
        fileAnnotations.filter { it.page == state.currentPage }
    }

    /** صندوق التحديد الحالي — اتحاد صناديق الأشكال المحدّدة. */
    fun selectionBox(): Rect? = unionBounds(
        selectedIds.mapNotNull { id -> annotationBounds(annotationPoints[id].orEmpty()) }
    )

    /** بيحفظ التحويل من [grabFrom] لـ[liveBox] على كل شكل محدّد. */
    fun commitTransform() {
        val from = grabFrom
        val to = liveBox
        grabbed = null; grabFrom = null; liveBox = null
        if (from == null || to == null || !to.movedFrom(from)) return
        val edited = selectedIds.mapNotNull { id ->
            val entity = fileAnnotations.firstOrNull { it.id == id } ?: return@mapNotNull null
            val points = annotationPoints[id].orEmpty()
            if (points.isEmpty()) return@mapNotNull null
            entity.copy(pointsJson = json.encodeToString(transformPoints(points, from, to)))
        }
        vm.updatePdfAnnotations(edited)
    }

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
                // في وضع القلم الإيماءات بتفضل شغّالة عشان الصباع يمرّر
                // ويكبّر، والقلم بياخد الحبر من طبقته.
                // التحديد بياخد السحب زي الرسم، بس **حتى في وضع القلم**:
                // التحديد مش حبر، والصباع المفروض يعرف يمسك شكل.
                drawingActive = !measure.enabled &&
                    (tool == PdfTool.SELECT || (tool.isDrawing && !settings.stylusOnly)),
                onDrawStart = { p ->
                    if (tool == PdfTool.SELECT) {
                        val hit = state.pageHit(p)
                        val at = hit?.let { state.pointOnPage(it.page, p) }
                        if (hit != null && at != null) {
                            dragLast = p
                            // المقابض بتخصّ صفحة التحديد بس. سحب على صفحة
                            // تانية معناه تحديد جديد، مش تعديل على القديم.
                            val box = if (hit.page == selectPage) selectionBox() else null
                            val handle = box?.let {
                                handleAt(
                                    it, at.x, at.y,
                                    state.normalisedTolerance(hit.page, HANDLE_GRAB_PX)
                                )
                            }
                            if (handle != null && box != null) {
                                grabbed = handle
                                grabFrom = box
                                liveBox = box
                            } else {
                                // سحب على الفاضي = مستطيل تحديد جديد، والقديم
                                // بيتفضّى فوراً مش في الآخر — غير كده الشاشة
                                // بترسم صناديق صفحة تانية فوق دي.
                                selectedIds.clear()
                                selectPage = hit.page
                                marqueeAnchor = at
                                marquee = Rect(at.x, at.y, at.x, at.y)
                            }
                        }
                    } else {
                        state.pageHit(p)?.let { hit ->
                            draftPage = hit.page
                            draft.clear()
                            draft += p
                        }
                    }
                },
                onDrawMove = { p ->
                    if (tool == PdfTool.SELECT) {
                        val page = selectPage
                        val at = if (page >= 0) state.pointOnPage(page, p) else null
                        val was = if (page >= 0) state.pointOnPage(page, dragLast) else null
                        if (at != null && was != null) {
                            val handle = grabbed
                            val current = marquee
                            if (handle != null) {
                                liveBox = liveBox?.let { handle.applyTo(it, at.x - was.x, at.y - was.y) }
                            } else if (current != null) {
                                marquee = rectOf(marqueeAnchor, at)
                            }
                            dragLast = p
                        }
                    } else if (draftPage >= 0) {
                        if (tool.freeform) {
                            draft += p
                        } else {
                            val first = draft.firstOrNull() ?: p
                            draft.clear(); draft += first; draft += p
                        }
                    }
                },
                onDrawEnd = {
                    if (tool == PdfTool.SELECT) {
                        val box = marquee
                        if (box != null) {
                            marquee = null
                            // مستطيل صغير جداً = نقرة اتعاملت كسحب. سيبها
                            // للنقرة تتعامل معاها بدل ما نفضّي التحديد.
                            if (box.width > MARQUEE_MIN || box.height > MARQUEE_MIN) {
                                val picked = annotationsByPage[selectPage].orEmpty().filter { a ->
                                    annotationBounds(annotationPoints[a.id].orEmpty())
                                        ?.touches(box) == true
                                }
                                selectedIds.addAll(picked.map { it.id })
                            }
                        } else {
                            commitTransform()
                        }
                    } else {
                        commitDraft()
                    }
                },
                onDrawCancel = {
                    if (tool == PdfTool.SELECT) {
                        marquee = null; grabbed = null; grabFrom = null; liveBox = null
                    } else {
                        discardDraft()
                    }
                },
                inkAccept = inkAccept,
                onInkStart = { point, pressure ->
                    state.pageHit(point)?.let { hit ->
                        draftPage = hit.page
                        draft.clear()
                        draft += point
                        draftPressure.reset()
                        draftPressure.add(pressure)
                    }
                },
                onInkMove = { point, pressure ->
                    if (draftPage >= 0) {
                        draftPressure.add(pressure)
                        if (tool.freeform) {
                            draft += point
                        } else {
                            // الأشكال نقطتين: مكان نزول القلم، ومكانه دلوقتي.
                            val first = draft.firstOrNull() ?: point
                            draft.clear(); draft += first; draft += point
                        }
                    }
                },
                gestureAccept = gestureAccept,
                tapsWhileDrawing = tool == PdfTool.SELECT,
                onTap = { point, kind ->
                    when {
                        // في وضع القياس النقرة بتحطّ نقطة. إخفاء الواجهة
                        // بيبقى على زرار الخروج بس — نقرة غامضة وسط قياس
                        // معناها رقم غلط ومحدش هيلاحظ.
                        // القياس ومعايرة المقياس بيتحطّوا بالنقر، فلازم
                        // يتفرزوا زي الحبر: في وضع القلم، نقرة الصباع
                        // بتقلب الواجهة بس ومابتحطّش نقطة.
                        measure.enabled && tapCanMeasure(kind) -> {
                            val hit = state.pageHit(point)
                            if (hit != null) measure.addPoint(hit.page, hit.nx, hit.ny)
                        }
                        measure.enabled -> chromeVisible = !chromeVisible
                        // نقرة بأداة التحديد بتمسك الشكل اللي تحتها.
                        tool == PdfTool.SELECT -> {
                            val hit = state.pageHit(point)
                            val picked = hit?.let { h ->
                                val tolerance = state.normalisedTolerance(h.page, HIT_TOLERANCE_PX)
                                annotationsByPage[h.page].orEmpty()
                                    .filter { a ->
                                        annotationHit(
                                            PdfTool.fromId(a.tool),
                                            annotationPoints[a.id].orEmpty(),
                                            h.nx, h.ny, tolerance
                                        )
                                    }
                                    // الأصغر مساحة هو الأقرب لنية المستخدم:
                                    // شكل صغير جوّه شكل كبير المفروض يتمسك هو.
                                    .minByOrNull { a ->
                                        annotationBounds(annotationPoints[a.id].orEmpty())
                                            ?.let { it.width * it.height } ?: Float.MAX_VALUE
                                    }
                            }
                            if (picked != null && hit != null) {
                                selectedIds.clear()
                                selectedIds.add(picked.id)
                                selectPage = hit.page
                            } else {
                                // نقرة على الفاضي بتفضّي التحديد. ولو مافيش
                                // تحديد أصلاً بتعمل اللي بتعمله دايماً —
                                // تخفي الواجهة. من غير كده الشريط بيبقى
                                // مالوش طريقة يختفي وانت في وضع التحديد.
                                val had = selectedIds.isNotEmpty()
                                clearSelection()
                                if (!had) chromeVisible = !chromeVisible
                            }
                        }
                        // أي نقرة بتلغي التحديد الأول. النقرة اللي بتخفي
                        // الواجهة وسايبة تحديد معلّق بتبان كأنها باج.
                        selection.isActive -> selection.clear()
                        else -> chromeVisible = !chromeVisible
                    }
                },
                onLongPress = { point, _ ->
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
                    // أثناء شدّ الصندوق بنرسم الأشكال في مكانها الجديد
                    // مباشرة. من غير المعاينة دي "اظبط أبعاده" بتبقى تخمين:
                    // بتشدّ صندوق فاضي وتستنى ترفع إيدك عشان تشوف النتيجة.
                    val previewFrom = grabFrom
                    val previewTo = liveBox
                    val previewing = previewFrom != null && previewTo != null
                    drawAnnotations(
                        s, annotationsByPage, annotationPoints,
                        skip = if (previewing) selectedIds.toSet() else emptySet()
                    )
                    if (previewFrom != null && previewTo != null) {
                        selectedIds.forEach { id ->
                            val item = fileAnnotations.firstOrNull { it.id == id }
                            val stored = annotationPoints[id].orEmpty()
                            if (item != null && stored.isNotEmpty()) {
                                val moved = transformPoints(stored, previewFrom, previewTo)
                                val screen = (moved.indices step 2).mapNotNull { i ->
                                    if (i + 1 >= moved.size) null
                                    else s.pagePointToScreen(item.page, moved[i], moved[i + 1])
                                }
                                drawAnnotation(
                                    PdfTool.fromId(item.tool), Color(item.color), screen,
                                    item.strokeWidth, item.opacity, s.zoom
                                )
                            }
                        }
                    }
                    if (draft.size >= 2) {
                        drawAnnotation(
                            tool, Color(style.colorArgb), draft,
                            style.widthPt, style.opacity, s.zoom
                        )
                    }
                    if (selection.isActive) drawSelection(s, selection.quads, c.accent)

                    // صندوق التحديد ومقابضه فوق الأشكال — لازم يبانوا حتى
                    // لو الشكل نفسه غامق.
                    if (tool == PdfTool.SELECT && selectPage >= 0) {
                        drawObjectSelection(
                            state = s,
                            page = selectPage,
                            boxes = selectedIds.mapNotNull { id ->
                                annotationBounds(annotationPoints[id].orEmpty())
                            },
                            liveBox = liveBox,
                            marquee = marquee,
                            colour = c.accent
                        )
                    }

                    // القياس فوق الكل: هو النتيجة اللي المستخدم بيقرأها،
                    // ولو تعليق غطّاه بيبقى الرقم موجود ومش مقروء.
                    drawMeasurements(
                        state = s,
                        itemsByPage = measurementsByPage,
                        pointsOf = { m -> measurementPoints[m.id].orEmpty() },
                        scaleOf = { page -> scaleFor(page) }
                    )
                    if (measure.enabled) {
                        drawMeasureDraft(s, measure, pageScale, Color(MEASURE_COLOR))
                    }
                }
            )

            if (perfVisible) {
                PdfPerfHud(
                    snapshot = perfSnapshot,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(Space.md)
                )
            }

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
                    onSplit = { splitOpen = true },
                    perfVisible = perfVisible,
                    onTogglePerf = { perfVisible = !perfVisible },
                    onToggleRail = { railVisible = !railVisible },
                    onToggleMode = {
                        state.setMode(
                            if (state.mode == ViewMode.CONTINUOUS_VERTICAL) ViewMode.CONTINUOUS_HORIZONTAL
                            else ViewMode.CONTINUOUS_VERTICAL,
                            active.allSizes()
                        )
                    },
                    onExport = { exportLauncher.launch("${file.nameWithoutExtension}-معلّق.pdf") },
                    onSendToWir = { wirOpen = true }
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

            /**
             * مؤشّر وضع القلم.
             *
             * شارة صغيرة بتبان **بس** لما الوضع يبقى فعّال فعلاً (فيه أداة
             * رسم مختارة)، مش لافتة دايمة. لو ماكانتش موجودة، المستخدم اللي
             * بيحاول يرسم بصباعه مش هيفهم ليه مافيش حبر — والسكوت هنا بيبان
             * كعطل.
             */
            AnimatedVisibility(
                visible = chromeVisible && penHasJob,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = MINIMAP_TOP_PAD, start = Space.md)
            ) {
                Surface(
                    color = c.accentContainer,
                    contentColor = c.onAccentContainer,
                    shape = Radius.pill
                ) {
                    Row(
                        Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        Icon(
                            Icons.Filled.Draw,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.sm)
                        )
                        Text(if (inkOn) "القلم بيكتب" else "القلم بيقيس", style = CwText.codeSmall)
                    }
                }
            }

            // ── الخريطة المصغّرة: عند التكبير العالي بس
            AnimatedVisibility(
                // رسم المصغرة يمر عبر نفس خيط PDFium المستخدم للبلاطات؛ لا
                // نسمح له بمنافسة التكبير الحي ثم نعيده بمجرد رفع الأصابع.
                visible = chromeVisible && !state.interacting && state.zoom > MINIMAP_FROM_ZOOM,
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
                            // الخروج من أداة التحديد بيفضّيه — صندوق فاضل
                            // على الشاشة وانت ماسك قلم بيبان كأنه عطل.
                            if (tool != PdfTool.SELECT) clearSelection()
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
                        selectedCount = if (tool == PdfTool.SELECT) selectedIds.size else 0,
                        onDeleteSelected = {
                            vm.deletePdfAnnotations(selectedIds.toList())
                            clearSelection()
                        },
                        onRestyleSelected = {
                            vm.updatePdfAnnotations(
                                selectedIds.mapNotNull { id ->
                                    fileAnnotations.firstOrNull { it.id == id }?.copy(
                                        color = style.colorArgb,
                                        strokeWidth = style.widthPt,
                                        opacity = style.opacity
                                    )
                                }
                            )
                        },
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

            if (wirOpen) {
                SendToWirSheet(
                    page = state.currentPage,
                    existing = wirs,
                    onSend = { name ->
                        wirOpen = false
                        vm.sendPageToWir(name, path, state.currentPage) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onDismiss = { wirOpen = false }
                )
            }

            if (splitOpen) {
                SplitPdfSheet(
                    pageCount = active.pageCount,
                    running = opRunning,
                    onSplit = {
                        splitOpen = false
                        opRunning = true
                        scope.launch {
                            val outDir = File(file.parentFile, "${file.nameWithoutExtension} — صفحات")
                            val result = PdfOps.splitIntoPages(file, outDir, file.nameWithoutExtension)
                            opRunning = false
                            result.onSuccess { pages ->
                                Toast.makeText(context, "اتقسم الملف إلى ${pages.size} صفحة في ${outDir.name}", Toast.LENGTH_LONG).show()
                            }.onFailure { error ->
                                Toast.makeText(context, "فشل التقسيم: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onDismiss = { if (!opRunning) splitOpen = false }
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
/** نصف قطر مسك مقبض الصندوق بالبكسل. */
private const val HANDLE_GRAB_PX = 28f

/** سماحية اختيار شكل بالنقر، بالبكسل — إصبع مش فأرة. */
private const val HIT_TOLERANCE_PX = 18f

/** أقل مقاس لمستطيل التحديد قبل ما نعتبره سحب مش نقرة (منسّب). */
private const val MARQUEE_MIN = 0.01f

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
private const val PERF_SAMPLE_INTERVAL_MS = 1_000L

@Composable
private fun PdfPerfHud(snapshot: PdfPerfMetrics.Snapshot, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xE6101216),
        shape = Radius.shapeMd
    ) {
        Column(Modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
            Text("PDF PERF", style = CwText.codeSmall, color = Color(0xFF6BE4B5))
            Text(
                "Tile ${snapshot.averageTileMs}/${snapshot.p95TileMs}ms · hit ${(snapshot.cacheHitRate * 100).toInt()}%",
                style = CwText.codeSmall,
                color = Color.White
            )
            Text(
                "${snapshot.cachedTiles} cache · ${snapshot.queuedTiles} queue · ${(snapshot.bitmapBytes / 1_048_576)}MB",
                style = CwText.codeSmall,
                color = Color(0xFFBAC3CE)
            )
        }
    }
}

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

/**
 * صندوق التحديد ومقابضه ومستطيل التحديد.
 *
 * المقابض بمقاس ثابت **بالبكسل** مش منسّب: المقبض حاجة بتتمسك بالصباع،
 * فمقاسه بيتبع الشاشة مش الورقة — عكس العلامة نفسها اللي بتكبر مع الرسمة.
 */
private fun DrawScope.drawObjectSelection(
    state: PdfViewerState,
    page: Int,
    boxes: List<Rect>,
    liveBox: Rect?,
    marquee: Rect?,
    colour: Color
) {
    fun screenRect(box: Rect): Rect? {
        val a = state.pagePointToScreen(page, box.left, box.top) ?: return null
        val b = state.pagePointToScreen(page, box.right, box.bottom) ?: return null
        return Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

    // كل شكل محدّد بحدّ خفيف — عشان تعرف إيه اللي جوّه التحديد بالظبط.
    if (liveBox == null && boxes.size > 1) {
        boxes.forEach { box ->
            screenRect(box)?.let { r ->
                drawRect(
                    colour.copy(alpha = 0.5f), r.topLeft, Size(r.width, r.height),
                    style = Stroke(1.5f, pathEffect = dash)
                )
            }
        }
    }

    val outer = liveBox ?: unionBounds(boxes)
    if (outer != null) {
        screenRect(outer)?.let { r ->
            drawRect(colour.copy(alpha = 0.10f), r.topLeft, Size(r.width, r.height))
            drawRect(colour, r.topLeft, Size(r.width, r.height), style = Stroke(2f))
            listOf(
                Offset(r.left, r.top), Offset(r.right, r.top),
                Offset(r.left, r.bottom), Offset(r.right, r.bottom)
            ).forEach { corner ->
                drawCircle(Color.White, HANDLE_DRAW_PX, corner)
                drawCircle(colour, HANDLE_DRAW_PX, corner, style = Stroke(2.5f))
            }
        }
    }

    if (marquee != null) {
        screenRect(marquee)?.let { r ->
            drawRect(colour.copy(alpha = 0.08f), r.topLeft, Size(r.width, r.height))
            drawRect(
                colour, r.topLeft, Size(r.width, r.height),
                style = Stroke(2f, pathEffect = dash)
            )
        }
    }
}

/** نصف قطر المقبض المرسوم بالبكسل. */
private const val HANDLE_DRAW_PX = 11f

/** بيرسم تعليقات كل صفحة مرئية في مكانها الصح. */
private fun DrawScope.drawAnnotations(
    state: PdfViewerState,
    annotationsByPage: Map<Int, List<PdfAnnotationEntity>>,
    pointsById: Map<Long, List<Float>>,
    /** علامات بترسم في مكان تاني دلوقتي (معاينة التحويل) — بنتخطّاها هنا. */
    skip: Set<Long> = emptySet()
) {
    if (annotationsByPage.isEmpty()) return
    val rect = state.visibleDocRect()
    for (slot in state.layout.visible(rect.left, rect.top, rect.right, rect.bottom)) {
        for (a in annotationsByPage[slot.index].orEmpty()) {
        if (a.id in skip) continue
        val flat = pointsById[a.id].orEmpty()
        if (flat.isEmpty()) continue
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
    onSplit: () -> Unit,
    perfVisible: Boolean,
    onTogglePerf: () -> Unit,
    onToggleRail: () -> Unit,
    onToggleMode: () -> Unit,
    onExport: () -> Unit,
    onSendToWir: () -> Unit
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
                        text = { Text(if (perfVisible) "إخفاء مؤشرات الأداء" else "إظهار مؤشرات الأداء") },
                        onClick = { menuOpen = false; onTogglePerf() }
                    )
                    DropdownMenuItem(
                        text = { Text("أرسل الصفحة لـWIR") },
                        leadingIcon = { Icon(Icons.Filled.FactCheck, contentDescription = null) },
                        onClick = { menuOpen = false; onSendToWir() }
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
                        text = { Text("تقسيم إلى صفحات") },
                        leadingIcon = { Icon(Icons.Filled.CallSplit, contentDescription = null) },
                        onClick = { menuOpen = false; onSplit() }
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
