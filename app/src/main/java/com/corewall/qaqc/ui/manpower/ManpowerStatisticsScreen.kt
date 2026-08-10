package com.corewall.qaqc.ui.manpower

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.EmptyState
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

@Composable
fun ManpowerStatisticsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val files by vm.attendanceFiles.collectAsStateWithLifecycle()
    val allDaily by vm.dailyAttendance.collectAsStateWithLifecycle()

    val fileIds = files.map { it.id }.toSet()
    val records = remember(allDaily, fileIds) { allDaily.filter { it.fileId in fileIds } }

    if (records.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.BarChart,
            title = "لسه مفيش إحصائيات",
            subtitle = "سجّل حضور في دور $level عشان تشوف الرسوم البيانية.",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    var period by remember { mutableIntStateOf(1) } // 0 أسبوع, 1 شهر, 2 ثلاثة شهور
    val window = when (period) { 0 -> 7; 1 -> 30; else -> 90 }
    /**
     * إحصاءات النافذة في مرورين على السجلات بدل حلقات متداخلة.
     *
     * القديم كان فيه تلات أنماط تربيعية في نفس الشاشة: `days.map { … 
     * records.filter { … } }`، و`days.toSet()` بتتبني من جديد **جوّه**
     * شرط الفلترة (يعني لكل سجل)، و`windowRecords.filter { fs.any { … } }`
     * لكل مجموعة تخصّص وشركة. وكله من غير `remember`.
     */
    val stats = remember(records, files, window) {
        val allDays = records.mapTo(HashSet()) { dayStart(it.date) }.sorted()
        val days = allDays.takeLast(window)
        val inWindow = days.toHashSet()

        val workersByDay = HashMap<Long, Int>(days.size * 2)
        val workersByFile = HashMap<Long, Int>()
        records.forEach { r ->
            val day = dayStart(r.date)
            workersByDay[day] = (workersByDay[day] ?: 0) + r.workers
            if (day in inWindow) {
                workersByFile[r.fileId] = (workersByFile[r.fileId] ?: 0) + r.workers
            }
        }
        StatsBundle(
            dailyWorkers = days.map { d -> d to (workersByDay[d] ?: 0) },
            byTrade = files.groupBy { Trade.from(it.trade) }
                .mapValues { (_, fs) -> fs.sumOf { workersByFile[it.id] ?: 0 } }
                .filterValues { it > 0 }.toList().sortedByDescending { it.second },
            topCompanies = files.groupBy { it.company.trim() }.filterKeys { it.isNotEmpty() }
                .mapValues { (_, fs) -> fs.sumOf { workersByFile[it.id] ?: 0 } }
                .filterValues { it > 0 }.toList().sortedByDescending { it.second }.take(5)
        )
    }
    val dailyWorkers = stats.dailyWorkers
    val peak = dailyWorkers.maxOfOrNull { it.second } ?: 0
    val avg = if (dailyWorkers.isNotEmpty()) dailyWorkers.map { it.second }.average() else 0.0
    val byTrade = stats.byTrade
    val topCompanies = stats.topCompanies

    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                listOf("أسبوع", "شهر", "3 أشهر").forEachIndexed { i, label ->
                    androidx.compose.material3.FilterChip(
                        selected = period == i,
                        onClick = { period = i },
                        label = { Text(label) }
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                MiniStat("ذروة العمالة", "$peak", LocalCwColors.current.danger.fg, Modifier.weight(1f))
                MiniStat("متوسط العمالة", "%.0f".format(avg), LocalCwColors.current.success.fg, Modifier.weight(1f))
            }
        }
        item {
            ChartCard("العمالة اليومية (آخر 14 يوم)") {
                BarChart(dailyWorkers.map { it.second }, MaterialTheme.colorScheme.primary)
            }
        }
        item {
            ChartCard("مقارنة التخصصات") {
                Column {
                    val max = (byTrade.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                    byTrade.forEach { (t, v) ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.xxs)) {
                            Text(t.label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Box(
                                Modifier.weight(1f).height(Space.lg)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, Radius.shapeSm)
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(v.toFloat() / max).height(Space.lg)
                                        .background(MaterialTheme.colorScheme.tertiary, Radius.shapeSm)
                                )
                            }
                            Spacer(Modifier.width(Space.sm))
                            Text("$v", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (topCompanies.isNotEmpty()) {
            item {
                ChartCard("أعلى 5 شركات") {
                    val colors = chartColors()
                    Column {
                        topCompanies.forEachIndexed { i, (company, workers) ->
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = Space.xs)
                            ) {
                                Box(
                                    Modifier.size(26.dp).background(
                                        colors[i % colors.size].copy(alpha = 0.15f),
                                        Radius.shapeSm
                                    ),
                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                ) {
                                    Text(
                                        "${i + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors[i % colors.size]
                                    )
                                }
                                Spacer(Modifier.width(Space.md))
                                Text(company, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "$workers عامل",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(shape = Radius.shapeLg, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = modifier) {
        Column(Modifier.padding(Space.lg)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Surface(shape = Radius.shapeLg, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.md))
            content()
        }
    }
}

@Composable
private fun BarChart(values: List<Int>, color: Color) {
    if (values.isEmpty()) return
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val n = values.size
        val gap = size.width * 0.02f
        val barW = (size.width - gap * (n + 1)) / n
        values.forEachIndexed { i, v ->
            val h = size.height * (v.toFloat() / max) * 0.92f
            val x = gap + i * (barW + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }
    }
}

/** إحصاءات نافذة زمنية — محسوبة مرة واحدة في [ManpowerStatisticsScreen]. */
private data class StatsBundle(
    val dailyWorkers: List<Pair<Long, Int>>,
    val byTrade: List<Pair<Trade, Int>>,
    val topCompanies: List<Pair<String, Int>>
)
