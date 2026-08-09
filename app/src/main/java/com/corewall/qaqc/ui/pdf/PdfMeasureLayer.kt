package com.corewall.qaqc.ui.pdf

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.corewall.qaqc.data.db.PdfMeasurementEntity
import com.corewall.qaqc.pdfengine.MeasureKind
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.pdfengine.Scale
import com.corewall.qaqc.pdfengine.pagePointToScreen
import com.corewall.qaqc.pdfengine.polygonArea
import com.corewall.qaqc.pdfengine.polygonPerimeter
import com.corewall.qaqc.pdfengine.polylineLength

/**
 * طبقة القياس — الحالة اللحظية والرسم.
 *
 * النقط بتتخزّن **منسّبة للصفحة** (٠..١) زي التعليقات بالظبط، فالقياس
 * بيفضل ملزوق في مكانه مع أي تكبير أو تدوير. التحويل لنقط PDF بيحصل
 * وقت الحساب بس، بضرب المقاس الحقيقي للصفحة.
 */
@Stable
class MeasureSession {

    /** وضع القياس شغّال؟ لما يبقى شغّال، النقر بيضيف نقطة مش بيخفي الواجهة. */
    var enabled by mutableStateOf(false)

    var kind by mutableStateOf(MeasureKind.DISTANCE)

    /**
     * وضع المعايرة: نفس رسم المسافة، بس النتيجة بتروح لحساب المقياس
     * مش لقياس متسجّل.
     */
    var calibrating by mutableStateOf(false)

    /** نقط منسّبة (nx, ny) بالتوالي. */
    val draft = mutableStateListOf<Float>()

    var draftPage by mutableIntStateOf(-1)
        private set

    val pointCount: Int get() = draft.size / 2

    fun addPoint(page: Int, nx: Float, ny: Float) {
        // القياس جوّه صفحة واحدة. أول نقطة بتحدّد الصفحة، واللي بعدها
        // في صفحة تانية بتبدأ قياس جديد بدل ما تدّي رقم بلا معنى.
        if (draftPage != page) {
            draft.clear()
            draftPage = page
        }
        draft += nx.coerceIn(0f, 1f)
        draft += ny.coerceIn(0f, 1f)
    }

    fun undoPoint() {
        if (draft.size >= 2) {
            draft.removeAt(draft.size - 1)
            draft.removeAt(draft.size - 1)
        }
        if (draft.isEmpty()) draftPage = -1
    }

    fun reset() {
        draft.clear()
        draftPage = -1
    }

    fun points(): List<Float> = draft.toList()

    /** أقل عدد نقط عشان القياس يبقى له معنى ويتحفظ. */
    fun isComplete(): Boolean = when {
        calibrating -> pointCount >= 2
        kind == MeasureKind.COUNT -> pointCount >= 1
        kind == MeasureKind.AREA -> pointCount >= 3
        else -> pointCount >= 2
    }
}

/** نقط منسّبة → نقط PDF، بمقاس الصفحة الحقيقي. */
fun toPagePoints(flat: List<Float>, pageWidthPt: Float, pageHeightPt: Float): List<Offset> =
    (flat.indices step 2).mapNotNull { i ->
        if (i + 1 >= flat.size) null
        else Offset(flat[i] * pageWidthPt, flat[i + 1] * pageHeightPt)
    }

/**
 * نص القيمة لقياس — أو رسالة إن الصفحة لسه مش معايَرة.
 *
 * الرسالة دي مهمة: رقم ظاهر من غير معايرة بيبقى **كذب مقنع**. المهندس
 * بيشوف "٤٢٠ مم" وبيصدّقه، وهو مبني على افتراض مقياس مش موجود.
 */
fun measurementText(
    kind: MeasureKind,
    pagePoints: List<Offset>,
    scale: Scale?
): String {
    if (kind == MeasureKind.COUNT) return "${pagePoints.size}"
    if (scale == null || !scale.isValid) return "محتاج معايرة"
    return when (kind) {
        MeasureKind.DISTANCE -> scale.formatLength(polylineLength(pagePoints))
        MeasureKind.AREA -> {
            val area = scale.formatArea(polygonArea(pagePoints))
            val perimeter = scale.formatLength(polygonPerimeter(pagePoints))
            "$area · محيط $perimeter"
        }
        MeasureKind.COUNT -> "${pagePoints.size}"
    }
}

// ══════════════════════════════════════════════════════════════ الرسم

/** بيرسم القياسات المحفوظة للصفحات الظاهرة. */
fun DrawScope.drawMeasurements(
    state: PdfViewerState,
    items: List<PdfMeasurementEntity>,
    pointsOf: (PdfMeasurementEntity) -> List<Float>,
    scaleOf: (page: Int) -> Scale?
) {
    if (items.isEmpty()) return
    val rect = state.visibleDocRect()
    val visible = state.layout
        .visible(rect.left, rect.top, rect.right, rect.bottom)
        .map { it.index }
        .toSet()

    for (item in items) {
        if (item.page !in visible) continue
        val slot = state.layout.slotAt(item.page) ?: continue
        val flat = pointsOf(item)
        if (flat.size < 2) continue

        val screen = (flat.indices step 2).mapNotNull { i ->
            if (i + 1 >= flat.size) null
            else state.pagePointToScreen(item.page, flat[i], flat[i + 1])
        }
        val kind = MeasureKind.fromId(item.kind)
        val label = item.label.ifBlank {
            measurementText(
                kind,
                toPagePoints(flat, slot.size.width, slot.size.height),
                scaleOf(item.page)
            )
        }
        drawMeasureShape(kind, screen, Color(item.colorArgb), label)
    }
}

