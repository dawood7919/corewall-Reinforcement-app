package com.corewall.qaqc.ui.dataroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TaskEntity
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwProgressBar
import com.corewall.qaqc.ui.design.CwSegmented
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.semantic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dueFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)

private enum class TaskFilter(val label: String) {
    OPEN("مفتوحة"), OVERDUE("متأخرة"), DONE("منجزة")
}

/** الأولوية بتاخد نبرة من النظام — مش لون مكتوب بالإيد. */
private fun priorityTone(priority: Int): CwTone = when (priority) {
    2 -> CwTone.Danger
    1 -> CwTone.Warning
    else -> CwTone.Neutral
}

private fun priorityLabel(priority: Int): String = when (priority) {
    2 -> "عاجل"
    1 -> "مهم"
    else -> "عادي"
}

/**
 * مهام الدور.
 *
 * اللي اتصلّح هنا: زرار الحذف كان بيمسح المهمة فوراً من غير أي تأكيد ومن
 * غير تراجع؛ ألوان الأولوية كانت مكتوبة بالإيد وساقطة في التباين؛ الفلتر
 * كان بيرجع لأوّله مع كل لفّة شاشة؛ والحالة الفاضية كانت بتتعرض **فوق**
 * القايمة مش بدالها.
 */
@Composable
fun TasksScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()

    var filter by rememberSaveable { mutableStateOf(TaskFilter.OPEN) }
    var editTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var confirmClearDone by rememberSaveable { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    fun isOverdue(t: TaskEntity) = !t.done && (t.dueDate ?: Long.MAX_VALUE) < now

    val filters = TaskFilter.entries
    val shown = when (filter) {
        TaskFilter.OPEN -> tasks.filter { !it.done }
        TaskFilter.OVERDUE -> tasks.filter { isOverdue(it) }
        TaskFilter.DONE -> tasks.filter { it.done }
    }
    val doneCount = tasks.count { it.done }
    val overdueCount = tasks.count { isOverdue(it) }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Space.screen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "$doneCount من ${tasks.size} منجزة",
                        style = CwText.metricSmall,
                        color = c.textPrimary
                    )
                    Text(
                        "مهام دور $level بس — معزولة عن باقي الأدوار",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textTertiary
                    )
                }
                CwButton("مهمة جديدة", { showAdd = true }, icon = Icons.Filled.Add)
            }
            if (tasks.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                CwProgressBar(
                    fraction = doneCount.toFloat() / tasks.size,
                    tone = if (overdueCount > 0) CwTone.Warning else CwTone.Success
                )
            }
            Spacer(Modifier.height(Space.md))
            CwSegmented(
                options = filters,
                selectedIndex = filters.indexOf(filter),
                label = { f ->
                    val n = when (f) {
                        TaskFilter.OPEN -> tasks.count { !it.done }
                        TaskFilter.OVERDUE -> overdueCount
                        TaskFilter.DONE -> doneCount
                    }
                    "${f.label} ($n)"
                },
                onSelect = { filter = filters[it] }
            )
        }

        if (shown.isEmpty()) {
            CwEmptyState(
                icon = Icons.Filled.Checklist,
                title = when (filter) {
                    TaskFilter.DONE -> "مفيش مهام منجزة لسه"
                    TaskFilter.OVERDUE -> "مفيش مهام متأخرة"
                    TaskFilter.OPEN -> "مفيش مهام مفتوحة في دور $level"
                },
                detail = when (filter) {
                    TaskFilter.OVERDUE -> "كل المهام اللي ليها تاريخ لسه في وقتها."
                    else -> "المهمة بتتحفظ مع الدور ده لوحده — لو بدّلت الدور مش هتشوفها."
                },
                action = if (filter == TaskFilter.OPEN) ({
                    CwButton("ضيف أول مهمة", { showAdd = true }, icon = Icons.Filled.Add)
                }) else null
            )
            return@Column
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Space.screen, end = Space.screen,
                top = Space.md, bottom = Space.bottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            items(shown, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    overdue = isOverdue(task),
                    onToggle = { vm.toggleTaskDone(task) },
                    onEdit = { editTask = task },
                    onDelete = { confirmDelete = task }
                )
            }
            if (filter == TaskFilter.DONE && doneCount > 0) {
                item(key = "clear-done") {
                    Spacer(Modifier.height(Space.sm))
                    CwButton(
                        "امسح كل المنجز ($doneCount)",
                        { confirmClearDone = true },
                        style = CwButtonStyle.Secondary,
                        icon = Icons.Filled.Delete,
                        fillWidth = true
                    )
                }
            }
        }
    }

    if (showAdd) {
        TaskEditDialog(vm = vm, task = null, defaultLevel = level, onDismiss = { showAdd = false })
    }
    editTask?.let { task ->
        TaskEditDialog(vm = vm, task = task, defaultLevel = level, onDismiss = { editTask = null })
    }

    // الحذف بقى بيسأل. قبل كده كانت ضغطة واحدة بتمسح من غير رجعة.
    confirmDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("تمسح المهمة دي؟") },
            text = { Text("\"${task.title}\" هتتشال نهائي ومفيش تراجع.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTask(task.id); confirmDelete = null }) {
                    Text("امسح", color = c.danger.fg)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("سيبها") }
            }
        )
    }

    if (confirmClearDone) {
        AlertDialog(
            onDismissRequest = { confirmClearDone = false },
            title = { Text("تمسح كل المهام المنجزة؟") },
            text = { Text("$doneCount مهمة هتتشال نهائي من دور $level.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteCompletedTasks(); confirmClearDone = false }) {
                    Text("امسح الكل", color = c.danger.fg)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDone = false }) { Text("سيبها") }
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    overdue: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalCwColors.current
    val tone = priorityTone(task.priority)

    CwCard(
        style = if (task.done) CwCardStyle.Plain else CwCardStyle.Accent,
        accent = tone.semantic().solid,
        contentPadding = PaddingValues(
            start = Space.sm, end = Space.sm,
            top = Space.sm, bottom = Space.sm
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = c.success.solid,
                    uncheckedColor = c.outline,
                    checkmarkColor = c.success.onSolid
                )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) c.textTertiary else c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.notes.isNotBlank()) {
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    if (task.priority > 0) {
                        CwStatusBadge(priorityLabel(task.priority), tone, compact = true)
                    }
                    task.dueDate?.let { due ->
                        CwStatusBadge(
                            label = (if (overdue) "متأخرة · " else "") + dueFormat.format(Date(due)),
                            tone = if (overdue) CwTone.Danger else CwTone.Neutral,
                            compact = true
                        )
                    }
                }
            }
            CwIconButton(Icons.Filled.Edit, "تعديل المهمة", onEdit)
            CwIconButton(Icons.Filled.Delete, "حذف المهمة", onDelete)
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
                Spacer(Modifier.height(Space.sm))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.sm))
                Text("الأولوية:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    (0..2).forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(priorityLabel(p)) }
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
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
