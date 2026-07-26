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

/** ألوان ثابتة لعناصر الرسوم (متناسقة مع الموك أب). */
val CHART_COLORS = listOf(
    Color(0xFF5B66D6), Color(0xFF37B98A), Color(0xFFE8890C), Color(0xFFE53935),
    Color(0xFF8E44AD), Color(0xFF2980B9), Color(0xFF16A085), Color(0xFFD64545)
)

/** رسم دائري (Donut) بنِسَب + مفتاح (Legend). */
@Composable
fun DonutChart(data: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val total = data.sumOf { it.second }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(130.dp).padding(6.dp)) {
            var start = -90f
            val stroke = size.minDimension * 0.22f
            val inset = stroke / 2
            data.forEachIndexed { i, (_, v) ->
                val sweep = 360f * v / total
                drawArc(
                    color = CHART_COLORS[i % CHART_COLORS.size],
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
                    Box(Modifier.size(10.dp).background(CHART_COLORS[i % CHART_COLORS.size], CircleShape))
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
