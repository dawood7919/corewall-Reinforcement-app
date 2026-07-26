package com.corewall.qaqc.ui.manpower

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.AttendanceFileEntity
import com.corewall.qaqc.ui.EmptyState

@Composable
fun AttendanceScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val files by vm.attendanceFiles.collectAsStateWithLifecycle()
    val daily by vm.dailyAttendance.collectAsStateWithLifecycle()

    var showDialog by remember { mutableStateOf(false) }

    val today = dayStart(System.currentTimeMillis())
    val fileIds = files.map { it.id }.toSet()
    val todayRecords = daily.filter { it.fileId in fileIds && dayStart(it.date) == today }
    val workersToday = todayRecords.sumOf { it.workers }
    val foremenToday = todayRecords.sumOf { it.foremen }
    val companies = files.map { it.company.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size

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
            // ملخّص اليوم
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(fullDate(System.currentTimeMillis()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("دور $level", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryTile(Icons.Filled.People, "عمال النهاردة", "$workersToday", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        SummaryTile(Icons.Filled.Badge, "مشرفين", "$foremenToday", Color(0xFFE8890C), Modifier.weight(1f))
                        SummaryTile(Icons.Filled.Apartment, "شركات", "$companies", Color(0xFF37B98A), Modifier.weight(1f))
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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

@Composable
private fun SummaryTile(icon: ImageVector, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row {
            Box(
                Modifier
                    .width(6.dp)
                    .height(120.dp)
                    .padding(0.dp)
            ) { Surface(color = tag, modifier = Modifier.fillMaxSize()) {} }
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = tag.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            Trade.from(file.trade).label,
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = tag,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text("بدأ ${shortDate(file.startDate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(file.company.ifBlank { "بدون اسم" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("$workersToday", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("عامل النهاردة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        Text("$foremenToday", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFE8890C))
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
