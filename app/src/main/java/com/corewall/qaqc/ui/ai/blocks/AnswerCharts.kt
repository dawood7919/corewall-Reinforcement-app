package com.corewall.qaqc.ui.ai.blocks

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ai.model.AnswerPoint
import com.corewall.qaqc.ui.theme.LocalVizColors

// المواصفات ثابتة عبر كل الرسوم: علامات رفيعة، فراغ 2px بيفصل بدل الحدود،
// شبكة شعرة متراجعة، والقيم مكتوبة صراحة مش متروكة للون.
private val BAR_THICKNESS = 22.dp     // ≤ 24dp — العلامة عمرها ما بتملى الخانة
private val SURFACE_GAP = 2.dp
private const val DRAW_MS = 850

/** تنعيم دخول الرسمة: من 0 لـ1 مرة واحدة عند أول ظهور. */
@Composable
private fun drawProgress(key: Any?): Float {
    var started by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { started = true }
    val p by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(DRAW_MS, easing = FastOutSlowInEasing),
        label = "draw"
    )
    return p
}

/** رقم مقروء: من غير كسور لو مش محتاجها، وبفواصل الآلاف. */
internal fun formatValue(v: Double): String {
    val abs = kotlin.math.abs(v)
    val whole = abs.toLong()
    val frac = abs - whole
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    val body = if (frac >= 0.05) "$grouped.${(frac * 10).toInt()}" else grouped
    return if (v < 0) "-$body" else body
}

private fun AnswerPoint.text(): String = display.ifBlank { formatValue(value) }

/**
 * مقارنة مقادير — أشرطة أفقية بلون واحد.
 *
 * سلسلة واحدة = لون واحد لكل الأشرطة. تدرّج اللون حسب الحجم بيكرّر
 * معلومة الطول نفسها ويحرق القناة اللونية على غير طايل.
 * كل شريط مكتوب قيمته عند طرفه، فالقراءة مش معتمدة على اللون.
 */
