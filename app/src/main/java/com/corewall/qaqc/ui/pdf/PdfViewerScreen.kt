package com.corewall.qaqc.ui.pdf

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.PdfAnnotationEntity
import com.corewall.qaqc.pdfengine.PdfCanvas
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.pdfengine.PdfOpenException
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.pdfengine.PageLayout
import com.corewall.qaqc.pdfengine.TileEngine
import com.corewall.qaqc.pdfengine.ViewMode
import com.corewall.qaqc.pdfengine.pageHit
import com.corewall.qaqc.pdfengine.pagePointToScreen
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class PdfTool(val toolName: String?) {
    PAN(null),
    HIGHLIGHT(PdfAnnotationEntity.TOOL_HIGHLIGHT),
    RECT(PdfAnnotationEntity.TOOL_RECT),
    CIRCLE(PdfAnnotationEntity.TOOL_CIRCLE),
    ARROW(PdfAnnotationEntity.TOOL_ARROW),
    FREEHAND(PdfAnnotationEntity.TOOL_FREEHAND)
}

private val PALETTE = listOf(
    0xFFFFD60A, // أصفر (هايلايت)
    0xFFFF453A, // أحمر
    0xFF34C759, // أخضر
    0xFF0A84FF, // أزرق
    0xFF000000  // أسود
)

private val json = Json

/**
 * عارض الـPDF — مبني على محرّك المربّعات.
 *
 * اللي اتغيّر عن النسخة القديمة، ولية:
 *
 * • **الرندر** كان صورة واحدة عرضها ٢٠٤٨ بكسل للصفحة كلها، والتكبير كان
 *   بيمطّطها. يعني عند ١٠× انت بتبصّ على ٢٠٥ بكسل حقيقيين متفرودين على
 *   الشاشة — والرسمة بتضيع في اللحظة اللي بتقرّب فيها عشان تقرا التسليح.
 *   دلوقتي كل منطقة بتترسم بدقّتها الحقيقية لحد ٦٤×.
 * • **التمرير** كان صفحة صفحة بأزرار. دلوقتي تمرير متصل رأسي وأفقي
 *   واندفاع طبيعي.
 * • **الذاكرة** كانت ٢٤ ميجا للصفحة الواحدة مهما كان التكبير. دلوقتي
 *   ميزانية محسوبة من الجهاز، وبتشيل كذا شاشة عند أي تكبير.
 *
 * التعليقات لسه بنفس النموذج المتخزّن (إحداثيات منسّبة ٠..١ لكل صفحة)،
 * فمفيش أي علامة قديمة ضاعت مع التغيير ده.
 */
