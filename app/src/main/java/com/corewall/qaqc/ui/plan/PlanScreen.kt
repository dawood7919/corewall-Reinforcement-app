package com.corewall.qaqc.ui.plan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.domain.ActiveRangeResult
import com.corewall.qaqc.ui.ColorDot
import com.corewall.qaqc.ui.LevelSelector
import com.corewall.qaqc.ui.theme.LocalCategoryColors
import com.corewall.qaqc.ui.theme.StatusColors

@Composable
fun PlanScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val namingMode by vm.namingMode.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selectedId by vm.selectedElementId.collectAsStateWithLifecycle()

    var showExport by remember { mutableStateOf(false) }

    val catColors = LocalCategoryColors.current
    val labelColor = MaterialTheme.colorScheme.onBackground
    val gapColor = Color(0xFFFF9F0A)

    // نتيجة المدى الشغّال لكل عنصر متسمّي في الدور الحالي
    val activeByElement = remember(schedule, level, names) {
        vm.planData.elements.associate { el ->
            val mark = names[el.id]
            el.id to (mark?.let { vm.logic.activeRange(schedule, it, level) })
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LevelSelector(
                levels = vm.levels,
                current = level,
                onPick = vm::setLevel,
                onStep = vm::stepLevel
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.setNamingMode(!namingMode) }) {
                Icon(
                    Icons.Filled.DriveFileRenameOutline,
                    contentDescription = "وضع التسمية",
                    tint = if (namingMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showExport = true }) {
                Icon(Icons.Filled.IosShare, contentDescription = "تصدير")
            }
        }

        if (namingMode) {
            val total = vm.planData.elements.size
            val named = names.size
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(
                    "وضع التسمية: $named / $total عنصر",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else named.toFloat() / total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            InteractivePlanCanvas(
                planData = vm.planData,
                selectedId = selectedId,
                backgroundColor = MaterialTheme.colorScheme.background,
                selectionColor = MaterialTheme.colorScheme.primary,
                fillFor = { el ->
                    val mark = names[el.id]
                    val active = activeByElement[el.id]
                    val status = InspectionStatus.from(mark?.let { inspections[el.id to level] })
                    val fill = if (settings.showStatuses && status != InspectionStatus.NONE)
                        StatusColors.of(status) else catColors.of(el.cat)
                    fill.copy(alpha = if (active is ActiveRangeResult.OutOfRange) 0.18f else 1f)
                },
                strokeFor = { el ->
                    val mark = names[el.id]
                    when {
                        activeByElement[el.id] is ActiveRangeResult.Gap ->
                            PlanStroke(gapColor, 3f, dashed = true)
                        namingMode && mark == null ->
                            PlanStroke(Color.White.copy(alpha = 0.9f), 1.5f, dashed = true)
                        else -> null
                    }
                },
                labelFor = { el ->
                    val mark = names[el.id]
                    if (settings.showNames && mark != null)
                        PlanLabel(mark, labelColor, scaleWithPlan = false)
                    else null
                },
                onTapElement = { vm.selectElement(it.id) },
                modifier = Modifier.fillMaxSize()
            )
        }

        LegendRow()
    }

    if (showExport) {
        ExportDialog(vm = vm, onDismiss = { showExport = false })
    }
}

@Composable
private fun LegendRow() {
    val cat = LocalCategoryColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(cat.wall, "حوائط")
        LegendItem(cat.couplingBeam, "كابلينج بيم")
        LegendItem(cat.internalBeam, "بيمات داخلية")
        LegendItem(StatusColors.of(InspectionStatus.WIR_SUBMITTED), "WIR")
        LegendItem(StatusColors.of(InspectionStatus.APPROVED), "مقبول")
        LegendItem(StatusColors.of(InspectionStatus.CAST), "تم الصب")
        LegendItem(StatusColors.of(InspectionStatus.REJECTED), "مرفوض")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