@Composable
fun BarChart(points: List<AnswerPoint>, unit: String, modifier: Modifier = Modifier) {
    val viz = LocalVizColors.current
    val shown = points.take(8)
    val max = shown.maxOfOrNull { kotlin.math.abs(it.value) }?.takeIf { it > 0.0 } ?: 1.0
    val progress = drawProgress(shown)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { p ->
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // القيمة نص عادي بلون النص — اللون بتاع البيانات للعلامة بس
                    Text(
                        buildString { append(p.text()); if (unit.isNotBlank()) append(" $unit") },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier.fillMaxWidth().height(BAR_THICKNESS / 2)
                        .clip(RoundedCornerShape(4.dp))
                        .background(viz.track)
                ) {
                    val frac = ((kotlin.math.abs(p.value) / max).toFloat() * progress)
                        .coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(frac.coerceAtLeast(0.005f))
                            // طرف البيانات مدوّر 4dp، والقاعدة مربّعة
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(viz.series(0))
                    )
                }
                if (p.note.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(p.note, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * جزء من كل — شريط واحد مقسّم + مفتاح.
 *
 * بدل الدونات: المقارنة بين القطاعات في الشريط أسهل بكتير، والمفتاح
 * موجود دايماً فالهوية مش متروكة للون لوحده. الفصل بفراغ 2px بلون السطح،
 * مش بحدود مرسومة.
 */
@Composable
fun SplitBar(points: List<AnswerPoint>, modifier: Modifier = Modifier) {
    val viz = LocalVizColors.current
    val shown = points.filter { it.value > 0.0 }.take(6)
    if (shown.isEmpty()) return
    val total = shown.sumOf { it.value }
    val progress = drawProgress(shown)

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(BAR_THICKNESS / 2).clip(RoundedCornerShape(4.dp)),
            horizontalArrangement = Arrangement.spacedBy(SURFACE_GAP)
        ) {
            shown.forEachIndexed { i, p ->
                Box(
                    Modifier
                        .weight(((p.value / total).toFloat() * progress + 0.0001f))
                        .fillMaxHeight()
                        .background(viz.series(i))
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // المفتاح: نقطة ملوّنة + نص بلون النص — دايماً موجود لأن السلاسل أكتر من واحدة
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(viz.series(i)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        p.label, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${p.text()} · ${((p.value / total) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * تغيّر عبر الزمن — خط 2px مع غسلة خفيفة تحته.
 *
 * القيم مش مكتوبة على كل نقطة: النهاية والأعلى والأدنى بس، وباقي القيم
 * في عرض الجدول. رقم على كل نقطة بيبقى ضوضاء ومحدش بيقراه.
 */
@Composable
fun TrendChart(points: List<AnswerPoint>, unit: String, modifier: Modifier = Modifier) {
    val viz = LocalVizColors.current
    val shown = points.take(24)
    if (shown.size < 2) return
    val values = shown.map { it.value }
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    val progress = drawProgress(shown)
    val line = viz.series(0)
    val surface = MaterialTheme.colorScheme.surface

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width
            val h = size.height
            val padY = 10.dp.toPx()
            val usable = h - padY * 2
            fun px(i: Int) = w * i / (shown.size - 1).toFloat()
            fun py(v: Double) = padY + (1f - ((v - min) / span).toFloat()) * usable

            // شبكة: ثلاث شعرات صلبة متراجعة — من غير تقطيع
            repeat(3) { g ->
                val y = padY + usable * g / 2f
                drawLine(viz.grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            val path = Path().apply {
                moveTo(px(0), py(values[0]))
                for (i in 1 until shown.size) lineTo(px(i), py(values[i]))
            }

            // الغسلة تحت الخط: نفس اللون بشفافية خفيفة، مش كتلة مشبعة
            val measure = PathMeasure().apply { setPath(path, false) }
            val drawn = Path()
            measure.getSegment(0f, measure.length * progress, drawn, true)

            val area = Path().apply {
                addPath(drawn)
                val endX = px(0) + (w - px(0)) * progress
                lineTo(endX, h - padY)
                lineTo(px(0), h - padY)
                close()
            }
            drawPath(area, line.copy(alpha = 0.10f))
            drawPath(
                drawn, line,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // علامة النهاية: ≥8dp مع حلقة بلون السطح عشان تفضل واضحة
            if (progress > 0.99f) {
                val cx = px(shown.size - 1)
                val cy = py(values.last())
                drawCircle(surface, radius = 6.dp.toPx(), center = Offset(cx, cy))
                drawCircle(line, radius = 4.dp.toPx(), center = Offset(cx, cy))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(shown.first().label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(shown.last().label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EdgeValue("الأخير", shown.last().text(), unit)
            EdgeValue("الأعلى", formatValue(max), unit)
            EdgeValue("الأدنى", formatValue(min), unit)
        }
    }
}

@Composable
private fun EdgeValue(label: String, value: String, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            buildString { append(value); if (unit.isNotBlank()) append(" $unit") },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * نسبة واحدة مقابل حد. المسار درجة أفتح من نفس تدرّج التعبئة،
 * فالحالة بتتقري على طول الشريط مش عند الطرف بس.
 */
@Composable
fun Meter(percent: Double, caption: String, modifier: Modifier = Modifier) {
    val viz = LocalVizColors.current
    val pct = percent.coerceIn(0.0, 100.0)
    val progress = drawProgress(pct)
    val fill = when {
        pct >= 75 -> viz.good
        pct >= 40 -> viz.warning
        else -> viz.critical
    }
    val shown = (pct * progress).toInt()

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$shown%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (caption.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    caption, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(viz.track)
        ) {
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth((pct / 100.0).toFloat() * progress)
                    .clip(RoundedCornerShape(5.dp))
                    .background(fill)
            )
        }
    }
}

/** جدول — الشكل الصح لما الأصناف تعدّي ٧ أو البيانات تكون تفصيلية. */
@Composable
fun DataTable(columns: List<String>, rows: List<List<String>>, modifier: Modifier = Modifier) {
    val cols = columns.take(4)
    if (cols.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            cols.forEach { c ->
                Text(
                    c, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        HairLine()
        rows.take(30).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                cols.indices.forEach { i ->
                    Text(
                        r.getOrElse(i) { "" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HairLine()
        }
        if (rows.size > 30) {
            Text(
                "+${rows.size - 30} صف إضافي",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun HairLine(color: Color = MaterialTheme.colorScheme.outline) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}
