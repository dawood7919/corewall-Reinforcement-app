package com.corewall.qaqc.ui.takeoff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TakeoffAnnotationEntity
import com.corewall.qaqc.data.db.TakeoffCategoryEntity
import com.corewall.qaqc.data.db.TakeoffGroupEntity
import com.corewall.qaqc.data.db.TakeoffItemEntity
import com.corewall.qaqc.pdfengine.PageLayout
import com.corewall.qaqc.pdfengine.PdfCanvas
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.pdfengine.PdfOpenException
import com.corewall.qaqc.pdfengine.PdfSessionHolder
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.pdfengine.TileEngine
import com.corewall.qaqc.pdfengine.pageHit
import com.corewall.qaqc.stylus.PointerKind
import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffAnnotation
import com.corewall.qaqc.takeoff.TakeoffAnnotationType
import com.corewall.qaqc.takeoff.TakeoffGeometryPart
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffMath
import com.corewall.qaqc.takeoff.TakeoffPoint
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.takeoff.TakeoffVertexTarget
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as DesignStroke
import com.corewall.qaqc.v2.pdf.V2DocumentPoint
import com.corewall.qaqc.v2.pdf.V2InkStyle
import com.corewall.qaqc.v2.pdf.V2InkUndoResult
import com.corewall.qaqc.v2.pdf.V2MeasurementFinishResult
import com.corewall.qaqc.v2.pdf.V2PageCalibration
import com.corewall.qaqc.v2.pdf.V2PdfWorkspaceHost
import com.corewall.qaqc.v2.pdf.V2PersistedInkStroke
import com.corewall.qaqc.v2.pdf.V2PersistedTakeoffItem
import com.corewall.qaqc.v2.pdf.V2TakeoffCommitCoordinator
import com.corewall.qaqc.v2.pdf.V2TakeoffCommitResult
import com.corewall.qaqc.v2.pdf.V2WorkspaceController
import com.corewall.qaqc.v2.pdf.V2WorkspaceTool
import com.corewall.qaqc.v2.pdf.toV2WorkspaceTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * شاشة الحصر — الرسمة وأدواتها.
 *
 * ## اللي اتعمل إعادة استخدام له بدل ما يتبني من جديد
 *
 * المحرّك (PDFium بمربّعات)، حالة العرض، الإيماءات، وفرز القلم — كلهم
 * من [PdfCanvas] الموجود. مواصفة البورت كانت بتقترح محرّك `PdfRenderer`
 * منفصل، وده كان هيبقى **محرّك PDF تاني في نفس التطبيق** — الفخ رقم ٧
 * في المواصفة نفسها.
 *
 * ## الجسر بين الشاشة والحساب
 *
 * `state.pageHit(screen)` بيرجّع `(page, nx, ny)` **منسّبة ٠..١** —
 * وده بالظبط نظام إحداثيات الحصر. فمفيش تحويل وسيط ولا فرصة لخلط
 * المساحات: اللمسة بتتحوّل مرة واحدة عند الحدود وخلاص.
 *
 * ## وضع الشاشة — [EditorMode]
 *
 * أداتين من غير الأربعة الأصليين (مساحة/طول/عدّ/خصم) محتاجين سحب مش
 * نقر: المستطيل، تعديل الرؤوس، والتحديد بمستطيل. التلاتة دول بيشغّلوا
 * `drawingActive = true` على [PdfCanvas]، وده بيقفل طبقة النقر تماماً
 * (شايفها في [PdfCanvas] نفسها) — يعني الأربعة الأصليين والتلاتة دول
 * مايشتغلوش في نفس اللحظة بحكم التصميم، مش عن طريق فحص شرط.
 */
private enum class EditorMode { POINTER, DRAW, RECT, VERTEX, BOXSELECT }

/**
 * أداة مختارة مستنّية اسم وفئة ولون **قبل** الرسم — عكس السلوك القديم
 * (السؤال بعد ما الشكل يخلص). المستخدم عايز الـID يتحجز في القاعدة
 * من الأول عشان يقدر يرشّحه في الصيغ بـ`@` وهو لسه بيرسم، مش بعدين.
 * [viaRect] بيفرّق مستطيل عن رسم بالنقر — نفس الأداة (مساحة) بس وضع
 * تحرير مختلف ([EditorMode.RECT] بدل [EditorMode.DRAW]).
 */
private data class PendingNaming(val tool: TakeoffTool, val viaRect: Boolean = false)

/**
 * تراجع بمستوى واحد — العكس المباشر لآخر تعديل. مقصود إنه مستوى واحد
 * بس مش تاريخ كامل: تخزين لقطة قبل **كل** تعديل ممكن (سحب، حذف،
 * إضافة) بيعقّد أي عملية بشكل كبير وبيزوّد فرص الأخطاء، ومستوى واحد
 * بيغطي الحالة الأكتر شيوعًا فعليًا — "غلطت في آخر حاجة عملتها،
 * ورّيني رجّعها". [label] بيتعرض في شريط الأدوات عشان يبان بيرجّع
 * إيه بالظبط.
 */
private data class UndoAction(val label: String, val perform: suspend () -> Unit)
/** بيانات البند المحجوزة لقياس V2 قبل حفظ هندسته النهائية في Room. */
private data class PendingV2Measurement(
    val tool: TakeoffTool,
    val name: String,
    val categoryId: Long?,
    val colorArgb: Long,
    val thicknessMetres: Double?
)

/** لون ثابت لخطوط القياس المرجعية — مميّز عن باليت البنود وعن رمادي الخصم. */
private const val DIMENSION_COLOR = 0xFF42A5F5L

/** لون ثابت لكل التعليقات — طبقة توضيح واحدة، مالهاش باليت زي البنود. */
private const val ANNOTATION_COLOR = 0xFFFFB300L

