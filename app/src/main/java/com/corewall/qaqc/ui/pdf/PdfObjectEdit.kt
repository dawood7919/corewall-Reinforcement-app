package com.corewall.qaqc.ui.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.corewall.qaqc.pdfengine.PdfViewerState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * تحديد العلامات وتعديلها — الهندسة.
 *
 * كل حسابات هذا الملف بالإحداثيات **المنسّبة** (٠..١ من عرض/ارتفاع
 * الصفحة)، لأن ده الشكل اللي العلامات متخزّنة بيه. الفايدة إن التعديل
 * مستقل تماماً عن التكبير والتمرير: تكبّر شكل وانت على ٠.٥× وتلاقيه
 * مظبوط لما تقرّب على ٨×.
 *
 * التحويل بيتعمل على **الصندوق**: كل شكل — سحابة، مستطيل، خربشة قلم —
 * ليه صندوق حاوي، وشدّ ركن الصندوق بيعيد توزيع كل نقطة بنفس النسبة.
 * ده اللي بيخلّي "اظبط أبعاده كمستطيل" تشتغل على أي شكل، مش على
 * المستطيلات بس.
 */

/** ركن الصندوق اللي ماسكه، أو تحريك الصندوق كله. */
enum class BoxHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

/** أقل مقاس للصندوق — من غيره الشدّ الزيادة بيقلب الشكل أو يصفّره. */
private const val MIN_SPAN = 0.004f

/** حدود مجموعة نقط مسطّحة (x,y,x,y…). */
fun annotationBounds(flat: List<Float>): Rect? {
    if (flat.size < 2) return null
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    var i = 0
    while (i + 1 < flat.size) {
        val x = flat[i]
        val y = flat[i + 1]
        left = min(left, x); right = max(right, x)
        top = min(top, y); bottom = max(bottom, y)
        i += 2
    }
    return Rect(left, top, right, bottom)
}

/** اتحاد صناديق — صندوق التحديد كله. */
fun unionBounds(boxes: List<Rect>): Rect? {
    if (boxes.isEmpty()) return null
    var r = boxes.first()
    boxes.drop(1).forEach {
        r = Rect(min(r.left, it.left), min(r.top, it.top), max(r.right, it.right), max(r.bottom, it.bottom))
    }
    return r
}

/**
 * هل النقطة بتلمس الشكل ده؟
 *
 * الأشكال المملوءة (التظليل) بتتمسك من جوّها، والباقي من **خطوطه** —
 * مستطيل فاضي كبير المفروض ماياخدش كل نقرة جوّه، وإلا بقى مستحيل تختار
 * حاجة تحته.
 */
fun annotationHit(
    tool: PdfTool,
    flat: List<Float>,
    nx: Float,
    ny: Float,
    tolerance: Float
): Boolean {
    val bounds = annotationBounds(flat) ?: return false
    val grown = Rect(
        bounds.left - tolerance, bounds.top - tolerance,
        bounds.right + tolerance, bounds.bottom + tolerance
    )
    if (!grown.contains(Offset(nx, ny))) return false

    if (tool.filled) return true

    return when {
        tool.freeform || tool == PdfTool.LINE || tool == PdfTool.ARROW -> {
            var i = 0
            var hit = false
            while (i + 3 < flat.size && !hit) {
                hit = distanceToSegment(
                    nx, ny, flat[i], flat[i + 1], flat[i + 2], flat[i + 3]
                ) <= tolerance
                i += 2
            }
            // خط من نقطة واحدة (نقرة) — قرّبنا منها كفاية.
            hit || (flat.size == 2 && hypot(nx - flat[0], ny - flat[1]) <= tolerance)
        }
        // مستطيل/دايرة/سحابة: قريب من الحافة، مش أي مكان جوّه.
        else -> {
            val inner = Rect(
                bounds.left + tolerance, bounds.top + tolerance,
                bounds.right - tolerance, bounds.bottom - tolerance
            )
            !(inner.width > 0f && inner.height > 0f && inner.contains(Offset(nx, ny)))
        }
    }
}

private fun distanceToSegment(
    px: Float, py: Float,
    ax: Float, ay: Float,
    bx: Float, by: Float
): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSq = dx * dx + dy * dy
    if (lengthSq <= 0f) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

/**
 * بينقل نقط من صندوق لصندوق.
 *
 * الصندوق اللي عرضه أو ارتفاعه صفر (خط أفقي تماماً مثلاً) مابيتقسمش عليه —
 * النقط بتتزحلق بدل ما تتمدّد، وده السلوك المتوقّع.
 */
