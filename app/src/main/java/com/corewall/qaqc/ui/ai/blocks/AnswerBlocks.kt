package com.corewall.qaqc.ui.ai.blocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corewall.qaqc.ai.model.AnswerBlock
import com.corewall.qaqc.ai.model.AnswerMetric
import com.corewall.qaqc.ui.theme.LocalSrtColors
import com.corewall.qaqc.ui.theme.LocalVizColors

/**
 * بلوك واحد من رد المساعد، بيدخل الشاشة بحركة خفيفة متدرّجة
 * حسب ترتيبه — الترتيب بيدّي إحساس إن الرد بيتبني قدامك.
 */
@Composable
fun AnswerBlockCard(
    block: AnswerBlock,
    index: Int,
    onOpenFile: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visible by remember(block) { mutableStateOf(false) }
    LaunchedEffect(block) {
        kotlinx.coroutines.delay(index * 70L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) +
            slideInVertically(tween(320, easing = LinearOutSlowInEasing)) { it / 5 }
    ) {
        when (block.type.uppercase()) {
            "ALERT" -> AlertBlock(block, modifier)
            "TEXT" -> TextBlock(block, modifier)
            else -> DataCard(block, onOpenFile, modifier)
        }
    }
}

/** فقرة شرح — من غير إطار، عشان متزوّدش ضوضاء على النص. */
@Composable
private fun TextBlock(block: AnswerBlock, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (block.title.isNotBlank()) {
            Text(block.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            block.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        MarksRow(block.marks)
    }
}

/**
 * تنبيه — أيقونة + كلمة الحالة دايماً مع اللون.
 * اللون لوحده مايكفيش يحمل المعنى.
 */
@Composable
private fun AlertBlock(block: AnswerBlock, modifier: Modifier = Modifier) {
    val viz = LocalVizColors.current
    val (tone, icon, label) = when (block.severity.uppercase()) {
        "CRITICAL" -> Triple(viz.critical, Icons.Filled.ErrorOutline, "حرج")
        "SERIOUS" -> Triple(viz.serious, Icons.Filled.WarningAmber, "مهم")
        "WARNING" -> Triple(viz.warning, Icons.Filled.WarningAmber, "تنبيه")
        "GOOD" -> Triple(viz.good, Icons.Filled.CheckCircle, "سليم")
        else -> Triple(LocalSrtColors.current.blue, Icons.Filled.Info, "معلومة")
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tone.copy(alpha = 0.10f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        block.title.ifBlank { label },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = tone)
                }
                if (block.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(block.body, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                MarksRow(block.marks)
            }
        }
    }
}