@Composable
fun TakeoffEditorScreen(
    vm: MainViewModel,
    drawingId: Long,
    path: String,
    onClose: () -> Unit
) {
    val c = LocalCwColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val file = remember(path) { File(path) }

    // ── فتح الرسمة
    var session by remember(path) { mutableStateOf<PdfDocumentSession?>(null) }
    val holder = remember(path) { PdfSessionHolder() }
    var openError by remember(path) { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        val opened = withContext(Dispatchers.IO) {
            runCatching { PdfDocumentSession.open(context, file) }
        }
        opened.onSuccess { if (holder.accept(it)) session = it }
        opened.onFailure { e ->
            openError = (e as? PdfOpenException)?.userMessage ?: "مقدرناش نفتح الرسمة"
        }
    }
    DisposableEffect(path) { onDispose { holder.dispose() } }

    val active = session
    if (openError != null || active == null) {
        Surface(Modifier.fillMaxSize(), color = c.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (openError != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(openError!!, color = c.textSecondary)
                        CwIconButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onClose)
                    }
                } else CircularProgressIndicator(color = c.accent)
            }
        }
        return
    }

    val engine = remember(active) { TileEngine(active, TileEngine.budgetFor(context)) }
    DisposableEffect(active) { onDispose { engine.clear() } }

    val state = remember(active) { PdfViewerState(active.pageCount) }
    val v2Controller = remember { V2WorkspaceController() }
    val v2CommitCoordinator = remember(drawingId) { V2TakeoffCommitCoordinator(drawingId, vm.takeoff) }
    val measured by active.measuredCount.collectAsStateWithLifecycle()

    LaunchedEffect(measured, state.mode) {
        state.updateLayout(PageLayout.build(active.allSizes(), state.mode))
    }
    LaunchedEffect(state.currentPage) {
        val from = (state.currentPage - 2).coerceAtLeast(0)
        val to = (state.currentPage + 4).coerceAtMost(active.pageCount - 1)
        for (p in from..to) active.measure(p)
    }

    // ── القسم المالك — عشان الفئات (مستوى القسم، مش الرسمة).
    var projectId by remember(drawingId) { mutableStateOf<Long?>(null) }
    var drawingName by remember(drawingId) { mutableStateOf("") }
    LaunchedEffect(drawingId) {
        val drawing = vm.takeoff.drawingById(drawingId)
        projectId = drawing?.projectId
        drawingName = drawing?.name.orEmpty()
        // بنود اتسمّت في جلسة فاتت وماترسمتش — بتتشال هنا. آمن دلوقتي
        // بالظبط لأن مفيش رسم شغّال لسه عند فتح الرسمة.
        vm.takeoff.purgeEmptyItems(drawingId)
    }
    val categories: List<TakeoffCategoryEntity> by remember(projectId) {
        projectId?.let { vm.takeoff.categories(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList<TakeoffCategoryEntity>())
    }.collectAsStateWithLifecycle(emptyList())
    val groups: List<TakeoffGroupEntity> by remember(projectId) {
        projectId?.let { vm.takeoff.groups(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList<TakeoffGroupEntity>())
    }.collectAsStateWithLifecycle(emptyList())

    // ── البيانات المخزّنة
    val rows by remember(drawingId) { vm.takeoff.items(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val scaleRows by remember(drawingId) { vm.takeoff.scales(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val formulaRows by remember(drawingId) { vm.takeoff.formulas(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val annotationRows by remember(drawingId) { vm.takeoff.annotations(drawingId) }
        .collectAsStateWithLifecycle(emptyList())

    val items = remember(rows) { rows.map { vm.takeoff.toModel(it) } }
    val pageItems = remember(items, state.currentPage) {
        items.filter { it.page == state.currentPage }
    }
    val annotations = remember(annotationRows) { annotationRows.map { vm.takeoff.annotationToModel(it) } }
    val pageAnnotations = remember(annotations, state.currentPage) {
        annotations.filter { it.page == state.currentPage }
    }

    /** هندسة الصفحة الحالية — مقاسها بالنقط + معايرتها. */
    val pageGeometry = remember(measured, state.currentPage, scaleRows) {
        val size = active.knownSize(state.currentPage) ?: active.estimate
        val mpp = scaleRows.firstOrNull { it.page == state.currentPage }?.metresPerPoint ?: 0.0
        PageGeometry(size.width.toDouble(), size.height.toDouble(), mpp)
    }

    /**
     * هندسة **أي** صفحة في الرسمة دي — مش الحالية بس. الصيغ بترجع لبنود
     * ممكن تكون على صفحات مختلفة، وكل واحدة محتاجة معايرتها هي.
     */
    val pageGeometryFor = remember(active, scaleRows) {
        { page: Int ->
            val size = active.knownSize(page) ?: active.estimate
            val mpp = scaleRows.firstOrNull { it.page == page }?.metresPerPoint ?: 0.0
            PageGeometry(size.width.toDouble(), size.height.toDouble(), mpp)
        }
    }

    // ── حالة الأدوات
    var mode by remember { mutableStateOf(EditorMode.POINTER) }
    var tool by remember { mutableStateOf(TakeoffTool.AREA) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var multiSelectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var colourIndex by remember { mutableIntStateOf(0) }
    var calibrating by remember { mutableStateOf(false) }
    var totalsOpen by remember { mutableStateOf(false) }
    var formulasOpen by remember { mutableStateOf(false) }
    var toolsSheetOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    /** الدوك السفلي مفرود ولا مطوي — مطوي بيدّي الرسمة الشاشة كلها. */
    var dockExpanded by rememberSaveable { mutableStateOf(false) }
    var lastUndo by remember { mutableStateOf<UndoAction?>(null) }
    var deductFor by remember { mutableStateOf<TakeoffItem?>(null) }
    /** بند بنضيف له هندسة إضافية (حلقة/قطعة جديدة) بدل إنشاء بند مستقل. */
    var addToShapeFor by remember { mutableStateOf<TakeoffItem?>(null) }
    var pendingNaming by remember { mutableStateOf<PendingNaming?>(null) }
    /** بند اتسمّى واتحجز ID له بس لسه ماترسمش — بيتحدّث بالهندسة عند الحفظ. */
    var pendingDrawItemId by remember { mutableStateOf<Long?>(null) }
    var pendingV2Measurement by remember { mutableStateOf<PendingV2Measurement?>(null) }
    var inkMode by remember { mutableStateOf(false) }
    var inkColorArgb by remember { mutableStateOf(0xFF1976D2L) }
    var inkWidthPx by remember { mutableStateOf(3.4f) }
    var editingItem by remember { mutableStateOf<TakeoffItem?>(null) }
    var snapEnabled by remember { mutableStateOf(true) }
    /** رسالة جلسة القياس؛ تمنع الإنهاء الناقص من الظهور كفقدان صامت للرسم. */
    var measurementNotice by remember { mutableStateOf<String?>(null) }

    // ── حالة التعليقات — طبقة مستقلة عن أدوات الحصر تمامًا.
    var annotationTool by remember { mutableStateOf<TakeoffAnnotationType?>(null) }
    val annotationDraft = remember(path) { mutableStateListOf<TakeoffPoint>() }
    var annotationDraftPage by remember { mutableIntStateOf(-1) }
    var textPromptPoint by remember { mutableStateOf<TakeoffPoint?>(null) }
    var textPromptPage by remember { mutableIntStateOf(-1) }
    var selectedAnnotationId by remember { mutableStateOf<String?>(null) }

    /**
     * المسوّدة **بإحداثيات الصفحة المنسّبة**، مش بإحداثيات الشاشة.
     *
     * ده مقصود: التمرير والتكبير شغّالين وأنت في نص شكل، فلو خزّنّا نقطة
     * الشاشة وأجّلنا التحويل للحظة الحفظ، أي تمرير بين أول لمسة والحفظ
     * كان هيزحلق الشكل كله. التحويل بيحصل **مرة واحدة عند اللمس** —
     * وبعدها النقطة متعلّقة في الورق، مش في الشاشة.
     */
    val draft = remember(path) { mutableStateListOf<TakeoffPoint>() }
    val draftPage = remember { mutableIntStateOf(-1) }
    /** نقطتا المعايرة — بتتجمعوا بنفس آلية المسوّدة. */
    val calibPoints = remember(path) { mutableStateListOf<TakeoffPoint>() }
    /**
     * نقطة إزاحة خط القياس وهو بيتسحب — زي AutoCAD بالظبط: بعد لمسَتين
     * تحدّدوا الطرفين، النقطة التالتة مش لمسة تالتة، دي سحب مستمر ("لوره
     * أو أدّام") لحد ما تسيب إصبعك في المكان المطلوب.
     */
    var dimDragPoint by remember { mutableStateOf<TakeoffPoint?>(null) }

    // ── حالة المستطيل (سحب) — نفس مبدأ المسوّدة: صفحة منسّبة، مش شاشة.
    var rectDraft by remember { mutableStateOf<Pair<TakeoffPoint, TakeoffPoint>?>(null) }
    var rectPage by remember { mutableIntStateOf(-1) }

    // ── حالة التحديد بمستطيل — إحداثيات شاشة خالص، معاينة بصرية بس
    //    (مش بتتخزّن)، فمفيش داعي تتحوّل لصفحة.
    var boxStart by remember { mutableStateOf<Offset?>(null) }
    var boxEnd by remember { mutableStateOf<Offset?>(null) }

    // ── حالة سحب رأس — نسخة محلية من رؤوس البند المحدّد بيتحرّك رأس
    //    فيها لحد ما يرفع إصبعه، وقتها بس بتتحفظ.
    var vertexItemId by remember { mutableStateOf<Long?>(null) }
    var vertexTarget by remember { mutableStateOf<TakeoffVertexTarget?>(null) }
    val vertexPoints = remember { mutableStateListOf<TakeoffPoint>() }
    /** آخر رأس اتلمس في وضع تعديل الرؤوس — بيفضل بعد ما السحب يخلص عشان
     *  زرار "احذف الرأس" يعرف يشتغل على إيه. */
    var vertexFocusTarget by remember { mutableStateOf<TakeoffVertexTarget?>(null) }

    fun clearDrafts() {
        v2Controller.cancelMeasurement()
        pendingV2Measurement = null
        inkMode = false
        draft.clear()
        draftPage.intValue = -1
        dimDragPoint = null
        calibPoints.clear()
        calibrating = false
        deductFor = null
        addToShapeFor = null
        // بند اتسمّى وحجز ID بس اتسابته (المستخدم بدّل أداة أو خرج) —
        // بيترسم مالوش هندسة، فمالوش لزمة يفضل شبح جوّه القاعدة.
        pendingDrawItemId?.let { id -> scope.launch { vm.takeoff.deleteItem(id) } }
        pendingDrawItemId = null
        rectDraft = null
        rectPage = -1
        boxStart = null
        boxEnd = null
        vertexItemId = null
        vertexTarget = null
        vertexPoints.clear()
        vertexFocusTarget = null
        annotationTool = null
        annotationDraft.clear()
        annotationDraftPage = -1
        textPromptPoint = null
        textPromptPage = -1
        measurementNotice = null
    }

    fun endSession() {
        // "الخروج الشامل" من الفخ رقم ٥: مسوّدة، تحديد، وضع، خصم — كلهم.
        clearDrafts()
        mode = EditorMode.POINTER
        selectedId = null
        selectedAnnotationId = null
        multiSelectedIds = emptySet()
    }

    fun saveNewItem(
        toolToSave: TakeoffTool, page: Int, points: List<TakeoffPoint>,
        name: String, categoryId: Long?, colorArgb: Long,
        thickness: Double? = null, colLength: Double? = null,
        colWidth: Double? = null, colHeight: Double? = null
    ) {
        scope.launch {
            val id = vm.takeoff.saveItem(
                TakeoffItemEntity(
                    drawingId = drawingId,
                    page = page,
                    tool = toolToSave.name,
                    name = name,
                    colorArgb = colorArgb,
                    pointsJson = vm.takeoff.encodeRing(points),
                    categoryId = categoryId,
                    thickness = thickness,
                    colLength = colLength,
                    colWidth = colWidth,
                    colHeight = colHeight,
                    createdAt = System.currentTimeMillis()
                )
            )
            lastUndo = UndoAction("رسم \"$name\"") { vm.takeoff.deleteItem(id) }
        }
    }

    /** يحفظ الجزء الهندسي المستهدف فقط، مع إبقاء بقية حلقات وقطاعات البند كما هي. */
    fun persistVertexPart(
        itemId: Long,
        target: TakeoffVertexTarget,
        points: List<TakeoffPoint>,
        undoLabel: String
    ) {
        scope.launch {
            vm.takeoff.itemById(itemId)?.let { row ->
                val updated = TakeoffMath.withVertices(vm.takeoff.toModel(row), target, points)
                val persisted = when (target.part) {
                    TakeoffGeometryPart.PRIMARY -> row.copy(pointsJson = vm.takeoff.encodeRing(updated.verts))
                    TakeoffGeometryPart.EXTRA_RING -> row.copy(extraRingsJson = vm.takeoff.encodeRings(updated.extraRings))
                    TakeoffGeometryPart.EXTRA_SEGMENT -> row.copy(extraSegmentsJson = vm.takeoff.encodeRings(updated.extraSegments))
                }
                vm.takeoff.saveItem(persisted)
                // `row` = اللقطة قبل التعديل، فالتراجع بيرجّع الشكل زي ما كان
                // بالظبط — سواء كان التعديل على الشكل الأساسي أو حلقة مضافة.
                lastUndo = UndoAction("$undoLabel \"${row.name}\"") { vm.takeoff.saveItem(row) }
            }
        }
    }

    fun commit() {
        val page = draftPage.intValue
        if (page < 0 || draft.isEmpty()) {
            measurementNotice = "ضع نقاط القياس أولاً"
            return
        }

        val points = draft.toList()
        val enough = TakeoffMath.canCommitMeasurement(tool, points.size)
        if (!enough) {
            measurementNotice = when (tool) {
                TakeoffTool.LENGTH -> "الطول يحتاج نقطتين على الأقل"
                TakeoffTool.DIMENSION -> "البُعد يحتاج طرفين وإزاحة"
                else -> "المساحة تحتاج ثلاث نقاط على الأقل"
            }
            return
        }
        measurementNotice = null

        // البُعد مرجع بصري — زي الخصم بالظبط، بدون اسم ولا فئة ولون ثابت،
        // ومستبعد من الإجماليات والـBOQ ومراجع الصيغ بحكم [TakeoffTool.isQuantity].
        if (tool == TakeoffTool.DIMENSION) {
            saveNewItem(
                TakeoffTool.DIMENSION, page, points,
                defaultName(TakeoffTool.DIMENSION, rows.size + 1), null, DIMENSION_COLOR
            )
            draft.clear(); draftPage.intValue = -1
            return
        }

        val parent = deductFor
        if (parent != null) {
            // الخصم مالوش اسم ولا فئة يختارهم المستخدم — مربوط بأبوه
            // وبطلع رمادي دايمًا، زي ما كان قبل التصنيفات.
            scope.launch {
                val id = vm.takeoff.saveItem(
                    TakeoffItemEntity(
                        drawingId = drawingId,
                        page = page,
                        tool = TakeoffTool.DEDUCT.name,
                        name = defaultName(TakeoffTool.DEDUCT, rows.size + 1),
                        colorArgb = 0xFF9E9E9EL,
                        pointsJson = vm.takeoff.encodeRing(points),
                        parentId = parent.id.toLongOrNull(),
                        createdAt = System.currentTimeMillis()
                    )
                )
                lastUndo = UndoAction("خصم من \"${parent.name}\"") { vm.takeoff.deleteItem(id) }
            }
            draft.clear(); draftPage.intValue = -1; deductFor = null
            return
        }

        // إضافة لشكل موجود — حلقة أو قطعة تانية بتتلحق بنفس البند، مش بند
        // جديد. بنقرا آخر نسخة من `items` وقت الحفظ نفسه، مش النسخة اللي
        // كانت موجودة وقت الضغط على "أضف للشكل" — عشان أي تعديل حصل في
        // الفترة دي مايتلغيش.
        val addTarget = addToShapeFor
        if (addTarget != null) {
            val itemId = addTarget.id.toLongOrNull()
            val live = items.firstOrNull { it.id == addTarget.id }
            if (itemId != null && live != null) {
                scope.launch {
                    vm.takeoff.itemById(itemId)?.let { row ->
                        // `row` نفسها اللقطة اللي قبل التعديل — التراجع
                        // بيرجّعها زي ما هي من غير ما يحتاج يحسب رياضيًا
                        // إيه اتضاف.
                        val updated = if (addTarget.tool == TakeoffTool.LENGTH) {
                            row.copy(extraSegmentsJson = vm.takeoff.encodeRings(live.extraSegments + listOf(points)))
                        } else {
                            row.copy(extraRingsJson = vm.takeoff.encodeRings(live.extraRings + listOf(points)))
                        }
                        vm.takeoff.saveItem(updated)
                        lastUndo = UndoAction("إضافة لـ \"${row.name}\"") { vm.takeoff.saveItem(row) }
                    }
                }
            }
            draft.clear(); draftPage.intValue = -1; addToShapeFor = null
            return
        }

        // الاسم والفئة واللون اتاخدوا **قبل** ما الرسم يبدأ (شوف `pickTool`)
        // — البند أصلاً محجوز له صف في القاعدة، وده بس بيحطّ هندسته فيه.
        val placeholderId = pendingDrawItemId
        if (placeholderId != null) {
            scope.launch {
                val row = vm.takeoff.itemById(placeholderId)
                if (row != null) {
                    vm.takeoff.saveItem(row.copy(page = page, pointsJson = vm.takeoff.encodeRing(points)))
                    lastUndo = UndoAction("رسم \"${row.name}\"") { vm.takeoff.deleteItem(placeholderId) }
                } else {
                    saveNewItem(
                        toolToSave = tool,
                        page = page,
                        points = points,
                        name = defaultName(tool, rows.size + 1),
                        categoryId = null,
                        colorArgb = TAKEOFF_PALETTE[colourIndex % TAKEOFF_PALETTE.size]
                    )
                }
            }
        } else {
            // لا يجوز أن تختفي كمية صحيحة لو أُلغي حجز البند بسبب انتقال
            // حالة سريع؛ ننشئ بنداً آمناً باسم افتراضي ونحفظ هندسته فوراً.
            saveNewItem(
                toolToSave = tool,
                page = page,
                points = points,
                name = defaultName(tool, rows.size + 1),
                categoryId = null,
                colorArgb = TAKEOFF_PALETTE[colourIndex % TAKEOFF_PALETTE.size]
            )
        }
        pendingDrawItemId = null
        draft.clear(); draftPage.intValue = -1
    }

    /**
     * أقرب رأس موجود بالفعل على نفس الصفحة، لو جوّه نصف قطر الالتقاط.
     *
     * بيفحص `verts` بس (مش الحلقات المتجمّعة) — نفس القيد اللي في تعديل
     * الرؤوس، ولنفس السبب: التجميع مالوش واجهة تضيف له لسه.
     *
     * بيشتغل بس على [addPoint] (رسم بالنقر) — سحب المستطيل وسحب الرأس
     * مش ملتقطين لسه، عشان الالتقاط أثناء السحب المستمر محتاج مؤشّر
     * بصري ("هيتلقط هنا") من غير ده بيحس المستخدم إن الشكل بيرتعش.
     */
    fun snapPoint(p: TakeoffPoint, page: Int): TakeoffPoint {
        if (!snapEnabled) return p
        var best: TakeoffPoint? = null
        var bestDist = Double.MAX_VALUE
        for (candidate in pageItems) {
            if (candidate.page != page || !candidate.visible) continue
            val vertices = buildList {
                addAll(candidate.verts)
                candidate.extraRings.forEach { addAll(it) }
                candidate.extraSegments.forEach { addAll(it) }
            }
            vertices.forEach { v ->
                val d = hypot((p.x - v.x) * pageGeometry.widthPt, (p.y - v.y) * pageGeometry.heightPt)
                if (d < bestDist) { bestDist = d; best = v }
            }
        }
        return if (bestDist <= settings.takeoffSnapRadiusPt) best ?: p else p
    }

    fun addPoint(screen: Offset) {
        // البُعد بس اثنين من اللمسات (الطرفين) — النقطة التالتة (إزاحة خط
        // القياس) سحب مستمر مش لمسة، وبتتولّى من `onDrawStart/Move/End`
        // بمجرّد ما `draft.size` توصل ٢ (شوف `drawingActive` تحت).
        if (tool == TakeoffTool.DIMENSION && draft.size >= 2) return
        val hit = state.pageHit(screen) ?: return
        if (draftPage.intValue < 0) draftPage.intValue = hit.page
        // لمسة على صفحة تانية وأنت في نص شكل بتتجاهل — البند بيخص صفحة
        // واحدة، وشكل بيعدّي بين صفحتين مالوش معنى في الحصر.
        if (hit.page != draftPage.intValue) return
        draft += snapPoint(TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble()), hit.page)
        measurementNotice = null
    }

    fun finishRect(a: TakeoffPoint, b: TakeoffPoint, page: Int) {
        val minX = minOf(a.x, b.x); val maxX = maxOf(a.x, b.x)
        val minY = minOf(a.y, b.y); val maxY = maxOf(a.y, b.y)
        // مستطيل أصغر من نص بكسل عمليًا = لمسة غلط مش شكل مقصود.
        if (maxX - minX < 0.002 || maxY - minY < 0.002) return
        val points = listOf(
            TakeoffPoint(minX, minY), TakeoffPoint(maxX, minY),
            TakeoffPoint(maxX, maxY), TakeoffPoint(minX, maxY)
        )
        val placeholderId = pendingDrawItemId
        if (placeholderId != null) {
            scope.launch {
                vm.takeoff.itemById(placeholderId)?.let { row ->
                    vm.takeoff.saveItem(row.copy(page = page, pointsJson = vm.takeoff.encodeRing(points)))
                    lastUndo = UndoAction("رسم \"${row.name}\"") { vm.takeoff.deleteItem(placeholderId) }
                }
            }
        }
        pendingDrawItemId = null
    }

    /**
     * اختيار أداة — بيسأل عن الاسم/الفئة/اللون **الأول**، وبعدين يدخل
     * وضع الرسم. البُعد وحده مستثنى: مالوش اسم ولا فئة أصلاً (زي الخصم
     * بالظبط)، فمفيش لزمة يوقف يسأل.
     */
    fun pickTool(picked: TakeoffTool, viaRect: Boolean = false) {
        clearDrafts()
        // العدّ والأعمدة لا يحتاجان مقياساً لأنهما علامات؛ أما بقية الأدوات
        // فتنتج طولاً أو مساحة أو حجماً، لذلك نبدأ بالمعايرة بدلاً من حفظ
        // كمية صفرية أو غير موثوقة.
        val needsScale = picked != TakeoffTool.COUNT && picked != TakeoffTool.COLUMN
        if (needsScale && !pageGeometry.calibrated) {
            calibrating = true
            return
        }
        if (picked == TakeoffTool.DIMENSION) {
            mode = EditorMode.DRAW
            tool = picked
        } else {
            pendingNaming = PendingNaming(picked, viaRect)
        }
    }

    /**
     * قفزة من لوحة القياسات/الفئات لبند بعينه — بتروح لصفحته، تقرّب على
     * صندوقه المحيط (مش الصفحة كلها)، وتحدّده. مقصود إنها تلغي أي أمر
     * شغّال الأول (زي زرار اليد بالظبط) عشان مفيش قفزة تحصل نص رسم شكل.
     */
    fun focusItem(item: TakeoffItem) {
        clearDrafts()
        mode = EditorMode.POINTER
        multiSelectedIds = emptySet()
        selectedAnnotationId = null
        state.goToPage(item.page, animateZoom = false)
        val slot = state.layout.slotAt(item.page)
        val allPts = item.verts + item.extraRings.flatten() + item.extraSegments.flatten()
        if (slot != null && allPts.isNotEmpty()) {
            val minX = allPts.minOf { it.x }.toFloat()
            val maxX = allPts.maxOf { it.x }.toFloat()
            val minY = allPts.minOf { it.y }.toFloat()
            val maxY = allPts.maxOf { it.y }.toFloat()
            val rMinX = slot.left + minX * slot.size.width
            val rMinY = slot.top + minY * slot.size.height
            val rMaxX = slot.left + maxX * slot.size.width
            val rMaxY = slot.top + maxY * slot.size.height
            // نقطة واحدة (عدّ/عمود بعلامة وحيدة) بتديك صندوق بلا مساحة —
            // بندي له نصف قطر بسيط عشان zoomToRect ميتجاهلوش.
            val pad = maxOf(slot.size.width, slot.size.height) * 0.06f
            val rect = if (rMaxX - rMinX < 1f || rMaxY - rMinY < 1f) {
                val cx = (rMinX + rMaxX) / 2f
                val cy = (rMinY + rMaxY) / 2f
                Rect(cx - pad, cy - pad, cx + pad, cy + pad)
            } else {
                Rect(rMinX - pad, rMinY - pad, rMaxX + pad, rMaxY + pad)
            }
            state.zoomToRect(rect, paddingPx = 80f)
        }
        selectedId = item.id
    }

    // طلب "روح للقياس ده" جاي من شاشة البيانات المنفصلة. بيستنى لحد ما
    // مقاسات الصفحات تتعرف (`measured`) عشان `zoomToRect` يحسب صح، وبيتستهلك
    // فورًا بعد التنفيذ عشان مايتكررش مع كل إعادة تركيب.
    val focusRequest by vm.takeoff.pendingFocusItemId.collectAsStateWithLifecycle()
    LaunchedEffect(focusRequest, items, measured) {
        val target = focusRequest ?: return@LaunchedEffect
        val wanted = items.firstOrNull { it.id == target.toString() } ?: return@LaunchedEffect
        focusItem(wanted)
        vm.takeoff.consumeFocusRequest()
    }

    fun saveAnnotation(type: TakeoffAnnotationType, page: Int, points: List<TakeoffPoint>, text: String = "") {
        scope.launch {
            vm.takeoff.saveAnnotation(
                TakeoffAnnotationEntity(
                    drawingId = drawingId,
                    page = page,
                    type = type.name,
                    pointsJson = vm.takeoff.encodeRing(points),
                    colorArgb = ANNOTATION_COLOR,
                    text = text,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** بتقفل السحابة أو السهم لو عنده نقط كفاية، وبترمي المسوّدة برضو لو لأ. */
    fun finishAnnotation() {
        val points = annotationDraft.toList()
        val page = annotationDraftPage
        val type = annotationTool
        if (type != null && page >= 0) {
            val enough = when (type) {
                TakeoffAnnotationType.CLOUD -> points.size >= 3
                TakeoffAnnotationType.ARROW -> points.size >= 2
                TakeoffAnnotationType.TEXT -> false // بتتحفظ من نافذة النص مباشرة، مش من هنا
                TakeoffAnnotationType.INK -> false // قلم V2 يحفظ كل خط مباشرة عند رفع S Pen
            }
            if (enough) saveAnnotation(type, page, points)
        }
        annotationDraft.clear()
        annotationDraftPage = -1
    }

    /** توجيه اللمسة حسب نوع التعليق النشط — بديل [addPoint] وقت التعليق. */
    fun handleAnnotationTap(screen: Offset, type: TakeoffAnnotationType) {
        val hit = state.pageHit(screen) ?: return
        if (annotationDraftPage < 0) annotationDraftPage = hit.page
        if (hit.page != annotationDraftPage) return
        val p = snapPoint(TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble()), hit.page)
        when (type) {
            TakeoffAnnotationType.TEXT -> {
                textPromptPoint = p
                textPromptPage = hit.page
            }
            TakeoffAnnotationType.ARROW -> {
                annotationDraft += p
                if (annotationDraft.size == 2) finishAnnotation()
            }
            TakeoffAnnotationType.CLOUD -> annotationDraft += p
            TakeoffAnnotationType.INK -> Unit
        }
    }

    fun undoLastDraftPoint() {
        when {
            draft.isNotEmpty() -> draft.removeAt(draft.lastIndex)
            annotationDraft.isNotEmpty() -> annotationDraft.removeAt(annotationDraft.lastIndex)
        }
    }

    // ── الحسابات المشتقّة من الوضع
    /**
     * ملحوظة معروفة: طبقة السحب دي مش بتفرّق بين القلم والصباع (بعكس
     * طبقة النقر اللي بتحترم "وضع القلم فقط" عن طريق `gestureAccept`).
     * يعني المستطيل وتعديل الرؤوس والتحديد بمستطيل التلاتة بيشتغلوا
     * بأي مؤشّر حتى لو وضع القلم فقط شغّال. أدوات نادرة الاستخدام نسبيًا،
     * فأجّلنا الإصلاح ده بدل ما نضيف تعقيد لمرحلة أولى.
     */
    val drawingActive = mode == EditorMode.RECT || mode == EditorMode.BOXSELECT ||
        (mode == EditorMode.VERTEX && selectedId != null) ||
        (mode == EditorMode.DRAW && tool == TakeoffTool.DIMENSION && draft.size == 2)
    val penHasJob = settings.stylusOnly && mode != EditorMode.POINTER
    val activeColour = Color(
        (TAKEOFF_PALETTE[colourIndex % TAKEOFF_PALETTE.size] or 0xFF000000L).toInt()
    )
    val calibrationColour = c.accent
    val draftPoints = draft.toList()
    val activeDraftTool = if (deductFor != null) TakeoffTool.DEDUCT else tool
    val selectedV2Tool = tool.toV2WorkspaceTool()
    val usesV2Measurement = mode == EditorMode.DRAW &&
        !calibrating &&
        annotationTool == null &&
        deductFor == null &&
        addToShapeFor == null &&
        selectedV2Tool != null &&
        pendingV2Measurement?.tool == tool
    val usesV2Workspace = usesV2Measurement || inkMode
    val activeV2Tool = if (inkMode) V2WorkspaceTool.INK else selectedV2Tool
    val v2PersistedItems = remember(pageItems, pageGeometry) {
        pageItems.mapNotNull { item ->
            item.tool.toV2WorkspaceTool()?.let {
                V2PersistedTakeoffItem(
                    id = item.id.toLongOrNull() ?: Long.MIN_VALUE,
                    page = item.page,
                    tool = item.tool,
                    points = item.verts.map { point -> V2DocumentPoint(point.x.toFloat(), point.y.toFloat()) },
                    extraRings = item.extraRings.map { ring ->
                        ring.map { point -> V2DocumentPoint(point.x.toFloat(), point.y.toFloat()) }
                    },
                    extraSegments = item.extraSegments.map { segment ->
                        segment.map { point -> V2DocumentPoint(point.x.toFloat(), point.y.toFloat()) }
                    },
                    colorArgb = item.colorArgb,
                    visible = item.visible,
                    calibration = V2PageCalibration(
                        metresPerPoint = pageGeometry.metresPerPoint,
                        thicknessMetres = item.thickness
                    )
                )
            }
        }
    }
    val v2PersistedInk = remember(pageAnnotations) {
        pageAnnotations.filter { it.type == TakeoffAnnotationType.INK }.mapNotNull { annotation ->
            annotation.id.toLongOrNull()?.let { annotationId ->
                V2PersistedInkStroke(
                    annotationId = annotationId,
                    page = annotation.page,
                    points = annotation.verts.map { point -> V2DocumentPoint(point.x.toFloat(), point.y.toFloat()) },
                    widthPx = annotation.text.toFloatOrNull()?.coerceIn(1.2f, 16f) ?: 3.4f,
                    colorArgb = annotation.colorArgb,
                    visible = annotation.visible
                )
            }
        }
    }
    LaunchedEffect(usesV2Workspace, activeV2Tool, pageGeometry, pendingV2Measurement, inkColorArgb, inkWidthPx) {
        if (usesV2Workspace && activeV2Tool != null) {
            v2Controller.selectTool(activeV2Tool)
            v2Controller.setCountCommitImmediately(false)
            v2Controller.setCalibration(
                V2PageCalibration(
                    metresPerPoint = pageGeometry.metresPerPoint,
                    thicknessMetres = pendingV2Measurement?.thicknessMetres
                )
            )
            v2Controller.setInkStyle(V2InkStyle(inkColorArgb, inkWidthPx))
        }
    }
    val liveReadout = measurementNotice ?: when {
        draftPoints.isEmpty() -> null
        activeDraftTool == TakeoffTool.COUNT || activeDraftTool == TakeoffTool.COLUMN ->
            "${if (activeDraftTool == TakeoffTool.COUNT) "عد" else "أعمدة"}: ${draftPoints.size}"
        activeDraftTool == TakeoffTool.LENGTH ->
            "طول حي: ${formatQuantity(TakeoffTool.LENGTH, TakeoffMath.length(draftPoints, pageGeometry))}"
        activeDraftTool == TakeoffTool.DIMENSION && draftPoints.size >= 2 ->
            "بُعد حي: ${formatQuantity(TakeoffTool.DIMENSION, TakeoffMath.length(draftPoints.take(2), pageGeometry))}"
        (activeDraftTool == TakeoffTool.AREA || activeDraftTool == TakeoffTool.DEDUCT || activeDraftTool == TakeoffTool.VOLUME) && draftPoints.size >= 3 ->
            "مساحة حية: ${formatQuantity(TakeoffTool.AREA, TakeoffMath.area(draftPoints, pageGeometry))}"
        else -> "ضع النقطة التالية (${draftPoints.size})"
    }
    val displayedReadout = if (inkMode) {
        "قلم S Pen — الإصبع للتنقل، وطرف الممحاة يحذف الخطوط"
    } else if (usesV2Workspace) {
        measurementNotice ?: "ارسم بالقلم ثم اضغط إنهاء للحفظ"
    } else {
        liveReadout
    }

    fun finishV2Measurement() {
        val pending = pendingV2Measurement ?: return
        val finished = v2Controller.finishMeasurement()
        val finishedRecordId = (finished as? V2MeasurementFinishResult.Saved)?.record?.id
        scope.launch {
            when (val result = v2CommitCoordinator.persist(
                finish = finished,
                name = pending.name,
                colorArgb = pending.colorArgb,
                categoryId = pending.categoryId
            )) {
                is V2TakeoffCommitResult.Persisted -> {
                    finishedRecordId?.let(v2Controller::acknowledgeMeasurementPersisted)
                    pendingV2Measurement = null
                    measurementNotice = null
                    mode = EditorMode.POINTER
                }
                is V2TakeoffCommitResult.Incomplete -> measurementNotice = result.message
                V2TakeoffCommitResult.NothingToSave -> measurementNotice = "ارسم بالقلم أولاً قبل الحفظ"
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = c.background) {
        Box(Modifier.fillMaxSize()) {
            if (usesV2Workspace) {
                V2PdfWorkspaceHost(
                    session = active,
                    page = state.currentPage,
                    modifier = Modifier.fillMaxSize(),
                    controller = v2Controller,
                    persistedItems = v2PersistedItems,
                    persistedInk = v2PersistedInk,
                    activeMeasurementColorArgb = pendingV2Measurement?.colorArgb ?: TAKEOFF_PALETTE[colourIndex % TAKEOFF_PALETTE.size],
                    commitCountsImmediately = false,
                    inkStyle = V2InkStyle(inkColorArgb, inkWidthPx),
                    onInkCommitted = { stroke ->
                        scope.launch {
                            vm.takeoff.saveAnnotation(
                                TakeoffAnnotationEntity(
                                    drawingId = drawingId,
                                    page = stroke.page,
                                    type = TakeoffAnnotationType.INK.name,
                                    pointsJson = vm.takeoff.encodeRing(
                                        stroke.points.map { point -> TakeoffPoint(point.x.toDouble(), point.y.toDouble()) }
                                    ),
                                    colorArgb = stroke.colorArgb,
                                    text = stroke.widthPx.toString(),
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            v2Controller.acknowledgeInkPersisted(stroke.id)
                        }
                    },
                    onPersistedInkErased = { annotationId ->
                        scope.launch {
                            // اللقطة قبل المسح: الممحاة بتشيل الخط من القاعدة
                            // نهائيًا، فمن غير دي مسحة غلط مالهاش رجعة.
                            val row = vm.takeoff.annotationById(annotationId)
                            vm.takeoff.deleteAnnotation(annotationId)
                            if (row != null) {
                                lastUndo = UndoAction("مسح تعليق") { vm.takeoff.saveAnnotation(row); Unit }
                            }
                        }
                    }
                )
            } else PdfCanvas(
                state = state,
                engine = engine,
                session = active,
                drawingActive = drawingActive,
                gestureAccept = remember(penHasJob) {
                    { kind: PointerKind -> !(penHasJob && kind.isPen) }
                },
                onDrawStart = { screen ->
                    when (mode) {
                        EditorMode.RECT -> {
                            state.pageHit(screen)?.let { hit ->
                                rectPage = hit.page
                                val p = TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble())
                                rectDraft = p to p
                            }
                        }
                        EditorMode.BOXSELECT -> { boxStart = screen; boxEnd = screen }
                        EditorMode.VERTEX -> {
                            val item = pageItems.firstOrNull { it.id == selectedId }
                            val hit = state.pageHit(screen)
                            if (item != null && hit != null && hit.page == item.page) {
                                val p = TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble())
                                val existingTarget = TakeoffMath.nearestVertexTarget(
                                    item, p, pageGeometry, tapRadiusPt = 16.0
                                )
                                val target = existingTarget ?: TakeoffMath.nearestEdgeInsertTarget(
                                    item, p, pageGeometry, radiusPt = 16.0
                                )
                                if (target != null) {
                                    vertexItemId = item.id.toLongOrNull()
                                    vertexTarget = target
                                    vertexPoints.clear()
                                    vertexPoints.addAll(TakeoffMath.verticesFor(item, target))
                                    if (existingTarget == null) {
                                        vertexPoints.add(target.vertexIndex, p)
                                    }
                                    vertexFocusTarget = target
                                }
                            }
                        }
                        EditorMode.DRAW -> {
                            // خط قياس بس — طرفاه بلمستين عاديتين، وده سحب نقطة
                            // الإزاحة التالتة بعدهم على طول ("لوره أو أدّام").
                            if (tool == TakeoffTool.DIMENSION && draft.size == 2) {
                                state.pageHit(screen)?.let { hit ->
                                    if (hit.page == draftPage.intValue) {
                                        dimDragPoint = TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble())
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                },
                onDrawMove = { screen ->
                    when (mode) {
                        EditorMode.RECT -> {
                            val start = rectDraft?.first
                            val hit = state.pageHit(screen)
                            if (start != null && hit != null && hit.page == rectPage) {
                                rectDraft = start to TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble())
                            }
                        }
                        EditorMode.BOXSELECT -> { boxEnd = screen }
                        EditorMode.VERTEX -> {
                            val idx = vertexTarget?.vertexIndex
                            val hit = state.pageHit(screen)
                            if (idx != null && hit != null && idx < vertexPoints.size) {
                                vertexPoints[idx] = TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble())
                            }
                        }
                        EditorMode.DRAW -> {
                            if (tool == TakeoffTool.DIMENSION && draft.size == 2) {
                                state.pageHit(screen)?.let { hit ->
                                    if (hit.page == draftPage.intValue) {
                                        dimDragPoint = TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble())
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                },
                onDrawEnd = {
                    when (mode) {
                        EditorMode.RECT -> {
                            val draft2 = rectDraft
                            if (draft2 != null) finishRect(draft2.first, draft2.second, rectPage)
                            rectDraft = null; rectPage = -1
                        }
                        EditorMode.BOXSELECT -> {
                            val s = boxStart; val e = boxEnd
                            if (s != null && e != null) {
                                val hs = state.pageHit(s)
                                val he = state.pageHit(e)
                                if (hs != null && he != null && hs.page == he.page) {
                                    val minP = TakeoffPoint(
                                        minOf(hs.nx, he.nx).toDouble(), minOf(hs.ny, he.ny).toDouble()
                                    )
                                    val maxP = TakeoffPoint(
                                        maxOf(hs.nx, he.nx).toDouble(), maxOf(hs.ny, he.ny).toDouble()
                                    )
                                    multiSelectedIds = pageItems
                                        .filter { it.page == hs.page && TakeoffMath.crossesBox(it, minP, maxP) }
                                        .map { it.id }.toSet()
                                }
                            }
                            boxStart = null; boxEnd = null
                        }
                        EditorMode.VERTEX -> {
                            val itemId = vertexItemId
                            val target = vertexTarget
                            if (itemId != null && target != null && vertexPoints.isNotEmpty()) {
                                persistVertexPart(itemId, target, vertexPoints.toList(), "تعديل رؤوس")
                            }
                            vertexItemId = null; vertexTarget = null; vertexPoints.clear()
                        }
                        EditorMode.DRAW -> {
                            if (tool == TakeoffTool.DIMENSION && draft.size == 2) {
                                dimDragPoint?.let { draft += it }
                                dimDragPoint = null
                                commit()
                            }
                        }
                        else -> Unit
                    }
                },
                onDrawCancel = {
                    rectDraft = null; rectPage = -1
                    boxStart = null; boxEnd = null
                    vertexItemId = null; vertexTarget = null; vertexPoints.clear()
                    dimDragPoint = null
                },
                onTap = { point, kind ->
                    val penOnly = settings.stylusOnly && mode != EditorMode.POINTER
                    when {
                        // في وضع القلم، الصباع بيتنقّل بس — مايرسمش ولا يعاير.
                        penOnly && !kind.isPen -> Unit
                        calibrating -> {
                            state.pageHit(point)?.let { hit ->
                                if (calibPoints.size < 2) {
                                    calibPoints += TakeoffPoint(hit.nx.toDouble(), hit.ny.toDouble())
                                }
                            }
                        }
                        // التعليقات طبقة مستقلة — بتاخد الأولوية على وضع
                        // النقر العادي، وبتفضل شغّالة أيًا كان `mode`.
                        annotationTool != null -> handleAnnotationTap(point, annotationTool!!)
                        mode == EditorMode.POINTER || mode == EditorMode.VERTEX -> {
                            val hit = state.pageHit(point)
                            val p = hit?.let { h -> TakeoffPoint(h.nx.toDouble(), h.ny.toDouble()) }
                            selectedId = p?.let { pt ->
                                // من الآخر للأول: الشكل اللي اترسم بعدين هو
                                // اللي فوق بصرياً، فلازم يكسب اللمسة.
                                pageItems.lastOrNull { candidate ->
                                    candidate.visible && TakeoffMath.hitTest(
                                        candidate, pt, pageGeometry, tapRadiusPt = 14.0
                                    )
                                }?.id
                            }
                            multiSelectedIds = emptySet()
                            // لمسة ماخدتش بند حصر — جرّب تلاقيها على تعليق
                            // بدل ما تسيب المستخدم بلا طريقة يشيله بيها.
                            selectedAnnotationId = if (selectedId == null && p != null) {
                                pageAnnotations.lastOrNull { it.visible && annotationHit(it, p, pageGeometry) }?.id
                            } else null
                        }
                        mode == EditorMode.DRAW -> addPoint(point)
                        else -> Unit
                    }
                },
                onLongPress = { _, _ ->
                    if (annotationTool == TakeoffAnnotationType.CLOUD) finishAnnotation()
                    else if (mode == EditorMode.DRAW) commit()
                },
                overlay = { s ->
                    val renderItems = if (vertexItemId != null && vertexTarget != null && vertexPoints.isNotEmpty()) {
                        val draggedId = vertexItemId.toString()
                        val target = vertexTarget!!
                        pageItems.map {
                            if (it.id == draggedId) TakeoffMath.withVertices(it, target, vertexPoints.toList()) else it
                        }
                    } else pageItems
                    drawTakeoffItems(
                        state = s,
                        items = renderItems,
                        page = s.currentPage,
                        deductionsOf = { parent ->
                            renderItems.filter {
                                it.tool == TakeoffTool.DEDUCT && it.parentId == parent.id
                            }
                        },
                        // كل عناصر renderItems أصلاً من نفس الصفحة الحالية
                        // (مفلترة قبل كده)، فمفيش داعي نبحث هندسة صفحة كل بند.
                        netQuantityOf = { it2 -> TakeoffMath.netQuantity(it2, renderItems, pageGeometry) },
                        selectedId = selectedId,
                        multiSelectedIds = multiSelectedIds,
                        strokeWidth = settings.takeoffStrokeWidth,
                        markerRadius = settings.takeoffMarkerRadius,
                        textScale = settings.takeoffTextScale
                    )
                    drawTakeoffAnnotations(
                        state = s, annotations = pageAnnotations, page = s.currentPage,
                        selectedId = selectedAnnotationId
                    )
                    if (annotationDraft.isNotEmpty() && annotationDraftPage >= 0) {
                        drawTakeoffAnnotationDraft(
                            state = s, page = annotationDraftPage,
                            points = annotationDraft.toList(),
                            colour = Color((ANNOTATION_COLOR or 0xFF000000L).toInt())
                        )
                    }
                    // نقطتا المعايرة لازم تبانوا وهو بيحطهم — من غير ده
                    // المستخدم مش عارف إذا كانت لمسته اتسجّلت ولا لأ.
                    if (calibrating && calibPoints.isNotEmpty()) {
                        drawTakeoffDraft(
                            state = s,
                            page = s.currentPage,
                            points = calibPoints.toList(),
                            tool = TakeoffTool.LENGTH,
                            colour = calibrationColour,
                            strokeWidth = settings.takeoffStrokeWidth
                        )
                    }
                    val placingDimOffset = tool == TakeoffTool.DIMENSION && draft.size == 2 && dimDragPoint != null
                    if (draft.isNotEmpty() && draftPage.intValue >= 0 && !placingDimOffset) {
                        drawTakeoffDraft(
                            state = s,
                            page = draftPage.intValue,
                            points = draft.toList(),
                            tool = if (deductFor != null) TakeoffTool.DEDUCT else tool,
                            colour = activeColour,
                            strokeWidth = settings.takeoffStrokeWidth,
                            markerRadius = settings.takeoffMarkerRadius
                        )
                    }
                    // معاينة حية لخط القياس وهو بيتسحب — بالشكل النهائي بالظبط
                    // (خطوط امتداد + سهمين + الرقم)، مش خط بسيط زي أي مسوّدة
                    // تانية، عشان المستخدم يشوف بالظبط فين هيتحط قبل ما يسيب إصبعه.
                    if (placingDimOffset && draftPage.intValue >= 0) {
                        val liveLength = TakeoffMath.length(draft.toList(), pageGeometry)
                        drawDimension(
                            state = s, page = draftPage.intValue,
                            p1 = draft[0], p2 = draft[1], pOffset = dimDragPoint!!,
                            text = formatQuantity(TakeoffTool.DIMENSION, liveLength),
                            color = activeColour
                        )
                    }
                    rectDraft?.let { (a, b) ->
                        drawTakeoffDraft(
                            state = s, page = rectPage,
                            points = listOf(
                                a, TakeoffPoint(b.x, a.y), b, TakeoffPoint(a.x, b.y)
                            ),
                            tool = TakeoffTool.AREA, colour = activeColour,
                            strokeWidth = settings.takeoffStrokeWidth
                        )
                    }
                    val bs = boxStart; val be = boxEnd
                    if (bs != null && be != null) {
                        drawRect(
                            color = c.accent,
                            topLeft = Offset(minOf(bs.x, be.x), minOf(bs.y, be.y)),
                            size = Size(abs(be.x - bs.x), abs(be.y - bs.y)),
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                            )
                        )
                    }
                }
            )

            TakeoffTopBar(
                title = when {
                    calibrating -> "عاير المقياس"
                    addToShapeFor != null -> "إضافة لـ: ${addToShapeFor?.name}"
                    else -> null
                },
                page = state.currentPage + 1,
                pageCount = active.pageCount,
                onBack = onClose,
                onPreviousPage = {
                    endSession()
                    state.goToPage(state.currentPage - 1)
                },
                onNextPage = {
                    endSession()
                    state.goToPage(state.currentPage + 1)
                },
                onTotals = { totalsOpen = true },
                onCalibrate = { endSession(); calibrating = true },
                onSettings = { settingsOpen = true },
                onTree = { vm.openTakeoffData(drawingId, drawingName) },
                onFormulas = { formulasOpen = true },
                undoLabel = lastUndo?.label,
                onUndo = {
                    val action = lastUndo
                    if (action != null) {
                        lastUndo = null
                        scope.launch { action.perform() }
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            TakeoffFloatingZoomControls(
                onZoomIn = {
                    if (usesV2Workspace) v2Controller.zoomBy(1.25f)
                    else state.zoomBy(1.25f, Offset(state.viewport.width / 2f, state.viewport.height / 2f))
                },
                onZoomOut = {
                    if (usesV2Workspace) v2Controller.zoomBy(0.8f)
                    else state.zoomBy(0.8f, Offset(state.viewport.width / 2f, state.viewport.height / 2f))
                },
                onFit = {
                    if (usesV2Workspace) v2Controller.fitPage() else state.fitPage()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(Space.md)
            )

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (calibrating) {
                    // لوحة عادية جوّه الـBox، مش شيت مودال — عشان تفضل الرسمة
                    // فوقها قابلة للمس (شوف التعليق فوق [TakeoffCalibratePanel]).
                    TakeoffCalibratePanel(
                        points = calibPoints.toList(),
                        pageGeometry = pageGeometry,
                        onApply = { metresPerPoint, note, allPages ->
                            scope.launch {
                                if (allPages) {
                                    vm.takeoff.copyScaleToAllPages(
                                        drawingId,
                                        com.corewall.qaqc.data.db.TakeoffScaleEntity(
                                            drawingId, state.currentPage, metresPerPoint, note
                                        ),
                                        active.pageCount
                                    )
                                } else {
                                    vm.takeoff.setScale(drawingId, state.currentPage, metresPerPoint, note)
                                }
                            }
                            endSession()
                        },
                        onClearPoints = { calibPoints.clear() },
                        onCancel = { endSession() }
                    )
                } else if (inkMode) {
                    S25InkDock(
                        colorArgb = inkColorArgb,
                        widthPx = inkWidthPx,
                        onColorChange = { inkColorArgb = it },
                        onWidthChange = { inkWidthPx = it },
                        onUndo = {
                            when (val undo = v2Controller.undoLastInk()) {
                                is V2InkUndoResult.Persisted -> scope.launch { vm.takeoff.deleteAnnotation(undo.annotationId) }
                                is V2InkUndoResult.Local -> measurementNotice = "تم التراجع عن آخر خط"
                                null -> measurementNotice = "لا يوجد خط للتراجع"
                            }
                        },
                        onExit = { endSession() }
                    )
                } else {
                    val selectedItem = pageItems.firstOrNull { it.id == selectedId }
                    val focusedVertices = selectedItem?.let { selected ->
                        vertexFocusTarget?.let { target -> TakeoffMath.verticesFor(selected, target) }
                    }.orEmpty()
                    S25MeasurementDock(
                        pointerActive = mode == EditorMode.POINTER,
                        activeTool = tool,
                        deducting = deductFor != null,
                        calibrated = pageGeometry.calibrated,
                        snapEnabled = snapEnabled,
                        liveReadout = displayedReadout,
                        hasDraft = if (usesV2Workspace) pendingV2Measurement != null else draft.isNotEmpty() || annotationDraft.isNotEmpty(),
                        expanded = dockExpanded,
                        onToggleExpanded = { dockExpanded = !dockExpanded },
                        selected = selectedId != null,
                        canAddToShape = selectedItem?.tool.let {
                            it == TakeoffTool.AREA || it == TakeoffTool.VOLUME || it == TakeoffTool.LENGTH
                        },
                        addingToShape = addToShapeFor != null,
                        canDeleteVertex = vertexFocusTarget != null && selectedItem != null &&
                            selectedItem.tool != TakeoffTool.DIMENSION &&
                            focusedVertices.size > minVertsFor(selectedItem.tool),
                        multiCount = multiSelectedIds.size,
                        onPointer = { endSession() },
                        onUndo = {
                            if (usesV2Workspace) {
                                if (!v2Controller.undoMeasurementPoint()) measurementNotice = "لا توجد نقطة للتراجع"
                            } else undoLastDraftPoint()
                        },
                        onToggleSnap = { snapEnabled = !snapEnabled },
                        onPick = { picked -> pickTool(picked) },
                        onMore = { toolsSheetOpen = true },
                        onDone = {
                            when {
                                usesV2Workspace -> finishV2Measurement()
                                annotationTool != null -> finishAnnotation()
                                else -> commit()
                            }
                        },
                        onAddToShape = {
                            val target = selectedItem
                            if (target != null) {
                                clearDrafts()
                                addToShapeFor = target
                                mode = EditorMode.DRAW
                                tool = target.tool
                            }
                        },
                        onToggleVisible = {
                            val itemId = selectedId?.toLongOrNull()
                            if (itemId != null) {
                                scope.launch {
                                    vm.takeoff.itemById(itemId)?.let { row ->
                                        vm.takeoff.saveItem(row.copy(visible = !row.visible))
                                    }
                                }
                            }
                        },
                        onDeleteVertex = {
                            val target = vertexFocusTarget
                            val item = selectedItem
                            val itemId = item?.id?.toLongOrNull()
                            val vertices = if (item != null && target != null) {
                                TakeoffMath.verticesFor(item, target)
                            } else emptyList()
                            if (target != null && item != null && itemId != null &&
                                item.tool != TakeoffTool.DIMENSION &&
                                target.vertexIndex < vertices.size && vertices.size > minVertsFor(item.tool)
                            ) {
                                val updated = vertices.toMutableList().also { it.removeAt(target.vertexIndex) }
                                persistVertexPart(itemId, target, updated, "حذف رأس من")
                            }
                            vertexFocusTarget = null
                        },
                        onDeleteSelected = {
                            val id = selectedId?.toLongOrNull()
                            if (id != null) {
                                scope.launch {
                                    val row = vm.takeoff.itemById(id)
                                    val children = vm.takeoff.childrenOf(id)
                                    vm.takeoff.deleteItem(id)
                                    if (row != null) {
                                        lastUndo = UndoAction("حذف \"${row.name}\"") {
                                            vm.takeoff.saveItem(row)
                                            children.forEach { vm.takeoff.saveItem(it) }
                                        }
                                    }
                                }
                            }
                            selectedId = null
                        },
                        onDeleteMulti = {
                            val ids = multiSelectedIds.mapNotNull { it.toLongOrNull() }
                            scope.launch {
                                val rows = ids.mapNotNull { vm.takeoff.itemById(it) }
                                val children = ids.flatMap { vm.takeoff.childrenOf(it) }
                                ids.forEach { vm.takeoff.deleteItem(it) }
                                if (rows.isNotEmpty()) {
                                    lastUndo = UndoAction("حذف ${rows.size} بند") {
                                        rows.forEach { vm.takeoff.saveItem(it) }
                                        children.forEach { vm.takeoff.saveItem(it) }
                                    }
                                }
                            }
                            multiSelectedIds = emptySet()
                        },
                        onEditSelected = { editingItem = selectedItem }
                    )
                }
            }
        }
    }

    if (toolsSheetOpen) {
        TakeoffToolsSheet(
            mode = mode,
            tool = tool,
            deducting = deductFor != null,
            canDeduct = selectedId != null &&
                pageItems.firstOrNull { it.id == selectedId }?.tool.let {
                    it == TakeoffTool.AREA || it == TakeoffTool.VOLUME
                },
            onPick = { picked -> pickTool(picked) },
            onRect = { pickTool(TakeoffTool.AREA, viaRect = true) },
            onVertexEdit = { clearDrafts(); mode = EditorMode.VERTEX },
            onBoxSelect = { clearDrafts(); mode = EditorMode.BOXSELECT; selectedId = null },
            onInk = { clearDrafts(); inkMode = true; toolsSheetOpen = false },
            onDeduct = {
                val parent = pageItems.firstOrNull { it.id == selectedId }
                if (parent != null) {
                    draft.clear(); draftPage.intValue = -1
                    deductFor = parent; mode = EditorMode.DRAW; tool = TakeoffTool.AREA
                }
            },
            onDismiss = { toolsSheetOpen = false }
        )
    }

    if (totalsOpen) {
        val categoryModels = remember(categories) { categories.map { vm.takeoff.categoryToModel(it) } }
        TakeoffTotalsSheet(
            items = items,
            pageGeometry = pageGeometry,
            categories = categoryModels,
            onDismiss = { totalsOpen = false }
        )
    }

    if (settingsOpen) {
        TakeoffSettingsSheet(
            settings = settings,
            onUpdate = { transform -> vm.updateSettings(transform) },
            onDismiss = { settingsOpen = false }
        )
    }

    pendingNaming?.let { naming ->
        TakeoffNameSheet(
            tool = naming.tool,
            suggestedName = defaultName(naming.tool, rows.size + 1),
            categories = categories,
            onCreateCategory = { name, color, onCreated ->
                val pid = projectId
                if (pid != null) {
                    scope.launch {
                        val cat = vm.takeoff.createCategory(pid, name, color)
                        onCreated(cat.id)
                    }
                }
            },
            onConfirm = { name, categoryId, colorArgb, thickness, colLength, colWidth, colHeight ->
                pendingNaming = null
                if (!naming.viaRect && naming.tool.toV2WorkspaceTool() != null) {
                    // أدوات V2 تحفظ مرة واحدة عند «إنهاء»؛ لا ننشئ صفاً فارغاً
                    // حتى لا يبقى بند شبح عند إلغاء مسودة القلم.
                    pendingV2Measurement = PendingV2Measurement(
                        tool = naming.tool,
                        name = name,
                        categoryId = categoryId,
                        colorArgb = colorArgb,
                        thicknessMetres = thickness
                    )
                    mode = EditorMode.DRAW
                    tool = naming.tool
                    measurementNotice = null
                    colourIndex++
                    return@TakeoffNameSheet
                }
                // البند بيتحجز في القاعدة دلوقتي، من غير هندسة لسه — عشان
                // الـID يبقى موجود من الأول للصيغ. وضع الرسم مايتفعّلش إلا
                // لما الـID يرجع فعلاً، عشان مفيش سباق بين اللمسة الأولى
                // وكتابة القاعدة.
                scope.launch {
                    val newId = vm.takeoff.saveItem(
                        TakeoffItemEntity(
                            drawingId = drawingId,
                            page = state.currentPage,
                            tool = naming.tool.name,
                            name = name,
                            colorArgb = colorArgb,
                            pointsJson = "[]",
                            categoryId = categoryId,
                            thickness = thickness,
                            colLength = colLength,
                            colWidth = colWidth,
                            colHeight = colHeight,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    pendingDrawItemId = newId
                    mode = if (naming.viaRect) EditorMode.RECT else EditorMode.DRAW
                    tool = naming.tool
                }
                colourIndex++
            },
            onDismiss = { pendingNaming = null }
        )
    }

    if (formulasOpen) {
        TakeoffFormulasSheet(
            drawingId = drawingId,
            items = items,
            categories = categories,
            formulaRows = formulaRows,
            pageGeometryFor = pageGeometryFor,
            onSave = { entity -> scope.launch { vm.takeoff.saveFormula(entity) } },
            onDelete = { id -> scope.launch { vm.takeoff.deleteFormula(id) } },
            onDismiss = { formulasOpen = false }
        )
    }

    editingItem?.let { editing ->
        TakeoffEditItemSheet(
            item = editing,
            categories = categories,
            onCreateCategory = { name, color, onCreated ->
                val pid = projectId
                if (pid != null) {
                    scope.launch {
                        val cat = vm.takeoff.createCategory(pid, name, color)
                        onCreated(cat.id)
                    }
                }
            },
            onSave = { name, categoryId, colorArgb, zone, progressPercent, rateOverride ->
                val itemId = editing.id.toLongOrNull()
                if (itemId != null) {
                    scope.launch {
                        vm.takeoff.itemById(itemId)?.let { row ->
                            vm.takeoff.saveItem(
                                row.copy(
                                    name = name, categoryId = categoryId, colorArgb = colorArgb,
                                    zone = zone, progressPercent = progressPercent, rateOverride = rateOverride
                                )
                            )
                        }
                    }
                }
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }

    if (textPromptPoint != null && textPromptPage >= 0) {
        TakeoffTextAnnotationSheet(
            onConfirm = { text ->
                saveAnnotation(TakeoffAnnotationType.TEXT, textPromptPage, listOf(textPromptPoint!!), text)
                textPromptPoint = null; textPromptPage = -1
            },
            onDismiss = { textPromptPoint = null; textPromptPage = -1 }
        )
    }
}

/**
 * اللمسة لمست تعليق؟ — إعادة استخدام لخوارزميات [TakeoffMath] الموجودة
 * بدل ما تتكرّر: نقطة-جوّه-مضلّع للسحابة، ومسافة-لخط للسهم.
 */
private fun annotationHit(a: TakeoffAnnotation, p: TakeoffPoint, page: PageGeometry): Boolean = when (a.type) {
    TakeoffAnnotationType.CLOUD -> TakeoffMath.pointInRing(p, a.verts)
    TakeoffAnnotationType.ARROW ->
        a.verts.size >= 2 && TakeoffMath.distanceToPolylinePt(p, a.verts, page) <= 14.0
    TakeoffAnnotationType.TEXT ->
        a.verts.isNotEmpty() && hypot(
            (p.x - a.verts[0].x) * page.widthPt, (p.y - a.verts[0].y) * page.heightPt
        ) <= 28.0
    TakeoffAnnotationType.INK ->
        a.verts.size >= 2 && TakeoffMath.distanceToPolylinePt(p, a.verts, page) <= 16.0
}

private fun defaultName(tool: TakeoffTool, index: Int): String = when (tool) {
    TakeoffTool.AREA -> "مساحة $index"
    TakeoffTool.LENGTH -> "طول $index"
    TakeoffTool.COUNT -> "عدّ $index"
    TakeoffTool.DEDUCT -> "خصم $index"
    TakeoffTool.VOLUME -> "حجم $index"
    TakeoffTool.COLUMN -> "عمود $index"
    TakeoffTool.DIMENSION -> "بُعد $index"
}

/** أقل عدد رؤوس مسموح بيه قبل ما "احذف الرأس" يوقف — زي القيد الهندسي لكل أداة. */
private fun minVertsFor(tool: TakeoffTool): Int = when (tool) {
    TakeoffTool.LENGTH -> 2
    TakeoffTool.COUNT, TakeoffTool.COLUMN -> 1
    else -> 3
}

@Composable
private fun TakeoffTopBar(
    title: String?,
    page: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onTotals: () -> Unit,
    onCalibrate: () -> Unit,
    onSettings: () -> Unit,
    onTree: () -> Unit,
    onFormulas: () -> Unit,
    undoLabel: String?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Surface(modifier.fillMaxWidth(), color = c.surface, shadowElevation = Elevation.raised) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CwIconButton(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", onBack)
            // سطر واحد مقصوص — **مش** تجميل.
            //
            // العمود ده جنب صف أيقونات فيه `horizontalScroll`. الصف ده كان
            // بلا وزن، فكان بياخد العرض المتاح كله ويسيب للعنوان صفر. نص
            // بعرض صفر بيلفّ حرف في كل سطر، فـ"الصفحة مش معايرة — عاير
            // الأول" كانت بتطلع ٢٨ سطر وتمطّ الشريط لنص الشاشة فوق الرسمة.
            // الوزن على الاتنين + القص بيمنعوا ده نهائيًا.
            Text(
                title ?: "صفحة $page من $pageCount",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // حالة المعايرة اتشالت من هنا: هي معروضة أصلاً كشارة في الدوك
            // السفلي، وتكرارها كان بيكلّف سطر تاني في أغلى مكان في الشاشة.
            Row(
                Modifier
                    .weight(2f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CwIconButton(
                    Icons.AutoMirrored.Filled.Undo,
                    undoLabel?.let { "تراجع عن: $it" } ?: "مفيش حاجة تتراجع عنها",
                    onUndo,
                    tint = if (undoLabel != null) c.warning.fg else null,
                    enabled = undoLabel != null
                )
                CwIconButton(Icons.Filled.Category, "القياسات والفئات", onTree)
                CwIconButton(Icons.Filled.Straighten, "معايرة المقياس", onCalibrate)
                if (pageCount > 1) {
                    CwIconButton(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        "الصفحة السابقة",
                        onPreviousPage,
                        enabled = page > 1
                    )
                    CwIconButton(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "الصفحة التالية",
                        onNextPage,
                        enabled = page < pageCount
                    )
                }
                CwIconButton(Icons.Filled.Calculate, "الصيغ", onFormulas)
                CwIconButton(Icons.Filled.Functions, "الإجماليات", onTotals)
                CwIconButton(Icons.Filled.Settings, "إعدادات الرسم", onSettings)
            }
        }
    }
}

/** أزرار تكبير/تصغير/ملائمة طايفة فوق الرسمة — تحكّم دقيق غير لقطة الأصابع. */
@Composable
private fun TakeoffFloatingZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Surface(
        modifier = modifier,
        color = c.surface,
        shape = Radius.shapeLg,
        shadowElevation = Elevation.floating,
        border = BorderStroke(DesignStroke.hair, c.outline)
    ) {
        Column(
            Modifier.padding(Space.xxs),
            verticalArrangement = Arrangement.spacedBy(Space.xxs)
        ) {
            CwIconButton(Icons.Filled.Add, "تكبير", onZoomIn)
            CwIconButton(Icons.Filled.Remove, "تصغير", onZoomOut)
            CwIconButton(Icons.Filled.FitScreen, "ملائمة الصفحة", onFit)
        }
    }
}

/**
 * شريط الحالة — صفحة/مقياس/تكبير/وحدة وحالة الالتقاط، زي أي أداة CAD
 * احترافية. الوحدة ثابتة "م" دلوقتي — تبديلها للإمبراطوري مش مطلوب لسه.
 */
/** شريط قلم ميداني: لون سريع، سماكة واضحة، تراجع وخروج بلا حجب لمس الرسم. */
@Composable
private fun S25InkDock(
    colorArgb: Long,
    widthPx: Float,
    onColorChange: (Long) -> Unit,
    onWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onExit: () -> Unit
) {
    val c = LocalCwColors.current
    val colors = listOf(0xFF1976D2L, 0xFF111827L, 0xFFD32F2FL, 0xFF7B1FA2L, 0xFF00796BL)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.sm),
        shape = Radius.shapeXl,
        color = c.surfaceRaised,
        shadowElevation = Elevation.floating,
        border = BorderStroke(1.dp, c.outline.copy(alpha = 0.82f))
    ) {
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, null, tint = Color(colorArgb.toInt()), modifier = Modifier.size(IconSize.md))
                Text(
                    "كتابة بالقلم",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    modifier = Modifier.padding(start = Space.sm).weight(1f)
                )
                CwIconButton(Icons.Filled.Undo, "تراجع عن آخر خط", onUndo)
                CwIconButton(Icons.Filled.Close, "إنهاء الكتابة", onExit)
            }
            Row(
                Modifier.padding(top = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colors.forEach { swatch ->
                    Surface(
                        onClick = { onColorChange(swatch) },
                        modifier = Modifier.size(Sizes.touch),
                        shape = Radius.pill,
                        color = Color(swatch.toInt()),
                        border = if (swatch == colorArgb) BorderStroke(2.dp, c.textPrimary) else null
                    ) {}
                }
                Text("السماكة", style = CwText.codeSmall, color = c.textSecondary)
                Slider(
                    value = widthPx,
                    onValueChange = onWidthChange,
                    valueRange = 1.4f..12f,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "اكتب بقلم S Pen. الإصبع يبقى للتكبير والتحريك، وطرف الممحاة يحذف الخط القريب.",
                style = CwText.codeSmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = Space.sm)
            )
        }
    }
}

@Composable
private fun TakeoffStatusBar(
    page: Int,
    pageCount: Int,
    scaleNote: String?,
    zoomPercent: Int,
    snapEnabled: Boolean,
    onToggleSnap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Surface(modifier.fillMaxWidth(), color = c.surfaceAlt) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            StatusField("صفحة $page/$pageCount")
            StatusField(scaleNote?.let { "مقياس $it" } ?: "غير معاير", warn = scaleNote == null)
            StatusField("تكبير $zoomPercent%")
            StatusField("وحدة م")
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(Radius.pill)
                    .clickable(onClick = onToggleSnap)
                    .padding(horizontal = Space.sm, vertical = Space.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                Icon(
                    Icons.Filled.GpsFixed, contentDescription = null,
                    tint = if (snapEnabled) c.accent else c.textTertiary,
                    modifier = Modifier.size(IconSize.sm)
                )
                Text(
                    if (snapEnabled) "التقاط: شغّال" else "التقاط: متوقّف",
                    style = CwText.codeSmall,
                    color = if (snapEnabled) c.accent else c.textTertiary
                )
            }
        }
    }
}

@Composable
private fun StatusField(text: String, warn: Boolean = false) {
    val c = LocalCwColors.current
    Text(
        text,
        style = CwText.codeSmall,
        color = if (warn) c.danger.fg else c.textSecondary,
        maxLines = 1
    )
}

/**
 * شريط الأدوات السفلي.
 *
 * أربع أدوات سريعة بس + زرار "المزيد" اللي بيفتح [TakeoffToolsSheet] —
 * بدل ما تتلمّ تسعة أزرار أيقونة من غير تسمية في شريط واحد مضغوط.
 * الأفعال السياقية (إنهاء/تعديل/حذف) بتفضل هنا لأنها مش اختيار أداة.
 */

/**
 * "أدوات الحصر" — شيت شبكي مقسّم لأقسام (قياس/تعليق/تحرير)، كل أداة
 * كارت بأيقونة ملوّنة واسم تحتها. بديل الشريط المضغوط لما المستخدم
 * يحتاج أداة مش من الأربعة السريعة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TakeoffToolsSheet(
    mode: EditorMode,
    tool: TakeoffTool,
    deducting: Boolean,
    canDeduct: Boolean,
    onPick: (TakeoffTool) -> Unit,
    onRect: () -> Unit,
    onVertexEdit: () -> Unit,
    onBoxSelect: () -> Unit,
    onInk: () -> Unit,
    onDeduct: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "أدوات القياس",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                CwIconButton(Icons.Filled.Close, "إغلاق", onDismiss)
            }

            Text("القياس", style = CwText.sectionLabel, color = c.textTertiary)
            ToolGrid {
                ToolGridItem(Icons.Filled.Square, "مساحة", tool == TakeoffTool.AREA && !deducting) {
                    onPick(TakeoffTool.AREA); onDismiss()
                }
                ToolGridItem(Icons.Filled.CropSquare, "مستطيل", mode == EditorMode.RECT) {
                    onRect(); onDismiss()
                }
                ToolGridItem(Icons.Filled.Timeline, "طول", tool == TakeoffTool.LENGTH) {
                    onPick(TakeoffTool.LENGTH); onDismiss()
                }
                ToolGridItem(Icons.Filled.PinDrop, "عدّ", tool == TakeoffTool.COUNT) {
                    onPick(TakeoffTool.COUNT); onDismiss()
                }
                ToolGridItem(Icons.Filled.Layers, "حجم", tool == TakeoffTool.VOLUME && !deducting) {
                    onPick(TakeoffTool.VOLUME); onDismiss()
                }
                ToolGridItem(Icons.Filled.ViewColumn, "عمود", tool == TakeoffTool.COLUMN) {
                    onPick(TakeoffTool.COLUMN); onDismiss()
                }
                ToolGridItem(Icons.Filled.SquareFoot, "بُعد", tool == TakeoffTool.DIMENSION) {
                    onPick(TakeoffTool.DIMENSION); onDismiss()
                }
            }

            Text("التحرير", style = CwText.sectionLabel, color = c.textTertiary)
            ToolGrid {
                ToolGridItem(Icons.Filled.Edit, "قلم وكتابة", false) {
                    onInk(); onDismiss()
                }
                ToolGridItem(Icons.Filled.OpenWith, "تعديل الرؤوس", mode == EditorMode.VERTEX) {
                    onVertexEdit(); onDismiss()
                }
                ToolGridItem(Icons.Filled.HighlightAlt, "تحديد بمستطيل", mode == EditorMode.BOXSELECT) {
                    onBoxSelect(); onDismiss()
                }
                ToolGridItem(
                    Icons.Filled.ContentCut, "خصم من المحدّد", deducting,
                    enabled = canDeduct || deducting
                ) {
                    onDeduct(); onDismiss()
                }
            }
        }
    }
}

/**
 * صف كروت أدوات، بيتمرّر أفقيًا. مش شبكة ملتفّة عن قصد — `FlowRow` مش
 * مضمون في نسخة Compose Foundation المجمّدة هنا، والتمرير الأفقي نفس
 * النمط المستخدم في شيبات المقاييس فوق، فمفيش لغة بصرية جديدة.
 */
@Composable
private fun ToolGrid(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) { content() }
}

@Composable
private fun ToolGridItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    val container = if (active) c.accentContainer else c.surfaceAlt
    val content = if (!enabled) c.textTertiary.copy(alpha = 0.4f) else if (active) c.accent else c.textSecondary
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(84.dp),
        shape = Radius.shapeLg,
        color = container,
        border = if (active) BorderStroke(DesignStroke.hair, c.accent) else null
    ) {
        Column(
            Modifier
                .padding(vertical = Space.md, horizontal = Space.xs)
                .heightIn(min = Sizes.touch),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(IconSize.lg))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