fun transformPoints(flat: List<Float>, from: Rect, to: Rect): List<Float> {
    val sx = if (from.width > 1e-6f) to.width / from.width else 1f
    val sy = if (from.height > 1e-6f) to.height / from.height else 1f
    val out = ArrayList<Float>(flat.size)
    var i = 0
    while (i + 1 < flat.size) {
        out += (to.left + (flat[i] - from.left) * sx).coerceIn(0f, 1f)
        out += (to.top + (flat[i + 1] - from.top) * sy).coerceIn(0f, 1f)
        i += 2
    }
    return out
}

/** بيطبّق سحب مقبض على الصندوق، مع منع الانقلاب. */
fun BoxHandle.applyTo(box: Rect, dx: Float, dy: Float): Rect {
    val r = when (this) {
        BoxHandle.MOVE -> Rect(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy)
        BoxHandle.TOP_LEFT -> Rect(box.left + dx, box.top + dy, box.right, box.bottom)
        BoxHandle.TOP_RIGHT -> Rect(box.left, box.top + dy, box.right + dx, box.bottom)
        BoxHandle.BOTTOM_LEFT -> Rect(box.left + dx, box.top, box.right, box.bottom + dy)
        BoxHandle.BOTTOM_RIGHT -> Rect(box.left, box.top, box.right + dx, box.bottom + dy)
    }
    if (this == BoxHandle.MOVE) {
        // التحريك بيفضل جوّه الصفحة بدل ما نقصّ الشكل عند الحافة.
        val w = r.width
        val h = r.height
        val left = r.left.coerceIn(0f, (1f - w).coerceAtLeast(0f))
        val top = r.top.coerceIn(0f, (1f - h).coerceAtLeast(0f))
        return Rect(left, top, left + w, top + h)
    }
    return Rect(
        min(r.left, r.right - MIN_SPAN).coerceIn(0f, 1f),
        min(r.top, r.bottom - MIN_SPAN).coerceIn(0f, 1f),
        max(r.right, r.left + MIN_SPAN).coerceIn(0f, 1f),
        max(r.bottom, r.top + MIN_SPAN).coerceIn(0f, 1f)
    )
}

/** المقبض اللي النقطة دي واقعة عليه، أو null. */
fun handleAt(box: Rect, nx: Float, ny: Float, grabRadius: Float): BoxHandle? {
    val corners = listOf(
        BoxHandle.TOP_LEFT to Offset(box.left, box.top),
        BoxHandle.TOP_RIGHT to Offset(box.right, box.top),
        BoxHandle.BOTTOM_LEFT to Offset(box.left, box.bottom),
        BoxHandle.BOTTOM_RIGHT to Offset(box.right, box.bottom)
    )
    corners.forEach { (handle, at) ->
        if (hypot(nx - at.x, ny - at.y) <= grabRadius) return handle
    }
    val inside = nx >= box.left - grabRadius && nx <= box.right + grabRadius &&
        ny >= box.top - grabRadius && ny <= box.bottom + grabRadius
    return if (inside) BoxHandle.MOVE else null
}

/** تقاطع صندوقين — أداة التحديد بالمستطيل بتاخد أي شكل بيلمسه. */
fun Rect.touches(other: Rect): Boolean =
    left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

/** مستطيل من نقطتين بأي ترتيب. */
fun rectOf(a: Offset, b: Offset): Rect =
    Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))

/**
 * نقطة على الشاشة → إحداثي منسّب جوّه **صفحة معيّنة**.
 *
 * مختلفة عن `pageHit`: دي مابترجعش null لما النقطة تخرج بره الصفحة —
 * بتقصّها على الحافة. السحب بيعدّي حافة الصفحة طول الوقت، و`pageHit`
 * بترجع null هناك فالسحب كان هيتقطع في نص حركة.
 */
fun PdfViewerState.pointOnPage(page: Int, screen: Offset): Offset? {
    val slot = layout.slotAt(page) ?: return null
    val doc = screenToDoc(screen)
    return Offset(
        ((doc.x - slot.left) / slot.size.width).coerceIn(0f, 1f),
        ((doc.y - slot.top) / slot.size.height).coerceIn(0f, 1f)
    )
}

/** نصف قطر المسك بالوحدات المنسّبة — ثابت بالبكسل على الشاشة. */
fun PdfViewerState.normalisedTolerance(page: Int, screenPx: Float): Float {
    val slot = layout.slotAt(page) ?: return 0.01f
    val width = slot.size.width * zoom
    return if (width > 1f) (screenPx / width).coerceIn(0.002f, 0.08f) else 0.01f
}

/** فرق بسيط بيمنع حفظ تعديل ما اتحركش فعلاً. */
fun Rect.movedFrom(other: Rect): Boolean =
    abs(left - other.left) > 1e-4f || abs(top - other.top) > 1e-4f ||
        abs(right - other.right) > 1e-4f || abs(bottom - other.bottom) > 1e-4f
