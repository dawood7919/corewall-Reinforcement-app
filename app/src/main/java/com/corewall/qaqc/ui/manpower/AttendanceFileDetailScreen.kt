package com.corewall.qaqc.ui.manpower

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.AttendanceFileEntity
import com.corewall.qaqc.data.db.DailyAttendanceEntity
import com.corewall.qaqc.ui.EmptyState
import java.util.Calendar

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AttendanceFileDetailScreen(vm: MainViewModel, fileId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val files by vm.attendanceFiles.collectAsStateWithLifecycle()
    val allDaily by vm.dailyAttendance.collectAsStateWithLifecycle()
    val file = files.firstOrNull { it.id == fileId }

    val records = remember(allDaily, fileId) {
        allDaily.filter { it.fileId == fileId }.sortedByDescending { it.date }
    }

    var view by remember { mutableIntStateOf(0) } // 0 timeline, 1 calendar
    var showAdd by remember { mutableStateOf(false) }
    var editFile by remember { mutableStateOf(false) }
    var editDaily by remember { mutableStateOf<DailyAttendanceEntity?>(null) }
    var addForDate by remember { mutableStateOf<Long?>(null) }

    if (file == null) { onClose(); return }
    val tag = colorFor(file.colorTag)

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addForDate = null; showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("تسجيل حضور") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // شريط علوي
            Surface(color = tag, contentColor = Color.White) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(file.company, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${Trade.from(file.trade).label} · دور ${file.level}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                    IconButton(onClick = { editFile = true }) { Icon(Icons.Filled.Edit, contentDescription = "تعديل", tint = Color.White) }
                    IconButton(onClick = { vm.deleteAttendanceFile(fileId); onClose() }) { Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = Color.White) }
                }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                listOf("السجل اليومي", "التقويم").forEachIndexed { i, label ->
                    SegmentedButton(selected = view == i, onClick = { view = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }

            if (records.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Add,
                    title = "مفيش تسجيل حضور لسه",
                    subtitle = "اضغط «تسجيل حضور» لإضافة أول يوم لـ${file.company}."
                )
            } else if (view == 0) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { rec ->
                        DailyCard(
                            rec = rec,
                            onEdit = { editDaily = rec },
                            onDuplicate = { vm.saveDaily(rec.copy(id = 0, date = System.currentTimeMillis())) },
                            onDelete = { vm.deleteDaily(rec.id) },
                            onShare = { shareDaily(context, file, rec) }
                        )
                    }
                }
            } else {
                CalendarView(
                    records = records,
                    tag = tag,
                    onPickDay = { dayMillis ->
                        val existing = records.firstOrNull { dayStart(it.date) == dayStart(dayMillis) }
                        if (existing != null) editDaily = existing else { addForDate = dayMillis; showAdd = true }
                    }
                )
            }
        }
    }

    if (showAdd) {
        AddDailyDialog(
            fileId = fileId,
            existing = addForDate?.let { d -> DailyAttendanceEntity(fileId = fileId, date = d, updatedAt = 0) },
            onDismiss = { showAdd = false },
            onSave = { vm.saveDaily(it); showAdd = false }
        )
    }
    editDaily?.let { rec ->
        AddDailyDialog(
            fileId = fileId, existing = rec,
            onDismiss = { editDaily = null },
            onSave = { vm.saveDaily(it); editDaily = null }
        )
    }
    if (editFile) {
        NewAttendanceFileDialog(
            existing = file,
            onDismiss = { editFile = false },
            onSave = { vm.saveAttendanceFile(it); editFile = false }
        )
    }
}

@Composable
private fun DailyCard(
    rec: DailyAttendanceEntity,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayName(rec.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(fullDate(rec.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Weather.from(rec.weather).let { "${it.emoji} ${it.label}" }, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Metric("👷", "${rec.workers}", "عمال", MaterialTheme.colorScheme.primary)
                Metric("🦺", "${rec.foremen}", "مشرفين", Color(0xFFE8890C))
                if (rec.engineers > 0) Metric("👨‍💼", "${rec.engineers}", "مهندسين", Color(0xFF2980B9))
                if (rec.supervisors > 0) Metric("🔎", "${rec.supervisors}", "مراقبين", Color(0xFF8E44AD))
                if (rec.overtimeHours > 0) Metric("🕒", "${rec.overtimeHours}", "س. إضافي", Color(0xFF37B98A))
            }
            if (rec.remarks.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(rec.remarks, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDuplicate) { Icon(Icons.Filled.ContentCopy, contentDescription = "تكرار", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun Metric(emoji: String, value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarView(records: List<DailyAttendanceEntity>, tag: Color, onPickDay: (Long) -> Unit) {
    var monthOffset by remember { mutableIntStateOf(0) }
    val cal = remember(monthOffset) {
        Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset); set(Calendar.DAY_OF_MONTH, 1) }
    }
    val monthName = remember(monthOffset) {
        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("ar")).format(cal.time)
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
    val recordedDays = records.map { dayStart(it.date) }.toSet()

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthOffset-- }) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "السابق") }
            Text(monthName, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { monthOffset++ }) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "التالي") }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            listOf("أحد", "إثن", "ثلا", "أرب", "خمي", "جمع", "سبت").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val cells = firstDow + daysInMonth
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (cInd in 0 until 7) {
                    val cellIndex = r * 7 + cInd
                    val day = cellIndex - firstDow + 1
                    if (day in 1..daysInMonth) {
                        val dayCal = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
                        val dayMillis = dayCal.timeInMillis
                        val has = dayStart(dayMillis) in recordedDays
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                onClick = { onPickDay(dayMillis) },
                                shape = CircleShape,
                                color = if (has) tag.copy(alpha = 0.15f) else Color.Transparent,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text("$day", style = MaterialTheme.typography.bodyMedium, fontWeight = if (has) FontWeight.Bold else FontWeight.Normal)
                                    if (has) Box(Modifier.size(5.dp).background(tag, CircleShape))
                                }
                            }
                        }
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f)) {}
                    }
                }
            }
        }
    }
}

private fun shareDaily(context: android.content.Context, file: AttendanceFileEntity, rec: DailyAttendanceEntity) {
    val text = buildString {
        appendLine("حضور ${file.company} — ${Trade.from(file.trade).label}")
        appendLine("دور ${file.level} · ${fullDate(rec.date)}")
        appendLine("عمال: ${rec.workers} · مشرفين: ${rec.foremen}")
        if (rec.engineers > 0) appendLine("مهندسين: ${rec.engineers}")
        if (rec.supervisors > 0) appendLine("مراقبين: ${rec.supervisors}")
        if (rec.overtimeHours > 0) appendLine("ساعات إضافية: ${rec.overtimeHours}")
        appendLine("الطقس: ${Weather.from(rec.weather).label}")
        if (rec.remarks.isNotBlank()) appendLine("ملاحظات: ${rec.remarks}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "مشاركة الحضور").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