@Composable
fun PdfViewerScreen(vm: MainViewModel, path: String, onClose: () -> Unit) {
    val c = LocalCwColors.current
    val context = LocalContext.current
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

    DisposableEffect(path) {
        onDispose { session?.close() }
    }

    val active = session
    if (openError != null || active == null) {
        LoadingOrError(openError, onClose)
        return
    }

    // ── المحرّك والحالة
    val engine = remember(active) { TileEngine(active, TileEngine.budgetFor(context)) }
    DisposableEffect(engine) { onDispose { engine.clear() } }

    val state = remember(active) { PdfViewerState(active.pageCount) }
    val measured by active.measuredCount.collectAsStateWithLifecycle()

    // الرصّ بيتعاد كل ما مقاس صفحة جديد يوصل. ده اللي بيخلّي المستند الكبير
    // يفتح فوراً بتقدير وبعدين يظبط نفسه من غير ما المستخدم يحسّ.
    LaunchedEffect(measured, state.mode) {
        state.setLayout(PageLayout.build(active.allSizes(), state.mode))
    }

    // قياس الصفحات القريبة من المشهد — مش كلها، عشان مستند ٢٠٠٠ صفحة
    // مايستهلكش القرص والوقت على صفحات محدش هيوصلها.
    LaunchedEffect(state.currentPage) {
        val from = (state.currentPage - 4).coerceAtLeast(0)
        val to = (state.currentPage + 8).coerceAtMost(active.pageCount - 1)
        for (p in from..to) active.measure(p)
    }

    // ── التعليقات
    val allAnnotations by vm.pdfAnnotations.collectAsStateWithLifecycle()
    val fileAnnotations = remember(allAnnotations, path) {
        allAnnotations.filter { it.filePath == path }
    }

    var tool by remember { mutableStateOf(PdfTool.PAN) }
    var colorIdx by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var draftPage by remember { mutableIntStateOf(-1) }
    var chromeVisible by remember { mutableStateOf(true) }

    val colorArgb = PALETTE[colorIdx]

    fun commitDraft() {
        val toolName = tool.toolName
        val page = draftPage
        if (toolName == null || page < 0 || draft.size < 2) {
            draft = emptyList(); draftPage = -1; return
        }
        // بنخزّن **منسّب لصفحته** — يفضل صح مع أي تكبير أو دوران أو تصدير
        val slot = state.layout.slotAt(page)
        if (slot == null) { draft = emptyList(); draftPage = -1; return }
        val flat = ArrayList<Float>(draft.size * 2)
        draft.forEach { p ->
            val doc = state.screenToDoc(p)
            flat += ((doc.x - slot.left) / slot.size.width).coerceIn(0f, 1f)
            flat += ((doc.y - slot.top) / slot.size.height).coerceIn(0f, 1f)
        }
        vm.addPdfAnnotation(
            PdfAnnotationEntity(
                filePath = path, page = page, tool = toolName,
                color = colorArgb, pointsJson = json.encodeToString(flat),
                createdAt = System.currentTimeMillis()
            )
        )
        draft = emptyList()
        draftPage = -1
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
                drawingActive = tool != PdfTool.PAN,
                onDrawStart = { p ->
                    val hit = state.pageHit(p)
                    if (hit != null) { draftPage = hit.page; draft = listOf(p) }
                },
                onDrawMove = { p ->
                    if (draftPage >= 0) {
                        draft = if (tool == PdfTool.FREEHAND) draft + p
                        else listOf(draft.firstOrNull() ?: p, p)
                    }
                },
                onDrawEnd = { commitDraft() },
                onTap = { chromeVisible = !chromeVisible },
                overlay = { s ->
                    drawAnnotations(s, fileAnnotations)
                    if (draft.size >= 2) {
                        tool.toolName?.let { drawShape(it, Color(colorArgb), draft, s.zoom) }
                    }
                }
            )

            AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                TopChrome(
                    name = file.name,
                    page = state.currentPage + 1,
                    pageCount = active.pageCount,
                    zoomPercent = state.zoomPercent(),
                    onClose = onClose,
                    onExport = { exportLauncher.launch("${file.nameWithoutExtension}-معلّق.pdf") }
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomChrome(
                    tool = tool,
                    onTool = { tool = if (tool == it) PdfTool.PAN else it },
                    colorIdx = colorIdx,
                    onColor = { colorIdx = it },
                    mode = state.mode,
                    onMode = { state.setMode(it, active.allSizes()) },
                    onFitWidth = { state.fitWidth() },
                    onFitPage = { state.fitPage() },
                    canUndo = fileAnnotations.any { it.page == state.currentPage },
                    onUndo = { vm.undoLastPdfAnnotation(path, state.currentPage) },
                    onClear = { vm.clearPdfPage(path, state.currentPage) }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════ الرسم فوق الصفحات

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
        drawShape(a.tool, Color(a.color), points, state.zoom)
    }
}

/** رسم شكل تعليق — نفس المنطق للمعاينة الحيّة وللمحفوظ. */
private fun DrawScope.drawShape(
    toolName: String,
    color: Color,
    points: List<Offset>,
    zoom: Float
) {
    if (points.size < 2) return
    val strokeWidth = 2.5.dp.toPx() * zoom.coerceIn(0.7f, 2.5f)
    val first = points.first()
    val last = points.last()
    val rect = Rect(
        min(first.x, last.x), min(first.y, last.y),
        maxOf(first.x, last.x), maxOf(first.y, last.y)
    )
    when (toolName) {
        PdfAnnotationEntity.TOOL_HIGHLIGHT ->
            drawRect(color.copy(alpha = 0.35f), rect.topLeft, Size(rect.width, rect.height))
        PdfAnnotationEntity.TOOL_RECT ->
            drawRect(color, rect.topLeft, Size(rect.width, rect.height), style = Stroke(strokeWidth))
        PdfAnnotationEntity.TOOL_CIRCLE ->
            drawOval(color, rect.topLeft, Size(rect.width, rect.height), style = Stroke(strokeWidth))
        PdfAnnotationEntity.TOOL_ARROW -> {
            drawLine(color, first, last, strokeWidth)
            val angle = atan2(last.y - first.y, last.x - first.x)
            val headLen = strokeWidth * 4
            listOf(angle + 2.6f, angle - 2.6f).forEach { a ->
                drawLine(
                    color, last,
                    Offset(last.x + headLen * cos(a), last.y + headLen * sin(a)),
                    strokeWidth
                )
            }
        }
        PdfAnnotationEntity.TOOL_FREEHAND -> {
            val path = Path().apply {
                moveTo(first.x, first.y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color, style = Stroke(strokeWidth))
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
    zoomPercent: Int,
    onClose: () -> Unit,
    onExport: () -> Unit
) {
    val c = LocalCwColors.current
    Surface(
        Modifier.fillMaxWidth(),
        color = c.surface.copy(alpha = 0.94f)
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CwIconButton(Icons.Filled.Close, "إغلاق", onClose)
            Column(Modifier.weight(1f).padding(horizontal = Space.sm)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    maxLines = 1
                )
                Text(
                    "صفحة $page من $pageCount · $zoomPercent٪",
                    style = CwText.codeSmall,
                    color = c.textTertiary,
                    maxLines = 1
                )
            }
            CwIconButton(Icons.Filled.IosShare, "صدّر نسخة معلّقة", onExport)
        }
    }
}

@Composable
private fun BottomChrome(
    tool: PdfTool,
    onTool: (PdfTool) -> Unit,
    colorIdx: Int,
    onColor: (Int) -> Unit,
    mode: ViewMode,
    onMode: (ViewMode) -> Unit,
    onFitWidth: () -> Unit,
    onFitPage: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    val c = LocalCwColors.current
    Surface(
        Modifier.fillMaxWidth(),
        color = c.surface.copy(alpha = 0.94f)
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs)
        ) {
            // لوحة الألوان بتظهر لما تبقى بترسم بس — كنترول مالوش معنى
            // دلوقتي بياخد مساحة ويشتّت.
            AnimatedVisibility(tool != PdfTool.PAN, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PALETTE.forEachIndexed { i, argb ->
                        Surface(
                            modifier = Modifier.size(Sizes.control),
                            shape = Radius.pill,
                            color = Color(argb),
                            border = androidx.compose.foundation.BorderStroke(
                                if (i == colorIdx) 3.dp else 1.dp,
                                if (i == colorIdx) c.accent else c.outline
                            ),
                            onClick = { onColor(i) }
                        ) {}
                    }
                    Spacer(Modifier.weight(1f))
                    CwIconButton(
                        Icons.AutoMirrored.Filled.Undo, "تراجع", onUndo,
                        enabled = canUndo
                    )
                    CwIconButton(
                        Icons.Filled.Delete, "امسح تعليقات الصفحة", onClear,
                        tint = c.danger.fg, enabled = canUndo
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                ToolChip(Icons.Filled.PanTool, "تنقّل", tool == PdfTool.PAN) { onTool(PdfTool.PAN) }
                ToolChip(Icons.Filled.Highlight, "تظليل", tool == PdfTool.HIGHLIGHT) { onTool(PdfTool.HIGHLIGHT) }
                ToolChip(Icons.Filled.CropSquare, "مستطيل", tool == PdfTool.RECT) { onTool(PdfTool.RECT) }
                ToolChip(Icons.Filled.RadioButtonUnchecked, "دايرة", tool == PdfTool.CIRCLE) { onTool(PdfTool.CIRCLE) }
                ToolChip(Icons.AutoMirrored.Filled.CallMade, "سهم", tool == PdfTool.ARROW) { onTool(PdfTool.ARROW) }
                ToolChip(Icons.Filled.Draw, "رسم حر", tool == PdfTool.FREEHAND) { onTool(PdfTool.FREEHAND) }

                Spacer(Modifier.width(Space.md))

                ToolChip(
                    if (mode == ViewMode.CONTINUOUS_HORIZONTAL) Icons.Filled.SwapHoriz else Icons.Filled.SwapVert,
                    if (mode == ViewMode.CONTINUOUS_HORIZONTAL) "تمرير أفقي" else "تمرير رأسي",
                    false
                ) {
                    onMode(
                        if (mode == ViewMode.CONTINUOUS_VERTICAL) ViewMode.CONTINUOUS_HORIZONTAL
                        else ViewMode.CONTINUOUS_VERTICAL
                    )
                }
                ToolChip(Icons.Filled.FitScreen, "ملء العرض", false, onFitWidth)
                ToolChip(Icons.Filled.ZoomOutMap, "الصفحة كاملة", false, onFitPage)
            }
        }
    }
}

@Composable
private fun ToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    Surface(
        shape = Radius.shapeMd,
        color = if (active) c.accentContainer else Color.Transparent,
        onClick = onClick,
        modifier = Modifier.height(Sizes.touch)
    ) {
        Row(
            Modifier.padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xxs)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) c.accent else c.textSecondary,
                modifier = Modifier.size(IconSize.md)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) c.accent else c.textSecondary,
                maxLines = 1
            )
        }
    }
}

