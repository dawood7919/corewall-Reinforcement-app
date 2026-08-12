package com.corewall.qaqc.ui.takeoff

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.pdfengine.pagePointToScreen
import com.corewall.qaqc.takeoff.TakeoffAnnotation
import com.corewall.qaqc.takeoff.TakeoffAnnotationType
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffMath
import com.corewall.qaqc.takeoff.TakeoffPoint
import com.corewall.qaqc.takeoff.TakeoffTool
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** سُمك خط البند على الشاشة. ثابت بصرياً — مش بيكبر مع التكبير. */
private const val STROKE = 2.5f
private const val FILL_ALPHA = 0.22f
private const val MARKER_RADIUS = 7f
private const val LABEL_PAD = 10f
private const val PROGRESS_BAR_H = 6f

/**
 * رسم بنود الحصر فوق الرسمة.
 *
 * ## الفتحات: مسار مركّب بقاعدة even-odd
 *
 * ده الفخ رقم ٢ في المواصفة. الطريقة السهلة لعمل "فتحة" في شكل هي إنك
 * ترسم فوقها بوضع مسح (`DST_OUT` أو ما شابه) — وده في نسخة الويب **خرم
 * صورة الصفحة نفسها** مش تعبئة الشكل بس.
 *
 * الطريقة الصح: مسار واحد فيه حلقات الأب **و** حلقات الخصم، وبيتملى مرة
 * واحدة بقاعدة even-odd. القاعدة دي بتقول: النقطة جوّه الشكل لو الشعاع
 * الخارج منها بيقطع عدد **فردي** من الحدود. الحلقة اللي جوّه حلقة تانية
 * بتقلب العدّ فبتطلع فتحة — رياضياً، من غير أي لمس لأي بكسل برّه الشكل.
 */
