package com.corewall.qaqc.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corewall.qaqc.data.model.PlanData
import com.corewall.qaqc.data.model.PlanElement
import kotlin.math.min

data class PlanStroke(val color: Color, val widthDp: Float, val dashed: Boolean)

data class PlanLabel(
    val text: String,
    val color: Color,
    /**
     * true: حجم النص نسبة من البلان نفسه (بيكبر ويصغر مع الزوم) —
     * false: حجم ثابت على الشاشة مهما كان الزوم.
     */
    val scaleWithPlan: Boolean = false,
    val bold: Boolean = true
)

/**
 * مسقط تفاعلي عام: Pinch-to-zoom + Pan، دبل-تاب للتكبير/الرجوع،
 * ولمسة واحدة بتختار عنصر. الألوان والحدود والليبلات بتيجي من بره
 * عن طريق lambdas — عشان كل أداة (Reinforcement / Counting / …) ترسم بطريقتها.
 */
@Composable
fun InteractivePlanCanvas(
    planData: PlanData,
    selectedId: String?,
    backgroundColor: Color,
    selectionColor: Color,
    fillFor: (PlanElement) -> Color,
    strokeFor: (PlanElement) -> PlanStroke?,
    labelFor: (PlanElement) -> PlanLabel?,
    onTapElement: (PlanElement) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val vb = planData.viewBoxRect // minX, minY, w, h

    fun baseTransform(size: IntSize): Pair<Float, Offset> {
        if (size.width == 0 || size.height == 0) return 1f to Offset.Zero
        val base = (min(size.width / vb[2], size.height / vb[3]) * 0.97).toFloat()
        val off = Offset(
            ((size.width - vb[2] * base) / 2).toFloat(),
            ((size.height - vb[3] * base) / 2).toFloat()
        )
        return base to off
    }

    fun screenRect(el: PlanElement, base: Float, baseOff: Offset): Rect {
        val x = ((el.x - vb[0]) * base + baseOff.x).toFloat() * scale + offset.x
        val y = ((el.y - vb[1]) * base + baseOff.y).toFloat() * scale + offset.y
        val w = (el.width * base).toFloat() * scale
        val h = (el.height * base).toFloat() * scale
        return Rect(x, y, x + w, y + h)
    }

    fun hitTest(pos: Offset): PlanElement? {
        val (base, baseOff) = baseTransform(canvasSize)
        val slop = 12f
        return planData.elements.lastOrNull { el ->
            val r = screenRect(el, base, baseOff)
            Rect(r.left - slop, r.top - slop, r.right + slop, r.bottom + slop).contains(pos)
        }
    }

    Canvas(
        modifier = modifier
            .background(backgroundColor)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 15f)
                    val z = newScale / scale
                    offset = Offset(
                        offset.x * z + centroid.x * (1 - z) + pan.x,
                        offset.y * z + centroid.y * (1 - z) + pan.y
                    )
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        if (scale > 2.2f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            val z = 3f
                            offset = Offset(
                                offset.x * z + pos.x * (1 - z),
                                offset.y * z + pos.y * (1 - z)
                            )
                            scale *= z
                        }
                    },
                    onTap = { pos -> hitTest(pos)?.let(onTapElement) }
                )
            }
    ) {
        val (base, baseOff) = baseTransform(IntSize(size.width.toInt(), size.height.toInt()))

        for (el in planData.elements) {
            val r = screenRect(el, base, baseOff)

            drawRect(
                color = fillFor(el),
                topLeft = r.topLeft,
                size = Size(r.width, r.height)
            )

            strokeFor(el)?.let { s ->
                drawRect(
                    color = s.color,
                    topLeft = r.topLeft,
                    size = Size(r.width, r.height),
                    style = Stroke(
                        width = s.widthDp.dp.toPx(),
                        pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 5f)) else null
                    )
                )
            }

            if (el.id == selectedId) {
                drawRect(
                    color = selectionColor,
                    topLeft = Offset(r.left - 3, r.top - 3),
                    size = Size(r.width + 6, r.height + 6),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            val label = labelFor(el) ?: continue
            // العنصر الطولي (الحائط الرأسي) ليبله بيتلف 90° عشان يبقى موازي له
            val vertical = el.height > el.width * 1.5
            val fontSizePx: Float
            if (label.scaleWithPlan) {
                // الحجم نسبة من سُمك العنصر على البلان — بيتكبر مع الزوم
                val thickness = min(r.width, r.height)
                fontSizePx = thickness * 0.75f
                if (fontSizePx < 7f) continue // صغير جداً للقراءة — استنى زوم أكبر
            } else {
                fontSizePx = 10.sp.toPx()
                if (r.width < 46f && r.height < 46f) continue
            }

            val layout = textMeasurer.measure(
                AnnotatedString(label.text),
                style = TextStyle(
                    fontSize = (fontSizePx / density).sp / fontScale,
                    fontWeight = if (label.bold) FontWeight.SemiBold else FontWeight.Normal,
                    color = label.color
                )
            )
            val cx = r.left + r.width / 2
            val cy = r.top + r.height / 2
            val topLeft = Offset(cx - layout.size.width / 2, cy - layout.size.height / 2)
            if (vertical) {
                rotate(degrees = -90f, pivot = Offset(cx, cy)) {
                    drawText(layout, topLeft = topLeft)
                }
            } else {
                drawText(layout, topLeft = topLeft)
            }
        }
    }
}