// ══════════════════════════════════════════════════════ التصدير

/**
 * بيصدّر نسخة كل صفحة فيها مرسومة بتعليقاتها.
 *
 * ملاحظة صريحة: التصدير ده **بيحوّل الصفحات لصور**. النص بيتحوّل بكسل،
 * فالنسخة المصدَّرة مش قابلة للبحث وحجمها أكبر. ده كان سلوك النسخة القديمة
 * وسايبينه زي ما هو دلوقتي عشان مانكسرش حاجة شغّالة — التصدير المتّجهي
 * (اللي بيكتب التعليقات كـ`/Annots` حقيقية) جاي في مرحلة عمليات المستندات.
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
                    drawShapeAndroid(canvas, a, flat, bitmap.width.toFloat(), bitmap.height.toFloat())
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

private fun drawShapeAndroid(
    canvas: android.graphics.Canvas,
    a: PdfAnnotationEntity,
    normalized: List<Float>,
    w: Float,
    h: Float
) {
    if (normalized.size < 4) return
    val pts = (normalized.indices step 2).mapNotNull { i ->
        if (i + 1 >= normalized.size) null
        else android.graphics.PointF(normalized[i] * w, normalized[i + 1] * h)
    }
    if (pts.size < 2) return
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = a.color.toInt()
        strokeWidth = w * 0.004f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    val first = pts.first()
    val last = pts.last()
    val rect = android.graphics.RectF(
        min(first.x, last.x), min(first.y, last.y),
        maxOf(first.x, last.x), maxOf(first.y, last.y)
    )
    when (a.tool) {
        PdfAnnotationEntity.TOOL_HIGHLIGHT -> {
            paint.style = android.graphics.Paint.Style.FILL
            paint.alpha = 90
            canvas.drawRect(rect, paint)
        }
        PdfAnnotationEntity.TOOL_RECT -> canvas.drawRect(rect, paint)
        PdfAnnotationEntity.TOOL_CIRCLE -> canvas.drawOval(rect, paint)
        PdfAnnotationEntity.TOOL_ARROW -> {
            canvas.drawLine(first.x, first.y, last.x, last.y, paint)
            val angle = atan2(last.y - first.y, last.x - first.x)
            val headLen = paint.strokeWidth * 4
            listOf(angle + 2.6f, angle - 2.6f).forEach { ang ->
                canvas.drawLine(
                    last.x, last.y,
                    last.x + headLen * cos(ang), last.y + headLen * sin(ang),
                    paint
                )
            }
        }
        PdfAnnotationEntity.TOOL_FREEHAND -> {
            val path = android.graphics.Path().apply {
                moveTo(first.x, first.y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            canvas.drawPath(path, paint)
        }
    }
}