fun DrawScope.drawTakeoffItems(
    state: PdfViewerState,
    items: List<TakeoffItem>,
    page: Int,
    deductionsOf: (TakeoffItem) -> List<TakeoffItem>,
    /** كمية البند الصافية — للاسم/الرقم فوق الشكل. */
    netQuantityOf: (TakeoffItem) -> Double,
    selectedId: String?,
    /** تحديد جماعي (بمستطيل) — نفس تمييز التحديد المفرد، بلا مقابض رؤوس. */
    multiSelectedIds: Set<String> = emptySet()
) {
    for (item in items) {
        if (!item.visible || item.page != page) continue
        if (item.tool == TakeoffTool.DEDUCT) continue   // بتترسم كفتحة في أبوها

        // اللون متخزّن ARGB في `Long`. الـor بيضمن العتامة الكاملة حتى
        // لو الصف القديم اتخزّن من غير قناة alpha.
        val paint = Color((item.colorArgb or 0xFF000000L).toInt())
        val selected = item.id == selectedId || item.id in multiSelectedIds
        val width = if (selected) STROKE * 1.8f else STROKE

        when (item.tool) {
            TakeoffTool.AREA -> {
                val rings = buildList {
                    add(item.verts)
                    addAll(item.extraRings)
                    // الخصومات بتدخل نفس المسار — هي اللي بتعمل الفتحة.
                    deductionsOf(item).filter { it.visible }.forEach { hole ->
                        add(hole.verts)
                        addAll(hole.extraRings)
                    }
                }
                val path = compoundPath(state, page, rings)
                drawPath(path, paint.copy(alpha = FILL_ALPHA))
                drawPath(path, paint, style = Stroke(width, join = StrokeJoin.Round))
            }

            TakeoffTool.LENGTH -> {
                (listOf(item.verts) + item.extraSegments).forEach { segment ->
                    val screen = segment.mapNotNull {
                        state.pagePointToScreen(page, it.x.toFloat(), it.y.toFloat())
                    }
                    if (screen.size >= 2) {
                        val path = Path().apply {
                            moveTo(screen.first().x, screen.first().y)
                            screen.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path, paint,
                            style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            TakeoffTool.COUNT -> {
                item.verts.forEach { marker ->
                    state.pagePointToScreen(page, marker.x.toFloat(), marker.y.toFloat())?.let { p ->
                        drawCircle(paint, MARKER_RADIUS, p)
                        drawCircle(Color.White, MARKER_RADIUS * 0.4f, p)
                    }
                }
            }

            // زي المساحة بالظبط — نفس مسار even-odd للخصومات — بس خط
            // متقطّع عشان تتفرّق بصريًا عن مساحة عادية من نظرة واحدة.
            TakeoffTool.VOLUME -> {
                val rings = buildList {
                    add(item.verts)
                    addAll(item.extraRings)
                    deductionsOf(item).filter { it.visible }.forEach { hole ->
                        add(hole.verts)
                        addAll(hole.extraRings)
                    }
                }
                val path = compoundPath(state, page, rings)
                drawPath(path, paint.copy(alpha = FILL_ALPHA))
                drawPath(
                    path, paint,
                    style = Stroke(
                        width, join = StrokeJoin.Round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 8f))
                    )
                )
            }

            // مربّع بدل دائرة العدّ — تمييز بصري إن كل علامة هنا حجم
            // عمود واحد مش قطعة واحدة.
            TakeoffTool.COLUMN -> {
                item.verts.forEach { marker ->
                    state.pagePointToScreen(page, marker.x.toFloat(), marker.y.toFloat())?.let { p ->
                        val half = MARKER_RADIUS
                        drawRect(
                            paint,
                            topLeft = Offset(p.x - half, p.y - half),
                            size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f)
                        )
                        drawRect(
                            Color.White,
                            topLeft = Offset(p.x - half * 0.4f, p.y - half * 0.4f),
                            size = androidx.compose.ui.geometry.Size(half * 0.8f, half * 0.8f)
                        )
                    }
                }
            }

            // ثلاث نقط: طرف، طرف، إزاحة خط القياس. النص بيترسم كجزء من
            // الخط نفسه، مش من آلية التسمية العامة تحت.
            TakeoffTool.DIMENSION -> {
                if (item.verts.size >= 3) {
                    drawDimension(
                        state, page, item.verts[0], item.verts[1], item.verts[2],
                        formatQuantity(TakeoffTool.DIMENSION, netQuantityOf(item)),
                        paint
                    )
                }
            }

            TakeoffTool.DEDUCT -> Unit
        }

        // التسمية: اسم + كمية فوق الشكل. البُعد ليه رسمه الخاص فوق،
        // والخصم بيترسم كفتحة مش بند مستقل — الاتنين مستبعدين هنا.
        if (item.tool != TakeoffTool.DIMENSION) {
            drawTakeoffLabel(state, page, item, formatQuantity(item.tool, netQuantityOf(item)), paint)
        }

        // المقابض بس للتحديد المفرد — التحديد الجماعي بمستطيل غرضه
        // الحذف الجماعي، مش تعديل الرؤوس.
        if (item.id == selectedId) drawHandles(state, page, item, paint)
    }
}

/** مسار واحد بكل الحلقات — القاعدة even-odd هي اللي بتعمل الفتحات. */
private fun DrawScope.compoundPath(
    state: PdfViewerState,
    page: Int,
    rings: List<List<TakeoffPoint>>
): Path {
    val path = Path()
    path.fillType = PathFillType.EvenOdd
    for (ring in rings) {
        if (ring.size < 3) continue
        val screen = ring.mapNotNull {
            state.pagePointToScreen(page, it.x.toFloat(), it.y.toFloat())
        }
        if (screen.size < 3) continue
        path.moveTo(screen.first().x, screen.first().y)
        screen.drop(1).forEach { path.lineTo(it.x, it.y) }
        path.close()
    }
    return path
}

/** مقابض الرؤوس على البند المحدّد — بتبان على الأجزاء المتجمّعة كمان. */
private fun DrawScope.drawHandles(
    state: PdfViewerState,
    page: Int,
    item: TakeoffItem,
    paint: Color
) {
    val all = buildList {
        addAll(item.verts)
        item.extraRings.forEach { addAll(it) }
        item.extraSegments.forEach { addAll(it) }
    }
    all.forEach { v ->
        state.pagePointToScreen(page, v.x.toFloat(), v.y.toFloat())?.let { p ->
            drawCircle(Color.White, 5f, p)
            drawCircle(paint, 5f, p, style = Stroke(2f))
        }
    }
}

/**
 * المسوّدة اللي بتتبني دلوقتي.
 *
 * بتاخد نقط الصفحة المنسّبة زي البنود المحفوظة بالظبط، وبتحوّلها للشاشة
 * مع كل إطار. يعني المسوّدة بتتحرّك وتكبّر مع الرسمة — لو كانت متخزّنة
 * بإحداثيات الشاشة كانت هتفضل واقفة مكانها والرسمة بتتحرّك تحتها.
 */
fun DrawScope.drawTakeoffDraft(
    state: PdfViewerState,
    page: Int,
    points: List<TakeoffPoint>,
    tool: TakeoffTool,
    colour: Color
) {
    val screen = points.mapNotNull {
        state.pagePointToScreen(page, it.x.toFloat(), it.y.toFloat())
    }
    if (screen.isEmpty()) return

    when (tool) {
        TakeoffTool.COUNT, TakeoffTool.COLUMN -> screen.forEach { drawCircle(colour, MARKER_RADIUS, it) }
        else -> {
            val closes = tool == TakeoffTool.AREA || tool == TakeoffTool.DEDUCT || tool == TakeoffTool.VOLUME
            if (screen.size >= 2) {
                val path = Path().apply {
                    moveTo(screen.first().x, screen.first().y)
                    screen.drop(1).forEach { lineTo(it.x, it.y) }
                    if (closes) close()
                }
                if (closes) drawPath(path, colour.copy(alpha = FILL_ALPHA))
                drawPath(
                    path, colour,
                    style = Stroke(STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            screen.forEach { drawCircle(colour, 4f, it) }
        }
    }
}

/** ألوان البنود — مميّزة عن بعض وواضحة فوق رسمة أبيض وأسود. */
val TAKEOFF_PALETTE = listOf(
    0xFFE53935L, 0xFF1E88E5L, 0xFF43A047L, 0xFFFB8C00L,
    0xFF8E24AAL, 0xFF00ACC1L, 0xFFD81B60L, 0xFF6D4C41L
)

/** بيرجّع الكمية بصياغة مقروءة حسب الأداة. */
fun formatQuantity(tool: TakeoffTool, value: Double): String = when (tool) {
    TakeoffTool.COUNT -> "${value.toInt()}"
    TakeoffTool.LENGTH, TakeoffTool.DIMENSION -> "%.2f م".format(value)
    TakeoffTool.VOLUME, TakeoffTool.COLUMN -> "%.2f م³".format(value)
    else -> "%.2f م²".format(value)
}

/** كمية بند بعد الخصومات — اختصار للشاشة. */
fun netOf(
    item: TakeoffItem,
    all: List<TakeoffItem>,
    page: com.corewall.qaqc.takeoff.PageGeometry
): Double = TakeoffMath.netQuantity(item, all, page)

// ═══════════════════════════════════════════════ التسمية فوق الشكل
//
// `drawText` من Compose محتاج TextMeasurer قابل للاستدعاء بس جوّه
// composable، ودالة الرسم هنا `DrawScope` عادية بتتنادى مرّات كتير في
// الإطار الواحد. الكتابة بمحرّك أندرويد المباشر (`nativeCanvas`) أرخص
// وبتدّي تشكيل عربي سليم. الـPaint متعمولة مرة واحدة برّه الدالة عن
// قصد — تخصيص كائنين في كل نداء وسط التكبير بيولّع الـGC.

private val labelNamePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    textSize = 30f
    isFakeBoldText = true
    color = android.graphics.Color.WHITE
}
private val labelQtyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    textSize = 26f
    color = android.graphics.Color.WHITE
}
private val labelBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.argb(210, 18, 20, 26)
}
private val progressBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.argb(90, 255, 255, 255)
}
private val progressFillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
private val labelRectF = android.graphics.RectF()