/** كارت بيانات: عنوان + المحتوى + عرض جدول للرسوم. */
@Composable
private fun DataCard(
    block: AnswerBlock,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chart = block.type.uppercase() in setOf("BAR", "SPLIT", "TREND")
    var asTable by remember(block) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            if (block.title.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        block.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    // كل رسمة ليها توأم جدولي — القيمة عمرها ما تكون محبوسة في اللون
                    if (chart) {
                        Surface(
                            onClick = { asTable = !asTable },
                            shape = CircleShape,
                            color = if (asTable) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                        ) {
                            Icon(
                                Icons.Filled.TableRows,
                                contentDescription = if (asTable) "عرض كرسمة" else "عرض كجدول",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(6.dp).size(16.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (chart && asTable) {
                DataTable(
                    columns = listOf("البند", if (block.unit.isNotBlank()) "القيمة (${block.unit})" else "القيمة"),
                    rows = block.points.map { listOf(it.label, it.display.ifBlank { formatValue(it.value) }) }
                )
            } else when (block.type.uppercase()) {
                "METRICS" -> MetricsRow(block.metrics)
                "BAR" -> BarChart(block.points, block.unit)
                "SPLIT" -> SplitBar(block.points)
                "TREND" -> TrendChart(block.points, block.unit)
                "METER" -> Meter(block.percent, block.body)
                "TABLE" -> DataTable(block.columns, block.rows)
                "LIST" -> BulletList(block.items, numbered = false)
                "STEPS" -> BulletList(block.items, numbered = true)
                "FILES" -> FileList(block.files, onOpenFile)
                "IMAGES" -> ImageGallery(block.files, onOpenFile)
                else -> Text(block.body, style = MaterialTheme.typography.bodyMedium)
            }

            if (block.body.isNotBlank() && block.type.uppercase() !in setOf("METER", "TEXT")) {
                Spacer(Modifier.height(10.dp))
                Text(
                    block.body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MarksRow(block.marks)
        }
    }
}

/**
 * صف الأرقام الرئيسية. الأرقام بتعدّ لقيمتها بدل ما تظهر فجأة —
 * العدّ بيلفت العين للرقم اللي هو أصلاً بيت القصيد.
 */
@Composable
private fun MetricsRow(metrics: List<AnswerMetric>) {
    val shown = metrics.take(4)
    if (shown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m -> StatTile(m, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(m: AnswerMetric, modifier: Modifier = Modifier) {
    val viz = LocalVizColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                m.label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            CountUpValue(m.value)
            if (m.delta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                val up = m.direction.equals("UP", true)
                val flat = m.direction.equals("FLAT", true)
                // اللون بيتحدّد بالاتجاه × هل الطلوع كويس — مش بالإشارة لوحدها
                val tone = when {
                    flat -> MaterialTheme.colorScheme.onSurfaceVariant
                    up == m.upIsGood -> viz.good
                    else -> viz.critical
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!flat) {
                        Icon(
                            if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null, tint = tone, modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(m.delta, style = MaterialTheme.typography.labelSmall, color = tone)
                }
            }
            if (m.hint.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    m.hint, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * بيعدّ للرقم لو القيمة رقمية، وبيعرضها زي ما هي لو نص.
 * الأرقام الكبيرة بتفضل بأرقام متناسبة (مش tabular) عشان متبانش مفكوكة.
 */
@Composable
private fun CountUpValue(value: String) {
    val numeric = value.replace(",", "").trim().toDoubleOrNull()
    if (numeric == null || kotlin.math.abs(numeric) > 1_000_000) {
        Text(
            value, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        return
    }
    var start by remember(value) { mutableStateOf(false) }
    LaunchedEffect(value) { start = true }
    val shown by animateIntAsState(
        targetValue = if (start) numeric.toInt() else 0,
        animationSpec = tween(900, easing = LinearOutSlowInEasing),
        label = "count"
    )
    val decimals = numeric != numeric.toInt().toDouble()
    Text(
        if (decimals && start) value else formatValue(shown.toDouble()),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun BulletList(items: List<String>, numbered: Boolean) {
    val srt = LocalSrtColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(12).forEachIndexed { i, s ->
            Row {
                if (numbered) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(srt.blueTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = srt.blue)
                    }
                } else {
                    Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(srt.blue))
                }
                Spacer(Modifier.width(10.dp))
                Text(s, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** أكواد العناصر المرتبطة — شرايح صغيرة بخط مونوسبيس. */
@Composable
private fun MarksRow(marks: List<String>) {
    if (marks.isEmpty()) return
    val srt = LocalSrtColors.current
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        marks.take(6).forEach { m ->
            Surface(shape = RoundedCornerShape(7.dp), color = srt.blueTint) {
                Text(
                    m,
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = com.corewall.qaqc.ui.theme.CodeTextStyle.copy(fontSize = 11.sp),
                    color = srt.blue
                )
            }
        }
    }
}

/** الحركة اللي بتظهر وقت انتظار الرد. */
@Composable
fun ThinkingRow(text: String, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "think")
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 180),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(srt.blue.copy(alpha = alpha))
            )
            Spacer(Modifier.width(5.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = srt.text3)
    }
}

/** يُستخدم لإخفاء/إظهار المصادر تحت الرد. */
@Composable
fun Collapsible(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically()
    ) { content() }
}
