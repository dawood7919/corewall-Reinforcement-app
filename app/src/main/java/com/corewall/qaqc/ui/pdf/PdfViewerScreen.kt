package com.corewall.qaqc.ui.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.PdfAnnotationEntity
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
import com.corewall.qaqc.ui.design.Space

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
 * عارض PDF داخلي: تقليب صفحات + زوم، وأدوات تعليق بتتحفظ تلقائي:
 * هايلايت مساحة / مستطيل / دايرة / سهم / رسم حر — مع تراجع ومسح
 * وتصدير نسخة PDF عليها كل التعليقات.
 */
@Composable
fun PdfViewerScreen(vm: MainViewModel, path: String, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(path) { File(path) }

    val rendererLock = remember(path) { Any() }
    val renderer = remember(path) {
        runCatching {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }.getOrNull()
    }
    DisposableEffect(path) {
        onDispose { runCatching { renderer?.close() } }
    }

    if (renderer == null) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("مقدرناش نفتح الـPDF ده", color = MaterialTheme.colorScheme.error)
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
            }
        }
        return
    }

    val pageCount = renderer.pageCount
    var pageIndex by remember(path) { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf(PdfTool.PAN) }
    var colorIdx by remember { mutableIntStateOf(0) }

    val allAnnotations by vm.pdfAnnotations.collectAsStateWithLifecycle()
    val pageAnnotations = remember(allAnnotations, path, pageIndex) {
        allAnnotations.filter { it.filePath == path && it.page == pageIndex }.sortedBy { it.id }
    }

    val pageBitmap by produceState<Bitmap?>(initialValue = null, path, pageIndex) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { renderPage(renderer, rendererLock, pageIndex) }.getOrNull()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        exportAnnotatedPdf(renderer, rendererLock, os) { page ->
                            allAnnotations.filter { it.filePath == path && it.page == page }
                        }
                    } ?: error("مقدرناش نفتح الملف")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "تم تصدير النسخة المعلّقة ✓" else "فشل التصدير: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // الشريط العلوي (مظبوط تحت الـstatus bar)
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "إغلاق") }
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.undoLastPdfAnnotation(path, pageIndex) }) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "تراجع")
                }
                IconButton(onClick = { vm.clearPdfPage(path, pageIndex) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "مسح الصفحة")
                }
                IconButton(onClick = {
                    exportLauncher.launch(file.nameWithoutExtension + "-annotated.pdf")
                }) {
                    Icon(Icons.Filled.IosShare, contentDescription = "تصدير نسخة معلّقة")
                }
            }

            // الصفحة
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val bmp = pageBitmap
                if (bmp == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    PdfPageCanvas(
                        bitmap = bmp,
                        annotations = pageAnnotations,
                        tool = tool,
                        colorArgb = PALETTE[colorIdx],
                        onCommit = { toolName, points ->
                            vm.addPdfAnnotation(
                                PdfAnnotationEntity(
                                    filePath = path,
                                    page = pageIndex,
                                    tool = toolName,
                                    color = PALETTE[colorIdx],
                                    pointsJson = json.encodeToString(points),
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                        }
                    )
                }
            }

            // شريط الأدوات (سطرين مظبوطين فوق الـnavigation bar)
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = Space.sm, vertical = Space.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xxs)
                    ) {
                        ToolButton(Icons.Filled.PanTool, "تحريك وزوم", tool == PdfTool.PAN) { tool = PdfTool.PAN }
                        ToolButton(Icons.Filled.Highlight, "هايلايت", tool == PdfTool.HIGHLIGHT) { tool = PdfTool.HIGHLIGHT }
                        ToolButton(Icons.Filled.CropSquare, "مستطيل", tool == PdfTool.RECT) { tool = PdfTool.RECT }
                        ToolButton(Icons.Filled.RadioButtonUnchecked, "دايرة", tool == PdfTool.CIRCLE) { tool = PdfTool.CIRCLE }
                        ToolButton(Icons.AutoMirrored.Filled.CallMade, "سهم", tool == PdfTool.ARROW) { tool = PdfTool.ARROW }
                        ToolButton(Icons.Filled.Draw, "رسم حر", tool == PdfTool.FREEHAND) { tool = PdfTool.FREEHAND }
                        Spacer(Modifier.width(Space.sm))
                        PALETTE.forEachIndexed { i, c ->
                            Box(
                                Modifier
                                    .padding(Space.xxs)
                                    .size(if (i == colorIdx) 30.dp else 24.dp)
                                    .background(Color(c), CircleShape)
                                    .pointerInput(i) { detectTapGestures { colorIdx = i } }
                            )
                        }
                    }
                    if (pageCount > 1) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (pageIndex > 0) pageIndex-- }, enabled = pageIndex > 0) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "السابقة")
                            }
                            androidx.compose.material3.Slider(
                                value = pageIndex.toFloat(),
                                onValueChange = { pageIndex = it.toInt().coerceIn(0, pageCount - 1) },
                                valueRange = 0f..(pageCount - 1).toFloat(),
                                steps = (pageCount - 2).coerceAtLeast(0),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${pageIndex + 1}/$pageCount",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = Space.sm)
                            )
                            IconButton(
                                onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
                                enabled = pageIndex < pageCount - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "التالية")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PdfPageCanvas(
    bitmap: Bitmap,
    annotations: List<PdfAnnotationEntity>,
    tool: PdfTool,
    colorArgb: Long,
    onCommit: (toolName: String, normalizedPoints: List<Float>) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var draft by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()

    // العرض الافتراضي: الصفحة بعرض الشاشة كامل (fit-width) ومبدأها من فوق —
    // ولو الصفحة أقصر من الشاشة بتتوسّط رأسياً.
    fun baseTransform(size: IntSize): Pair<Float, Offset> {
        if (size.width == 0 || size.height == 0) return 1f to Offset.Zero
        val base = size.width / bw
        val yOff = ((size.height - bh * base) / 2).coerceAtLeast(0f)
        return base to Offset(0f, yOff)
    }

    /** مستطيل عرض الصفحة على الشاشة بعد الزوم. */
    fun displayRect(size: IntSize): Rect {
        val (base, baseOff) = baseTransform(size)
        val left = baseOff.x * scale + offset.x
        val top = baseOff.y * scale + offset.y
        return Rect(left, top, left + bw * base * scale, top + bh * base * scale)
    }

    fun toNormalized(p: Offset): Pair<Float, Float> {
        val r = displayRect(canvasSize)
        val nx = ((p.x - r.left) / r.width).coerceIn(0f, 1f)
        val ny = ((p.y - r.top) / r.height).coerceIn(0f, 1f)
        return nx to ny
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF3A3A3C))
            .onSizeChanged { canvasSize = it }
            .pointerInput(tool) {
                if (tool == PdfTool.PAN) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 10f)
                        val z = newScale / scale
                        offset = Offset(
                            offset.x * z + centroid.x * (1 - z) + pan.x,
                            offset.y * z + centroid.y * (1 - z) + pan.y
                        )
                        scale = newScale
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { pos -> draft = listOf(pos) },
                        onDrag = { change, _ ->
                            draft = if (tool == PdfTool.FREEHAND) draft + change.position
                            else listOf(draft.first(), change.position)
                        },
                        onDragEnd = {
                            if (draft.size >= 2) {
                                val normalized = draft.flatMap { p ->
                                    val (nx, ny) = toNormalized(p)
                                    listOf(nx, ny)
                                }
                                tool.toolName?.let { onCommit(it, normalized) }
                            }
                            draft = emptyList()
                        },
                        onDragCancel = { draft = emptyList() }
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                })
            }
    ) {
        val r = displayRect(IntSize(size.width.toInt(), size.height.toInt()))
        drawImage(
            image = image,
            dstOffset = IntOffset(r.left.toInt(), r.top.toInt()),
            dstSize = IntSize(r.width.toInt().coerceAtLeast(1), r.height.toInt().coerceAtLeast(1))
        )

        for (a in annotations) {
            val points = runCatching { json.decodeFromString<List<Float>>(a.pointsJson) }.getOrNull() ?: continue
            val screenPoints = (points.indices step 2).mapNotNull { i ->
                if (i + 1 >= points.size) null
                else Offset(r.left + points[i] * r.width, r.top + points[i + 1] * r.height)
            }
            drawShape(a.tool, Color(a.color), screenPoints, scale)
        }

        if (draft.size >= 2) {
            tool.toolName?.let { drawShape(it, Color(colorArgb), draft, scale) }
        }
    }
}