/** نقطة ارتساء التسمية — مركز تقريبي للشكل، أو أول علامة للعدّ/العمود. */
private fun labelAnchor(item: TakeoffItem): TakeoffPoint? {
    val pts = item.verts
    if (pts.isEmpty()) return null
    return when (item.tool) {
        TakeoffTool.AREA, TakeoffTool.VOLUME -> {
            var x = 0.0
            var y = 0.0
            pts.forEach { x += it.x; y += it.y }
            TakeoffPoint(x / pts.size, y / pts.size)
        }
        TakeoffTool.LENGTH -> pts[pts.size / 2]
        else -> pts.first()
    }
}

/**
 * الاسم والكمية فوق الشكل — وشريط تقدّم صغير لو البند عنده نسبة تنفيذ.
 *
 * العدّ والعمود بيطلعوا سطر واحد "الاسم (العدد)" بدل سطرين، مطابقة
 * لتسمية نسخة الويب — سطرين لعلامة نقطة واحدة بيبان زيادة بصرياً.
 */
private fun DrawScope.drawTakeoffLabel(
    state: PdfViewerState,
    page: Int,
    item: TakeoffItem,
    quantityText: String,
    accent: Color
) {
    val anchor = labelAnchor(item) ?: return
    val p = state.pagePointToScreen(page, anchor.x.toFloat(), anchor.y.toFloat()) ?: return

    val isMarkerTool = item.tool == TakeoffTool.COUNT || item.tool == TakeoffTool.COLUMN
    val lines = if (isMarkerTool) listOf("${item.name} (${item.verts.size})") else listOf(item.name, quantityText)

    val widths = lines.mapIndexed { i, line -> (if (i == 0) labelNamePaint else labelQtyPaint).measureText(line) }
    val boxWidth = (widths.maxOrNull() ?: 0f) + LABEL_PAD * 2
    val lineHeight = labelNamePaint.textSize * 1.2f
    val hasProgress = item.progressPercent != null &&
        (item.tool == TakeoffTool.AREA || item.tool == TakeoffTool.LENGTH || item.tool == TakeoffTool.VOLUME)
    val boxHeight = lineHeight * lines.size + LABEL_PAD * 2 + if (hasProgress) PROGRESS_BAR_H + LABEL_PAD else 0f

    val left = p.x - boxWidth / 2f
    val top = p.y - boxHeight / 2f
    labelRectF.set(left, top, left + boxWidth, top + boxHeight)

    drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(labelRectF, 8f, 8f, labelBgPaint)
        var ty = top + LABEL_PAD + labelNamePaint.textSize
        lines.forEachIndexed { i, line ->
            val linePaint = if (i == 0) labelNamePaint else labelQtyPaint
            drawText(line, p.x, ty, linePaint)
            ty += lineHeight
        }
        if (hasProgress) {
            val pct = (item.progressPercent ?: 0.0).coerceIn(0.0, 100.0).toFloat() / 100f
            val barTop = top + boxHeight - LABEL_PAD - PROGRESS_BAR_H
            val barLeft = left + LABEL_PAD
            val barRight = left + boxWidth - LABEL_PAD
            drawRoundRect(barLeft, barTop, barRight, barTop + PROGRESS_BAR_H, 3f, 3f, progressBgPaint)
            progressFillPaint.color = accent.toArgb()
            drawRoundRect(
                barLeft, barTop, barLeft + (barRight - barLeft) * pct, barTop + PROGRESS_BAR_H,
                3f, 3f, progressFillPaint
            )
        }
    }
}

