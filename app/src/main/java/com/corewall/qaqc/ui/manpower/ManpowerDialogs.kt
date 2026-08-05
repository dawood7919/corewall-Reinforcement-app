package com.corewall.qaqc.ui.manpower

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.data.db.AttendanceFileEntity
import com.corewall.qaqc.data.db.DailyAttendanceEntity

// ---------------------------------------------------------------- ملف جديد

@Composable
fun NewAttendanceFileDialog(
    existing: AttendanceFileEntity? = null,
    onDismiss: () -> Unit,
    onSave: (AttendanceFileEntity) -> Unit
) {
    var company by remember { mutableStateOf(existing?.company ?: "") }
    var trade by remember { mutableStateOf(existing?.let { Trade.from(it.trade) } ?: Trade.STEEL_FIXERS) }
    var startDate by remember { mutableLongStateOf(existing?.startDate ?: System.currentTimeMillis()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var colorTag by remember { mutableLongStateOf(existing?.colorTag ?: TAG_COLORS.first()) }
    var showDate by remember { mutableStateOf(false) }
    var tradeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "ملف حضور جديد" else "تعديل ملف الحضور") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = company, onValueChange = { company = it },
                    label = { Text("اسم الشركة / المقاول") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("التخصص", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(onClick = { tradeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(trade.label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = tradeMenu, onDismissRequest = { tradeMenu = false }) {
                        Trade.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(t.label) }, onClick = { trade = t; tradeMenu = false })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("تاريخ البدء: ${shortDate(startDate)}")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("لون الوسم", style = MaterialTheme.typography.labelMedium)
                // الدايرة صغيرة، بس مساحة اللمس ٤٨. قبل كده الزرار نفسه كان
                // 28dp — أقل من الحد الأدنى — وكان **فاضي تماماً** لما ما يكونش
                // مختار، يعني زرار مالوش أي اسم ولا محتوى لقارئ الشاشة.
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TAG_COLORS.forEach { c ->
                        val selected = c == colorTag
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { colorTag = c }
                                )
                                .semantics {
                                    contentDescription =
                                        "وسم ${tagColorName(c)}" + if (selected) " — مختار" else ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(if (selected) 34.dp else 28.dp)
                                    .background(Color(c), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = company.isNotBlank(),
                onClick = {
                    onSave(
                        (existing ?: AttendanceFileEntity(level = "", company = "", trade = "", startDate = 0, createdAt = System.currentTimeMillis()))
                            .copy(
                                company = company.trim(),
                                trade = trade.name,
                                startDate = startDate,
                                notes = notes.trim(),
                                colorTag = colorTag
                            )
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (showDate) DatePickerSheet(startDate, { startDate = it; showDate = false }, { showDate = false })
}

// ---------------------------------------------------------------- حضور يومي

@Composable
fun AddDailyDialog(
    fileId: Long,
    existing: DailyAttendanceEntity? = null,
    onDismiss: () -> Unit,
    onSave: (DailyAttendanceEntity) -> Unit
) {
    var date by remember { mutableLongStateOf(existing?.date ?: System.currentTimeMillis()) }
    var workers by remember { mutableIntStateOf(existing?.workers ?: 0) }
    var foremen by remember { mutableIntStateOf(existing?.foremen ?: 0) }
    var engineers by remember { mutableIntStateOf(existing?.engineers ?: 0) }
    var supervisors by remember { mutableIntStateOf(existing?.supervisors ?: 0) }
    var overtime by remember { mutableStateOf((existing?.overtimeHours ?: 0.0).let { if (it == 0.0) "" else it.toString() }) }
    var weather by remember { mutableStateOf(existing?.let { Weather.from(it.weather) } ?: Weather.SUNNY) }
    var remarks by remember { mutableStateOf(existing?.remarks ?: "") }
    var showDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "تسجيل حضور يوم" else "تعديل الحضور") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("${dayName(date)} · ${fullDate(date)}")
                }
                Spacer(Modifier.height(8.dp))
                NumberRow("عمال", workers) { workers = it }
                NumberRow("مشرفين (فورمان)", foremen) { foremen = it }
                NumberRow("مهندسين", engineers) { engineers = it }
                NumberRow("مراقبين", supervisors) { supervisors = it }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = overtime, onValueChange = { overtime = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("ساعات إضافية (Overtime)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("الطقس", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Weather.entries.forEach { w ->
                        Surface(
                            onClick = { weather = w },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            color = if (weather == w) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text("${w.emoji} ${w.label}", Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = remarks, onValueChange = { remarks = it },
                    label = { Text("ملاحظات (منطقة العمل…)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    (existing ?: DailyAttendanceEntity(fileId = fileId, date = 0, updatedAt = 0)).copy(
                        fileId = fileId,
                        date = date,
                        workers = workers, foremen = foremen, engineers = engineers, supervisors = supervisors,
                        overtimeHours = overtime.toDoubleOrNull() ?: 0.0,
                        weather = weather.name,
                        remarks = remarks.trim()
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (showDate) DatePickerSheet(date, { date = it; showDate = false }, { showDate = false })
}

@Composable
private fun NumberRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        com.corewall.qaqc.ui.theme.SrtStepper(value = value, onChange = onChange, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let(onPick) ?: onDismiss() }) { Text("تم") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    ) { DatePicker(state = state) }
}
