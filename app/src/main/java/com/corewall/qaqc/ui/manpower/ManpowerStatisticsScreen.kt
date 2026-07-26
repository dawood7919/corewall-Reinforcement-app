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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.EmptyState

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

    // آخر 14 يوم فيها تسجيل
    val days = records.map { dayStart(it.date) }.distinct().sorted().takeLast(14)
    val dailyWorkers = days.map { d -> d to records.filter { dayStart(it.date) == d }.sumOf { r -> r.workers } }
    val peak = dailyWorkers.maxOfOrNull { it.second } ?: 0
    val avg = if (dailyWorkers.isNotEmpty()) dailyWorkers.map { it.second }.average() else 0.0

    val byTrade = files.groupBy { Trade.from(it.trade) }
        .mapValues { (_, fs) -> records.filter { r -> fs.any { it.id == r.fileId } }.sumOf { it.workers } }
        .filterValues { it > 0 }.toList().sortedByDescending { it.second }

    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("ذروة العمالة", "$peak", Color(0xFFE53935), Modifier.weight(1f))
                MiniStat("متوسط العمالة", "%.0f".format(avg), Color(0xFF37B98A), Modifier.weight(1f))
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
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Text(t.label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Box(
                                Modifier.weight(1f).height(16.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            ) {
                                Box(
                                    Modifier.fillMaxWidth(v.toFloat() / max).height(16.dp)
                                        .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp))
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("$v", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
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
