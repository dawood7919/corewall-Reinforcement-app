package com.corewall.qaqc.ui.manpower

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

@Composable
fun ManpowerReportsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val files by vm.attendanceFiles.collectAsStateWithLifecycle()
    val allDaily by vm.dailyAttendance.collectAsStateWithLifecycle()

    val fileIds = files.map { it.id }.toSet()
    val records = remember(allDaily, fileIds) { allDaily.filter { it.fileId in fileIds } }

    val totalWorkers = records.sumOf { it.workers }
    val totalForemen = records.sumOf { it.foremen }
    val days = records.map { dayStart(it.date) }.distinct()
    val perDay = days.map { d -> records.filter { dayStart(it.date) == d }.sumOf { r -> r.workers } }
    val avg = if (perDay.isNotEmpty()) perDay.average() else 0.0
    val maxW = perDay.maxOrNull() ?: 0
    val minW = perDay.minOrNull() ?: 0

    val byTrade = files.groupBy { Trade.from(it.trade) }
        .mapValues { (_, fs) -> records.filter { r -> fs.any { it.id == r.fileId } }.sumOf { it.workers } }
        .filterValues { it > 0 }.toList().sortedByDescending { it.second }
    val byCompany = files.groupBy { it.company.trim() }
        .mapValues { (_, fs) -> records.filter { r -> fs.any { it.id == r.fileId } }.sumOf { it.workers } }
        .filterValues { it > 0 }.toList().sortedByDescending { it.second }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { ManpowerExport.writePdf(it, level, files, allDaily) }
                }
            }.isSuccess
            Toast.makeText(context, if (ok) "تم تصدير PDF ✓" else "فشل التصدير", Toast.LENGTH_SHORT).show()
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { ManpowerExport.writeCsv(it, level, files, allDaily) }
                }
            }.isSuccess
            Toast.makeText(context, if (ok) "تم تصدير Excel/CSV ✓" else "فشل التصدير", Toast.LENGTH_SHORT).show()
        }
    }

    if (records.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.TableView,
            title = "مفيش بيانات للتقرير",
            subtitle = "سجّل حضور في دور $level الأول عشان يتولّد التقرير.",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        item {
            Text("تقرير دور $level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                StatCard("إجمالي العمال", "$totalWorkers", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatCard("إجمالي المشرفين", "$totalForemen", LocalCwColors.current.series(1), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                StatCard("متوسط/يوم", "%.0f".format(avg), LocalCwColors.current.series(2), Modifier.weight(1f))
                StatCard("أعلى", "$maxW", LocalCwColors.current.series(5), Modifier.weight(1f))
                StatCard("أقل", "$minW", LocalCwColors.current.series(6), Modifier.weight(1f))
            }
        }
        item {
            Surface(shape = Radius.shapeLg, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Space.lg)) {
                    Text("اتجاه الحضور", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Space.md))
                    LineChart(perDay, MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            if (byTrade.isNotEmpty()) {
                Surface(shape = Radius.shapeLg, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Space.lg)) {
                        Text("توزيع العمالة حسب النوع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(Space.sm))
                        DonutChart(byTrade.map { it.first.label to it.second })
                    }
                }
            }
        }
        item { DistributionCard("توزيع الشركات", byCompany.map { it.first to it.second }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { pdfLauncher.launch("manpower-$level.pdf") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null); Spacer(Modifier.width(Space.sm)); Text("PDF")
                }
                OutlinedButton(onClick = { csvLauncher.launch("manpower-$level.csv") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.TableView, contentDescription = null); Spacer(Modifier.width(Space.sm)); Text("Excel/CSV")
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(shape = Radius.shapeLg, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = modifier) {
        Column(Modifier.padding(Space.lg)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DistributionCard(title: String, data: List<Pair<String, Int>>) {
    if (data.isEmpty()) return
    val max = data.maxOf { it.second }.coerceAtLeast(1)
    Surface(shape = Radius.shapeLg, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.md))
            data.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.xs)) {
                    Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Box(
                        Modifier.weight(1f).height(Space.lg)
                            .background(MaterialTheme.colorScheme.surfaceVariant, Radius.shapeSm)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(value.toFloat() / max).height(Space.lg)
                                .background(MaterialTheme.colorScheme.primary, Radius.shapeSm)
                        )
                    }
                    Spacer(Modifier.width(Space.sm))
                    Text("$value", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
