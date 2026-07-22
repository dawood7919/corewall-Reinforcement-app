package com.corewall.qaqc.ui.counting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.BarCountEntity
import com.corewall.qaqc.data.model.PlanElement

/** صف قابل للتعديل في الـSheet (قبل الحفظ في Room). */
private data class EntryDraft(var countText: String, var diameter: Int)

/**
 * قائمة تسجيل أعداد الأسياخ الرأسية لجدار: قسم للموقع وقسم للدروينج،
 * كل صف = عدد + مؤشر اختيار القطر. الحفظ بيقفل الـSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountingSheet(vm: MainViewModel, element: PlanElement, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val barCounts by vm.barCounts.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()

    val existing = remember(element.id) { barCounts.filter { it.elementId == element.id } }
    val siteDrafts = remember(element.id) {
        siteOf(existing).map { EntryDraft(it.count.toString(), it.diameter) }
            .ifEmpty { listOf(EntryDraft("", 12)) }
            .toMutableStateList()
    }
    val drawingDrafts = remember(element.id) {
        drawingOf(existing).map { EntryDraft(it.count.toString(), it.diameter) }
            .ifEmpty { listOf(EntryDraft("", 12)) }
            .toMutableStateList()
    }

    fun toEntities(): List<BarCountEntity> {
        fun convert(drafts: List<EntryDraft>, source: String) = drafts.mapNotNull { d ->
            val count = d.countText.trim().toIntOrNull() ?: return@mapNotNull null
            if (count <= 0) null
            else BarCountEntity(elementId = element.id, source = source, diameter = d.diameter, count = count)
        }
        return convert(siteDrafts, BarCountEntity.SOURCE_SITE) +
            convert(drawingDrafts, BarCountEntity.SOURCE_DRAWING)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val mark = names[element.id]
            Text(
                "عدّ الأسياخ الرأسية — ${mark ?: element.id}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            EntrySection(
                title = "الموجود في الموقع",
                drafts = siteDrafts
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            EntrySection(
                title = "كما في الدروينج",
                drafts = drawingDrafts
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveBarCounts(element.id, toEntities()) }) {
                    Text("حفظ وإغلاق")
                }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    }
}

@Composable
private fun EntrySection(title: String, drafts: androidx.compose.runtime.snapshots.SnapshotStateList<EntryDraft>) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))

    drafts.forEachIndexed { index, draft ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft.countText,
                onValueChange = { new ->
                    drafts[index] = draft.copy(countText = new.filter { it.isDigit() }.take(4))
                },
                label = { Text("العدد") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            DiameterPicker(
                diameter = draft.diameter,
                onPick = { drafts[index] = draft.copy(diameter = it) }
            )
            IconButton(onClick = { drafts.removeAt(index) }, enabled = drafts.size > 1 || draft.countText.isNotBlank()) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "حذف الصف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // معاينة حية بنفس صيغة البلان: 22Ø12+4Ø16
    val preview = drafts
        .mapNotNull { d -> d.countText.toIntOrNull()?.takeIf { it > 0 }?.let { it to d.diameter } }
        .sortedBy { it.second }
        .joinToString("+") { (c, dia) -> "${c}Ø${dia}" }
    if (preview.isNotEmpty()) {
        Text(
            preview,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }

    OutlinedButton(onClick = { drafts.add(EntryDraft("", 12)) }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("أضف قطر تاني")
    }
}

@Composable
private fun DiameterPicker(diameter: Int, onPick: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Ø$diameter", fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "اختار القطر")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            STANDARD_DIAMETERS.forEach { dia ->
                DropdownMenuItem(
                    text = { Text("Ø$dia mm") },
                    onClick = { onPick(dia); expanded = false }
                )
            }
        }
    }
}
