package com.corewall.qaqc.ui.manpower

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.min
import com.corewall.qaqc.ui.design.LocalCwColors

/**
 * ألوان السلاسل — من اللوحة، مش قايمة مستقلة.
 *
 * دي كانت آخر مجموعة ألوان موازية في التطبيق: ٨ درجات متختارة عشان تطابق
 * موك أب، من غير أي فحص لعمى الألوان ولا للتباين على الأسطح الغامقة.
 * سلسلة اللوحة اتفحصت بمحاكاة protan/deutan/tritan على الأسطح التلاتة،
 * وترتيبها جزء من الفحص — عشان كده بنقراها زي ما هي.
 */
@Composable
fun chartColors(): List<Color> = LocalCwColors.current.series

/** رسم دائري (Donut) بنِسَب + مفتاح (Legend). */
@Composable
fun DonutChart(data: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }.coerceAtLeast(1)
    val colors = chartColors()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(130.dp).padding(6.dp)) {
            var start = -90f
            val stroke = size.minDimension * 0.22f
            val inset = stroke / 2
            data.forEachIndexed { i, (_, v) ->
                val sweep = 360f * v / total
                drawArc(
                    color = colors[i % colors.size],
                    startAngle = start,
                    sweepAngle = sweep - 2f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke)
                )
                start += sweep
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            data.forEachIndexed { i, (label, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Box(Modifier.size(10.dp).background(colors[i % colors.size], CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp), maxLines = 1)
                    Text("${(v * 100 / total)}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** رسم خطّي بسيط لاتجاه القيم. */
@Composable
fun LineChart(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    val fill = color.copy(alpha = 0.12f)
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        val n = values.size
        val dx = size.width / (n - 1)
        fun pt(i: Int): Offset {
            val v = values[i]
            return Offset(i * dx, size.height - (v.toFloat() / max) * size.height * 0.9f - size.height * 0.05f)
        }
        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until n) lineTo(pt(i).x, pt(i).y)
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(area, fill)
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
        for (i in 0 until n) drawCircle(color, radius = 3.dp.toPx(), center = pt(i))
    }
}

/** رسم أعمدة عمودي. */
@Composable
fun VerticalBars(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    if (values.isEmpty()) return
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val n = values.size
        val gap = size.width * 0.015f
        val barW = (size.width - gap * (n + 1)) / n
        values.forEachIndexed { i, v ->
            val h = (v.toFloat() / max) * size.height * 0.92f
            drawRoundRect(
                color = color,
                topLeft = Offset(gap + i * (barW + gap), size.height - h),
                size = Size(barW.coerceAtLeast(1f), h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(min(barW / 2, 6f), min(barW / 2, 6f))
            )
        }
    }
}
