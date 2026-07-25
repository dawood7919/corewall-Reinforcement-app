package com.corewall.qaqc.ui.dataroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TaskEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dueFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)

private enum class TaskFilter(val label: String) {
    ALL("الكل"), OVERDUE("متأخر"), DONE("المنجز")
}

private fun priorityColor(priority: Int): Color = when (priority) {
    2 -> Color(0xFFFF453A)
    1 -> Color(0xFFFF9F0A)
    else -> Color(0xFF8E8E93)
}

private fun priorityLabel(priority: Int): String = when (priority) {
    2 -> "عاجل"
    1 -> "مهم"
    else -> "عادي"
}

/** تبويب المهام: To-Do مودرن — أولويات، تواريخ استحقاق، ربط بدور، فلاتر وتقدّم. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(TaskFilter.ALL) }
    var editTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val filtered = when (filter) {
        TaskFilter.ALL -> tasks.filter { !it.done }
        TaskFilter.OVERDUE -> tasks.filter { !it.done && (it.dueDate ?: Long.MAX_VALUE) < now }
        TaskFilter.DONE -> tasks.filter { it.done }
    }
    val doneCount = tasks.count { it.done }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("مهام دور $level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "$doneCount / ${tasks.size} منجزة في الدور ده",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("مهمة جديدة")
                }
            }
            if (tasks.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { doneCount.toFloat() / tasks.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Text(
                if (filter == TaskFilter.DONE) "مفيش مهام منجزة في دور $level"
                else "مفيش مهام في دور $level ✓",
                Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
        ) {
            items(filtered, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    overdue = !task.done && (task.dueDate ?: Long.MAX_VALUE) < now,
                    onToggle = { vm.toggleTaskDone(task) },
                    onEdit = { editTask = task },
                    onDelete = { vm.deleteTask(task.id) }
                )
            }
            if (filter == TaskFilter.DONE && doneCount > 0) {
                item {
                    TextButton(onClick = { vm.deleteCompletedTasks() }) {
                        Text("مسح كل المنجز", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showAdd) {
        TaskEditDialog(
            vm = vm,
            task = null,
            defaultLevel = level,
            onDismiss = { showAdd = false }
        )
    }
    editTask?.let { task ->
        TaskEditDialog(
            vm = vm,
            task = task,
            defaultLevel = level,
            onDismiss = { editTask = null }
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    overdue: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(64.dp)
                    .background(priorityColor(task.priority))
            )
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                if (task.notes.isNotBlank()) {
                    Text(
                        task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        priorityLabel(task.priority),
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor(task.priority),
                        fontWeight = FontWeight.Bold
                    )
                    task.dueDate?.let {
                        Text(
                            "⏰ ${dueFormat.format(Date(it))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "تعديل", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditDialog(
    vm: MainViewModel,
    task: TaskEntity?,
    defaultLevel: String,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var notes by remember { mutableStateOf(task?.notes ?: "") }
    var priority by remember { mutableStateOf(task?.priority ?: 0) }
    var dueDate by remember { mutableStateOf(task?.dueDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "مهمة جديدة · دور $defaultLevel" else "تعديل المهمة") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("المهمة") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("الأولوية:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..2).forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(priorityLabel(p)) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = dueDate != null,
                        onClick = { showDatePicker = true },
                        label = {
                            Text(dueDate?.let { "⏰ ${dueFormat.format(Date(it))}" } ?: "تاريخ استحقاق")
                        },
                        leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null) }
                    )
                    if (dueDate != null) {
                        TextButton(onClick = { dueDate = null }) { Text("مسح") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    vm.upsertTask(
                        TaskEntity(
                            id = task?.id ?: 0,
                            title = title.trim(),
                            notes = notes.trim(),
                            done = task?.done ?: false,
                            priority = priority,
                            dueDate = dueDate,
                            level = task?.level ?: defaultLevel,
                            createdAt = task?.createdAt ?: System.currentTimeMillis(),
                            completedAt = task?.completedAt
                        )
                    )
                    onDismiss()
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dueDate = state.selectedDateMillis
                    showDatePicker = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") } }
        ) {
            DatePicker(state = state)
        }
    }
}
