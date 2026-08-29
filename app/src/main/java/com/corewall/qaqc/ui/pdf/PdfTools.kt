package com.corewall.qaqc.ui.pdf

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import com.corewall.qaqc.data.db.PdfAnnotationEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * أدوات التعليم — التعريف والرسم، في مكان واحد.
 *
 * كل أداة بتعرّف **سلوكها** مش شكلها بس: هل بترسم بمسار حر ولا بمستطيل،
 * سُمكها الافتراضي كام، وشفافيتها كام. الفصل ده هو اللي بيخلّي إضافة أداة
 * جديدة سطر واحد هنا بدل تعديل في أربع أماكن.
 *
 * والرسم كله بيعدّي من [drawAnnotation] — نسخة واحدة بتستخدمها المعاينة
 * الحيّة والعلامات المحفوظة والتصدير. لما كان فيه نسختين، التصدير كان
 * بيطلع شكل مختلف عن اللي على الشاشة.
 */
enum class PdfTool(
    val id: String?,
    val label: String,
    val icon: ImageVector,
    /** بيرسم بمسار حر (كل النقط) ولا بنقطتين (بداية ونهاية)؟ */
    val freeform: Boolean = false,
    val defaultWidthPt: Float = 2.5f,
    val defaultOpacity: Float = 1f,
    /** بيملا المساحة بدل ما يرسم حدودها. */
    val filled: Boolean = false
) {
    PAN(null, "تنقّل", Icons.Filled.PanTool),

    /**
     * تحديد وتعديل — مش أداة رسم، فمالهاش `id` ومابتنتجش علامة.
     *
     * نقرة بتختار الشكل اللي تحتها، وسحب على الفاضي بيرسم مستطيل تحديد
     * وكل شكل بيلمسه بيتحدّد. زرار واحد لأن الاتنين نفس النية: "عايز أمسك
     * حاجة موجودة"، والتفريق بينهم هو نفس التفريق بين نقرة وسحب في أي
     * محرّر.
     */
    SELECT(null, "تحديد", Icons.Filled.HighlightAlt),

    PEN(PdfAnnotationEntity.TOOL_FREEHAND, "قلم", Icons.Filled.Draw, freeform = true, defaultWidthPt = 2f),

    MARKER(
        PdfAnnotationEntity.TOOL_MARKER, "ماركر", Icons.Filled.Brush,
        freeform = true, defaultWidthPt = 8f, defaultOpacity = 0.45f
    ),

    HIGHLIGHT(
        PdfAnnotationEntity.TOOL_HIGHLIGHT, "تظليل", Icons.Filled.Highlight,
        defaultOpacity = 0.35f, filled = true
    ),

    LINE(PdfAnnotationEntity.TOOL_LINE, "خط", Icons.Filled.Remove),

    ARROW(PdfAnnotationEntity.TOOL_ARROW, "سهم", Icons.AutoMirrored.Filled.CallMade),

    RECT(PdfAnnotationEntity.TOOL_RECT, "مستطيل", Icons.Filled.CropSquare),

    ELLIPSE(PdfAnnotationEntity.TOOL_CIRCLE, "دايرة", Icons.Filled.RadioButtonUnchecked),

    /**
     * سحابة المراجعة — الأداة اللي بتفرّق في رسمة هندسية.
     * الاستشاري بيتوقّع يشوف المنطقة المتغيّرة محوّطة بسحابة، مش بمستطيل.
     */
    CLOUD(PdfAnnotationEntity.TOOL_CLOUD, "سحابة مراجعة", Icons.Filled.Cloud, defaultWidthPt = 1.5f);

    val isDrawing: Boolean get() = id != null

    companion object {
        val drawing: List<PdfTool> = entries.filter { it.isDrawing }

        /** بيرجّع الأداة من الكود المتخزّن — للعلامات القديمة كمان. */
        fun fromId(id: String): PdfTool = entries.firstOrNull { it.id == id } ?: PEN
    }
}

/** لوحة ألوان التعليم. مختارة عشان تفضل مقروءة فوق رسمة أبيض/أسود. */
val PDF_PALETTE: List<Long> = listOf(
    0xFFFF3B30, // أحمر — الاعتراضات
    0xFFFF9F0A, // برتقالي
    0xFFFFD60A, // أصفر — التظليل
    0xFF34C759, // أخضر — المقبول
    0xFF00C7BE, // فيروزي
    0xFF0A84FF, // أزرق — الملاحظات
    0xFFAF52DE, // بنفسجي
    0xFF1C1C1E  // أسود
)

/** خطوات السُمك بالنقط — أربعة بس، ومحسوسة الفرق. */
val PDF_WIDTHS: List<Float> = listOf(1f, 2.5f, 5f, 10f)

/** خطوات الشفافية. */
val PDF_OPACITIES: List<Float> = listOf(1f, 0.6f, 0.35f)

/** إعدادات الأداة النشطة. */
data class ToolStyle(
    val colorArgb: Long = PDF_PALETTE[0],
    val widthPt: Float = 2.5f,
    val opacity: Float = 1f
)

// ═══════════════════════════════════════════════════════════ الرسم

/**
 * بيرسم علامة واحدة.
 *
 * [points] بالفعل محوّلة لإحداثيات الشاشة. [zoom] محتاجينه عشان نحوّل السُمك
 * من نقط لبكسل — العلامة بتكبر مع الرسمة زي ما لو كانت متحبّرة عليها.
 */
