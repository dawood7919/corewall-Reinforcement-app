package com.corewall.qaqc.ui.takeoff

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.pdfengine.pagePointToScreen
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffMath
import com.corewall.qaqc.takeoff.TakeoffTool

/** سُمك خط البند على الشاشة. ثابت بصرياً — مش بيكبر مع التكبير. */
private const val STROKE = 2.5f
private const val FILL_ALPHA = 0.22f
private const val MARKER_RADIUS = 7f

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
    selectedId: String?
) {
    for (item in items) {
        if (!item.visible || item.page != page) continue
        if (item.tool == TakeoffTool.DEDUCT) continue   // بتترسم كفتحة في أبوها

        // اللون متخزّن ARGB في `Long`. الـor بيضمن العتامة الكاملة حتى
        // لو الصف القديم اتخزّن من غير قناة alpha.
        val paint = Color((item.colorArgb or 0xFF000000L).toInt())
        val selected = item.id == selectedId
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

            TakeoffTool.DEDUCT -> Unit
        }

        if (selected) drawHandles(state, page, item, paint)
    }
}

/** مسار واحد بكل الحلقات — القاعدة even-odd هي اللي بتعمل الفتحات. */
private fun DrawScope.compoundPath(
    state: PdfViewerState,
    page: Int,
    rings: List<List<com.corewall.qaqc.takeoff.TakeoffPoint>>
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

/** المسوّدة اللي بتتبني دلوقتي — خط متقطّع بصرياً عن المحفوظ. */
fun DrawScope.drawTakeoffDraft(
    points: List<Offset>,
    tool: TakeoffTool,
    colour: Color
) {
    when (tool) {
        TakeoffTool.COUNT -> points.forEach { drawCircle(colour, MARKER_RADIUS, it) }
        else -> {
            if (points.size >= 2) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    if (tool == TakeoffTool.AREA || tool == TakeoffTool.DEDUCT) close()
                }
                if (tool == TakeoffTool.AREA || tool == TakeoffTool.DEDUCT) {
                    drawPath(path, colour.copy(alpha = FILL_ALPHA))
                }
                drawPath(
                    path, colour,
                    style = Stroke(STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            points.forEach { drawCircle(colour, 4f, it) }
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
    TakeoffTool.LENGTH -> "%.2f م".format(value)
    else -> "%.2f م²".format(value)
}

/** كمية بند بعد الخصومات — اختصار للشاشة. */
fun netOf(
    item: TakeoffItem,
    all: List<TakeoffItem>,
    page: com.corewall.qaqc.takeoff.PageGeometry
): Double = TakeoffMath.netQuantity(item, all, page)