// ═══════════════════════════════════════════════ خط القياس المرجعي

private val dimTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    textSize = 24f
}
private const val DIM_GAP = 4f
private const val DIM_OVERSHOOT = 6f
private const val DIM_ARROW = 10f

/**
 * خط قياس بأسلوب AutoCAD: خطوط امتداد من الطرفين لخط القياس المُزاح،
 * سهمين، ونص فوق منتصفه.
 *
 * [pOffset] مش بيتحسب منه رقم — غرضه بس تحديد بعد خط القياس عن الخط
 * الأصلي p1-p2 بصريًا (أقرب نقطة إسقاط عمودي عليه).
 */
private fun DrawScope.drawDimension(
    state: PdfViewerState,
    page: Int,
    p1: TakeoffPoint,
    p2: TakeoffPoint,
    pOffset: TakeoffPoint,
    text: String,
    color: Color
) {
    val s1 = state.pagePointToScreen(page, p1.x.toFloat(), p1.y.toFloat()) ?: return
    val s2 = state.pagePointToScreen(page, p2.x.toFloat(), p2.y.toFloat()) ?: return
    val sO = state.pagePointToScreen(page, pOffset.x.toFloat(), pOffset.y.toFloat()) ?: return

    val dx = s2.x - s1.x
    val dy = s2.y - s1.y
    val len = hypot(dx, dy)
    if (len < 1f) return
    val nx = -dy / len
    val ny = dx / len
    val offsetDist = (sO.x - s1.x) * nx + (sO.y - s1.y) * ny

    val d1 = Offset(s1.x + nx * offsetDist, s1.y + ny * offsetDist)
    val d2 = Offset(s2.x + nx * offsetDist, s2.y + ny * offsetDist)
    val sign = if (offsetDist >= 0f) 1f else -1f

    // خطوط الامتداد: فجوة صغيرة عند الطرف الأصلي، وتخطّي بسيط بعد خط القياس.
    fun extension(from: Offset, to: Offset) {
        val gapStart = Offset(from.x + nx * DIM_GAP * sign, from.y + ny * DIM_GAP * sign)
        val overshoot = Offset(to.x + nx * DIM_OVERSHOOT * sign, to.y + ny * DIM_OVERSHOOT * sign)
        drawLine(color, gapStart, overshoot, strokeWidth = 1.4f)
    }
    extension(s1, d1)
    extension(s2, d2)

    drawLine(color, d1, d2, strokeWidth = 1.6f)
    drawArrowhead(d1, d2, color)
    drawArrowhead(d2, d1, color)

    val mid = Offset((d1.x + d2.x) / 2f, (d1.y + d2.y) / 2f)
    val angle = atan2(dy, dx)
    drawContext.canvas.nativeCanvas.apply {
        save()
        rotate(Math.toDegrees(angle.toDouble()).toFloat(), mid.x, mid.y)
        dimTextPaint.color = color.toArgb()
        drawText(text, mid.x, mid.y - 6f, dimTextPaint)
        restore()
    }
}