/** رسم شكل تعليق على الشاشة (نفس المنطق مستخدم للمعاينة والمحفوظ). */
private fun DrawScope.drawShape(toolName: String, color: Color, points: List<Offset>, zoom: Float) {
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

// ---------------------------------------------------------------- Rendering

private fun renderPage(renderer: PdfRenderer, lock: Any, index: Int, targetWidth: Int = 2048): Bitmap {
    synchronized(lock) {
        val page = renderer.openPage(index)
        try {
            val height = (targetWidth * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } finally {
            page.close()
        }
    }
}

/** تصدير نسخة PDF كل صفحة فيها مرسومة بتعليقاتها. */
private fun exportAnnotatedPdf(
    renderer: PdfRenderer,
    lock: Any,
    os: java.io.OutputStream,
    annotationsFor: (page: Int) -> List<PdfAnnotationEntity>
) {
    val doc = android.graphics.pdf.PdfDocument()
    for (i in 0 until renderer.pageCount) {
        val bitmap = renderPage(renderer, lock, i, targetWidth = 2000)
        val canvas = android.graphics.Canvas(bitmap)
        for (a in annotationsFor(i)) {
            val points = runCatching { json.decodeFromString<List<Float>>(a.pointsJson) }.getOrNull() ?: continue
            drawShapeAndroid(canvas, a, points, bitmap.width.toFloat(), bitmap.height.toFloat())
        }
        // مقاس صفحة الـPDF بالنقط زي الأصل
        val (pw, ph) = synchronized(lock) {
            val page = renderer.openPage(i)
            try { page.width to page.height } finally { page.close() }
        }
        val pdfPage = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
        pdfPage.canvas.drawBitmap(
            bitmap, null,
            android.graphics.RectF(0f, 0f, pw.toFloat(), ph.toFloat()),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        )
        doc.finishPage(pdfPage)
        bitmap.recycle()
    }
    doc.writeTo(os)
    doc.close()
}

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