/** بيرسم القياس اللي المستخدم بيرسمه دلوقتي. */
fun DrawScope.drawMeasureDraft(
    state: PdfViewerState,
    session: MeasureSession,
    scale: Scale?,
    color: Color
) {
    val page = session.draftPage
    if (page < 0 || session.draft.size < 2) return
    val slot = state.layout.slotAt(page) ?: return
    val flat = session.points()

    val screen = (flat.indices step 2).mapNotNull { i ->
        if (i + 1 >= flat.size) null
        else state.pagePointToScreen(page, flat[i], flat[i + 1])
    }
    val kind = if (session.calibrating) MeasureKind.DISTANCE else session.kind
    val label =
        if (session.calibrating) "${screen.size} نقطة"
        else measurementText(kind, toPagePoints(flat, slot.size.width, slot.size.height), scale)

    drawMeasureShape(kind, screen, color, label, draft = true)
}

private fun DrawScope.drawMeasureShape(
    kind: MeasureKind,
    screen: List<Offset>,
    color: Color,
    label: String,
    draft: Boolean = false
) {
    if (screen.isEmpty()) return

    when (kind) {
        MeasureKind.COUNT -> {
            screen.forEachIndexed { index, p ->
                drawCircle(color.copy(alpha = 0.22f), COUNT_RADIUS, p)
                drawCircle(color, COUNT_RADIUS, p, style = Stroke(width = LINE_WIDTH))
                drawLabel("${index + 1}", p, color, small = true)
            }
            return
        }

        MeasureKind.AREA -> {
            if (screen.size >= 3) {
                val path = Path().apply {
                    moveTo(screen.first().x, screen.first().y)
                    screen.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, color.copy(alpha = 0.16f))
                drawPath(path, color, style = Stroke(width = LINE_WIDTH))
            } else {
                drawPolyline(screen, color)
            }
        }

        MeasureKind.DISTANCE -> drawPolyline(screen, color)
    }

    // عُقد المسار — بتوري النقط اللي اتحطّت فعلاً، ودي اللي بتخلّي
    // المستخدم يعرف لو نقطة وقعت في مكان غلط قبل ما يخلّص.
    screen.forEach { p ->
        drawCircle(Color.White, NODE_RADIUS, p)
        drawCircle(color, NODE_RADIUS, p, style = Stroke(width = NODE_STROKE))
    }

    if (label.isNotBlank()) {
        val anchor = when (kind) {
            MeasureKind.AREA -> centroid(screen)
            else -> screen.last()
        }
        drawLabel(label, anchor, color, small = false, offsetY = if (draft) -LABEL_LIFT else 0f)
    }
}

private fun DrawScope.drawPolyline(screen: List<Offset>, color: Color) {
    for (i in 1 until screen.size) {
        drawLine(color, screen[i - 1], screen[i], strokeWidth = LINE_WIDTH)
    }
}

private fun centroid(points: List<Offset>): Offset {
    if (points.isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    points.forEach { x += it.x; y += it.y }
    return Offset(x / points.size, y / points.size)
}

/**
 * كتابة على الكانفاس بمحرّك أندرويد.
 *
 * `drawText` من Compose محتاج `TextMeasurer` ومقاس ثابت، والقياس هنا نصّه
 * بيتغيّر مع كل حركة إصبع. الرسم المباشر أرخص، وكمان بيدّي تشكيل عربي
 * سليم لكلمة زي "محتاج معايرة".
 *
 * الـ`Paint` متعمولة مرة واحدة برّه الدالة عن قصد: دي بتتنادى عشرات
 * المرات في كل إطار، وتخصيص كائنين في كل نداء بيولّع الـGC وسط التمرير.
 */
private val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
}
private val labelBackground = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
}
private val labelBorder = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    style = android.graphics.Paint.Style.STROKE
    strokeWidth = NODE_STROKE
}
private val labelRect = android.graphics.RectF()

private fun DrawScope.drawLabel(
    text: String,
    at: Offset,
    color: Color,
    small: Boolean,
    offsetY: Float = 0f
) {
    if (text.isBlank()) return
    labelPaint.textSize = if (small) LABEL_SMALL_PX else LABEL_PX
    labelPaint.isFakeBoldText = !small
    labelPaint.color = color.toArgb()
    labelBorder.color = color.toArgb()

    val width = labelPaint.measureText(text)
    val metrics = labelPaint.fontMetrics
    val pad = if (small) LABEL_PAD_SMALL else LABEL_PAD
    val cy = at.y + offsetY - (if (small) 0f else LABEL_LIFT)

    labelRect.set(
        at.x - width / 2f - pad,
        cy + metrics.top - pad,
        at.x + width / 2f + pad,
        cy + metrics.bottom + pad
    )

    // خلفية صلبة تحت النص: الرسمة التنفيذية مليانة خطوط، ونص من غير
    // خلفية بيبقى غير مقروء فوقها مهما كان لونه.
    drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(labelRect, LABEL_RADIUS, LABEL_RADIUS, labelBackground)
        drawRoundRect(labelRect, LABEL_RADIUS, LABEL_RADIUS, labelBorder)
        drawText(text, at.x, cy, labelPaint)
    }
}

private const val LINE_WIDTH = 3f
private const val NODE_RADIUS = 7f
private const val NODE_STROKE = 2.5f
private const val COUNT_RADIUS = 16f
private const val LABEL_PX = 34f
private const val LABEL_SMALL_PX = 22f
private const val LABEL_PAD = 10f
private const val LABEL_PAD_SMALL = 4f
private const val LABEL_RADIUS = 8f
private const val LABEL_LIFT = 22f