/** معاينة سحابة/سهم وهو بيتبني — خط بسيط، الشكل النهائي بيبان بعد الحفظ. */
fun DrawScope.drawTakeoffAnnotationDraft(
    state: PdfViewerState,
    page: Int,
    points: List<TakeoffPoint>,
    colour: Color
) {
    val screen = points.mapNotNull { state.pagePointToScreen(page, it.x.toFloat(), it.y.toFloat()) }
    if (screen.isEmpty()) return
    if (screen.size >= 2) {
        val path = Path().apply {
            moveTo(screen.first().x, screen.first().y)
            screen.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path, colour,
            style = Stroke(
                STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
            )
        )
    }
    screen.forEach { drawCircle(colour, 4f, it) }
}

// ═══════════════════════════════════════════════ التعليقات
//
// طبقة توضيح بصري مستقلة تمامًا عن بنود الحصر — مفيش كمية ولا فئة ولا
// دخول في أي إجمالي، فمفيش أي تقاطع مع منطق [drawTakeoffItems] فوق.

/** رسم كل تعليقات صفحة — سحابة/سهم/نص. */
fun DrawScope.drawTakeoffAnnotations(
    state: PdfViewerState,
    annotations: List<TakeoffAnnotation>,
    page: Int,
    selectedId: String?
) {
    for (a in annotations) {
        if (!a.visible || a.page != page) continue
        val color = Color((a.colorArgb or 0xFF000000L).toInt())
        val width = if (a.id == selectedId) STROKE * 1.8f else STROKE
        when (a.type) {
            TakeoffAnnotationType.CLOUD -> drawCloud(state, page, a.verts, color, width)
            TakeoffAnnotationType.ARROW ->
                if (a.verts.size >= 2) drawAnnotationArrow(state, page, a.verts[0], a.verts[1], color, width)
            TakeoffAnnotationType.TEXT -> drawAnnotationText(state, page, a.verts.firstOrNull(), a.text, color)
        }
    }
}