fun DrawScope.drawAnnotation(
    tool: PdfTool,
    color: Color,
    points: List<Offset>,
    widthPt: Float,
    opacity: Float,
    zoom: Float
) {
    if (points.size < 2) return
    val stroke = (widthPt * zoom).coerceAtLeast(1f)
    val paint = color.copy(alpha = opacity)
    val first = points.first()
    val last = points.last()
    val rect = Rect(
        min(first.x, last.x), min(first.y, last.y),
        maxOf(first.x, last.x), maxOf(first.y, last.y)
    )

    when (tool) {
        PdfTool.PAN, PdfTool.SELECT -> Unit

        PdfTool.HIGHLIGHT ->
            drawRect(paint, rect.topLeft, Size(rect.width, rect.height))

        PdfTool.RECT ->
            drawRect(paint, rect.topLeft, Size(rect.width, rect.height), style = Stroke(stroke))

        PdfTool.ELLIPSE ->
            drawOval(paint, rect.topLeft, Size(rect.width, rect.height), style = Stroke(stroke))

        PdfTool.LINE ->
            drawLine(paint, first, last, stroke, cap = StrokeCap.Round)

        PdfTool.ARROW -> {
            drawLine(paint, first, last, stroke, cap = StrokeCap.Round)
            val angle = atan2(last.y - first.y, last.x - first.x)
            val head = (stroke * 4f).coerceAtLeast(12f)
            listOf(angle + ARROW_SPREAD, angle - ARROW_SPREAD).forEach { a ->
                drawLine(
                    paint, last,
                    Offset(last.x - head * cos(a), last.y - head * sin(a)),
                    stroke, cap = StrokeCap.Round
                )
            }
        }

        PdfTool.PEN, PdfTool.MARKER ->
            drawPath(
                smoothPath(points), paint,
                style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

        PdfTool.CLOUD ->
            drawPath(cloudPath(rect, stroke), paint, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

/** رأس السهم — الزاوية بالتقدير الدائري. */
private const val ARROW_SPREAD = 2.6f

/**
 * سحابة مراجعة: أقواس متتالية على محيط المستطيل.
 *
 * نصف قطر القوس بيتحسب من مقاس المستطيل مش ثابت — سحابة بأقواس ثابتة على
 * مستطيل صغير بتبقى دايرة، وعلى مستطيل كبير بتبقى مسنّنة ناعمة مش سحابة.
 */
private fun cloudPath(rect: Rect, stroke: Float): Path {
    val path = Path()
    if (rect.width <= 0f || rect.height <= 0f) return path

    val radius = (min(rect.width, rect.height) / 8f)
        .coerceIn(stroke * 2.5f, 28f)
    val step = radius * 1.7f

    // نمشي على المحيط ونحطّ قوس كل خطوة
    fun arcsAlong(from: Offset, to: Offset) {
        val len = hypot(to.x - from.x, to.y - from.y)
        if (len <= 0f) return
        val count = (len / step).toInt().coerceAtLeast(1)
        val dx = (to.x - from.x) / count
        val dy = (to.y - from.y) / count
        // زاوية اتجاه الضلع عشان الأقواس تطلع لبرّه
        val dirDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        for (i in 0 until count) {
            val cx = from.x + dx * (i + 0.5f)
            val cy = from.y + dy * (i + 0.5f)
            path.addArc(
                Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                dirDeg - 170f,
                160f
            )
        }
    }

    val tl = Offset(rect.left, rect.top)
    val tr = Offset(rect.right, rect.top)
    val br = Offset(rect.right, rect.bottom)
    val bl = Offset(rect.left, rect.bottom)
    arcsAlong(tl, tr)
    arcsAlong(tr, br)
    arcsAlong(br, bl)
    arcsAlong(bl, tl)
    return path
}

/**
 * مسار ناعم من نقط الخط.
 *
 * `lineTo` بين كل نقطتين بيدّي خط مكسّر — بيبان كأنه مرسوم بمسطرة على
 * أجزاء، خصوصاً في الكتابة بالقلم. الطريقة هنا هي **منحنى تربيعي بنقط
 * المنتصف**: كل نقطة أصلية بتبقى نقطة تحكّم، والمنحنى بيعدّي من منتصف كل
 * ضلع.
 *
 * الاختيار ده مقصود بدل التنعيم بالمتوسّط المتحرّك: ده **مابيأخّرش**
 * الخط ورا القلم — كل نقطة بتدخل في الشكل أول ما توصل، ومفيش نافذة
 * استنّى. التنعيم اللي بيستنّى عيّنات جاية بيدّي خط أنعم شوية وإحساس
 * إن الحبر بيلحق القلم، وده أسوأ بكتير من ضلع مكسّر.
 *
 * الخط اللي فيه نقطتين بيتحوّل لخط مستقيم — مافيش منحنى من نقطتين.
 */
fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path

    val first = points.first()
    path.moveTo(first.x, first.y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    for (i in 1 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        path.quadraticBezierTo(
            current.x, current.y,
            (current.x + next.x) / 2f, (current.y + next.y) / 2f
        )
    }
    // آخر نقطة بتتوصّل بضلع مستقيم: المنحنى الأخير مالوش نقطة بعده
    // يعدّي من منتصفها، وسيبان الخط ناقص طرفه بيبان كخط مقطوع.
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}
