package com.corewall.qaqc.ui.manpower

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.data.db.AttendanceFileEntity
import com.corewall.qaqc.ui.EmptyState
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

@Composable
fun AttendanceScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val files by vm.attendanceFiles.collectAsStateWithLifecycle()
    val daily by vm.dailyAttendance.collectAsStateWithLifecycle()
    val gradient = com.corewall.qaqc.ui.theme.LocalAppGradients.current.header

    var showDialog by remember { mutableStateOf(false) }

    val today = dayStart(System.currentTimeMillis())
    val fileIds = files.map { it.id }.toSet()
    val todayRecords = daily.filter { it.fileId in fileIds && dayStart(it.date) == today }
    val workersToday = todayRecords.sumOf { it.workers }
    val foremenToday = todayRecords.sumOf { it.foremen }
    val engineersToday = todayRecords.sumOf { it.engineers }
    val helpersToday = todayRecords.sumOf { it.supervisors }
    val totalLaborToday = workersToday + foremenToday + engineersToday + helpersToday
    val companiesActive = todayRecords.map { it.fileId }.distinct().size

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("ملف حضور جديد") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // كارت ملخّص اليوم (أزرق متدرّج زي الموك أب)
            Surface(
                shape = Radius.shapeXl,
                color = Color.Transparent,
                modifier = Modifier.padding(Space.lg).fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .background(Brush.verticalGradient(gradient), Radius.shapeXl)
                        .padding(Space.xl)
                ) {
                    Text("اليوم · دور $level", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                    Text(fullDate(System.currentTimeMillis()), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Space.lg))
                    Row(Modifier.fillMaxWidth()) {
                        BigMetric("$workersToday", "إجمالي العمال", Modifier.weight(1f))
                        DividerV()
                        BigMetric("$foremenToday", "الفورمان", Modifier.weight(1f))
                        DividerV()
                        BigMetric("$engineersToday", "المهندسين", Modifier.weight(1f))
                    }
                }
            }

            if (files.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Groups,
                    title = "مفيش ملفات حضور في دور $level",
                    subtitle = "أنشئ أول ملف حضور لمقاول/تخصص وابدأ تسجّل العمالة اليومية.",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                if (files.isNotEmpty()) {
                    item(key = "__health__") {
                        // الألوان من سلسلة اللوحة بالترتيب — كانت ٦ قيم مكتوبة
                        // بالإيد ومالهاش علاقة بأي نظام، وتلاتة منهم ساقطين تباين.
                        val cw = LocalCwColors.current
                        HealthGrid(
                            listOf(
                                HealthMetric("العمال", workersToday, Icons.Filled.Groups, cw.series(0)),
                                HealthMetric("الفورمان", foremenToday, Icons.Filled.Badge, cw.series(1)),
                                HealthMetric("المهندسين", engineersToday, Icons.Filled.Engineering, cw.series(2)),
                                HealthMetric("المساعدين", helpersToday, Icons.Filled.People, cw.series(6)),
                                HealthMetric("إجمالي العمالة", totalLaborToday, Icons.Filled.Groups, cw.series(5)),
                                HealthMetric("الشركات", companiesActive, Icons.Filled.Apartment, cw.series(7))
                            )
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text("ملفات الحضور", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
                items(files, key = { it.id }) { file ->
                    val fileToday = todayRecords.filter { it.fileId == file.id }
                    val lastUpdate = daily.filter { it.fileId == file.id }.maxByOrNull { it.updatedAt }
                    AttendanceFileCard(
                        file = file,
                        workersToday = fileToday.sumOf { it.workers },
                        foremenToday = fileToday.sumOf { it.foremen },
                        lastUpdated = lastUpdate?.updatedAt,
                        onClick = { vm.openAttendanceFile(file.id) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        NewAttendanceFileDialog(
            onDismiss = { showDialog = false },
            onSave = { file -> vm.saveAttendanceFile(file); showDialog = false }
        )
    }
}

private data class HealthMetric(val label: String, val value: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val accent: Color)

/** شبكة كروت العمالة بستايل Apple Health — بعدّادات متحركة. */
@Composable
private fun HealthGrid(metrics: List<HealthMetric>) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        metrics.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                row.forEach { m -> HealthCard(m, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HealthCard(m: HealthMetric, modifier: Modifier = Modifier) {
    val animated by androidx.compose.animation.core.animateIntAsState(
        targetValue = m.value,
        animationSpec = androidx.compose.animation.core.tween(650),
        label = "hc"
    )
    Surface(
        shape = Radius.shapeLg,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(Modifier.padding(Space.md)) {
            Box(
                Modifier.size(30.dp).background(m.accent.copy(alpha = 0.14f), Radius.shapeSm),
                contentAlignment = Alignment.Center
            ) { Icon(m.icon, contentDescription = null, tint = m.accent, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.height(Space.sm))
            Text("$animated", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(m.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun BigMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun DividerV() {
    Box(
        Modifier
            .width(Space.xxs)
            .height(Space.xxl)
            .background(Color.White.copy(alpha = 0.25f))
    )
}

@Composable
private fun AttendanceFileCard(
    file: AttendanceFileEntity,
    workersToday: Int,
    foremenToday: Int,
    lastUpdated: Long?,
    onClick: () -> Unit
) {
    val tag = colorFor(file.colorTag)
    Surface(
        onClick = onClick,
        shape = Radius.shapeXl,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row {
            Box(
                Modifier
                    .width(Space.sm)
                    .height(120.dp)
                    .padding(Space.xxs)
            ) { Surface(color = tag, modifier = Modifier.fillMaxSize()) {} }
            Column(Modifier.padding(Space.lg).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = tag.copy(alpha = 0.15f), shape = Radius.shapeSm) {
                        Text(
                            Trade.from(file.trade).label,
                            Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                            style = MaterialTheme.typography.labelMedium,
                            color = tag,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Space.xs))
                        Text("بدأ ${shortDate(file.startDate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).background(tag, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            file.company.trim().take(1).uppercase().ifBlank { "?" },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(Space.md))
                    Text(file.company.ifBlank { "بدون اسم" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("$workersToday", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("عامل النهاردة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("$foremenToday", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LocalCwColors.current.warning.fg)
                        Text("مشرف", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.weight(1f))
                    if (lastUpdated != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("آخر تحديث", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(timeOf(lastUpdated), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