private const val CLOUD_BUMP_SPACING = 26f
private const val CLOUD_BUMP_H = 9f

/** حدّ سحابة متعرّج — كل ضلع بيتقسّم لقطع صغيرة، وكل قطعة بتنتفخ لبرّه. */
private fun DrawScope.drawCloud(
    state: PdfViewerState,
    page: Int,
    verts: List<TakeoffPoint>,
    color: Color,
    width: Float
) {
    val screen = verts.mapNotNull { state.pagePointToScreen(page, it.x.toFloat(), it.y.toFloat()) }
    if (screen.size < 2) return
    val closed = screen.size >= 3
    val ring = if (closed) screen + screen.first() else screen
    val path = Path()
    path.moveTo(ring.first().x, ring.first().y)
    for (i in 0 until ring.size - 1) {
        val a = ring[i]
        val b = ring[i + 1]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len < 1f) continue
        val bumps = (len / CLOUD_BUMP_SPACING).toInt().coerceAtLeast(1)
        val nx = -dy / len
        val ny = dx / len
        for (s in 0 until bumps) {
            val t0 = s.toFloat() / bumps
            val t1 = (s + 1f) / bumps
            val p1x = a.x + dx * t1
            val p1y = a.y + dy * t1
            val midX = (a.x + dx * t0 + p1x) / 2f + nx * CLOUD_BUMP_H
            val midY = (a.y + dy * t0 + p1y) / 2f + ny * CLOUD_BUMP_H
            path.quadraticBezierTo(midX, midY, p1x, p1y)
        }
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawAnnotationArrow(
    state: PdfViewerState,
    page: Int,
    p1: TakeoffPoint,
    p2: TakeoffPoint,
    color: Color,
    width: Float
) {
    val s1 = state.pagePointToScreen(page, p1.x.toFloat(), p1.y.toFloat()) ?: return
    val s2 = state.pagePointToScreen(page, p2.x.toFloat(), p2.y.toFloat()) ?: return
    drawLine(color, s1, s2, strokeWidth = width, cap = StrokeCap.Round)
    drawArrowhead(s2, s1, color)
}

private val annTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    textSize = 28f
}
private val annBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.argb(220, 30, 24, 10)
}
private val annRectF = android.graphics.RectF()

private fun DrawScope.drawAnnotationText(
    state: PdfViewerState,
    page: Int,
    point: TakeoffPoint?,
    text: String,
    color: Color
) {
    if (point == null || text.isBlank()) return
    val p = state.pagePointToScreen(page, point.x.toFloat(), point.y.toFloat()) ?: return
    annTextPaint.color = color.toArgb()
    val w = annTextPaint.measureText(text)
    val pad = 8f
    annRectF.set(
        p.x - w / 2f - pad, p.y - annTextPaint.textSize / 2f - pad,
        p.x + w / 2f + pad, p.y + annTextPaint.textSize / 2f + pad
    )
    drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(annRectF, 6f, 6f, annBgPaint)
        drawText(text, p.x, p.y + annTextPaint.textSize / 3f, annTextPaint)
    }
}

/** رأس سهم مثلّث عند [tip] متّجه بعيد عن [from]. */
private fun DrawScope.drawArrowhead(tip: Offset, from: Offset, color: Color) {
    val angle = atan2(tip.y - from.y, tip.x - from.x)
    val spread = 0.4
    val a1 = Offset(
        tip.x - DIM_ARROW * cos(angle - spread).toFloat(),
        tip.y - DIM_ARROW * sin(angle - spread).toFloat()
    )
    val a2 = Offset(
        tip.x - DIM_ARROW * cos(angle + spread).toFloat(),
        tip.y - DIM_ARROW * sin(angle + spread).toFloat()
    )
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(a1.x, a1.y)
        lineTo(a2.x, a2.y)
        close()
    }
    drawPath(path, color)
}
