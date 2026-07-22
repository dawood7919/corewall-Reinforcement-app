package com.corewall.qaqc.ui.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.domain.SteelCalculator
import com.corewall.qaqc.ui.ColorDot
import com.corewall.qaqc.ui.theme.StatusColors

@Composable
fun ToolsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SearchSection(vm)
        Spacer(Modifier.height(20.dp))
        CalculatorSection()
        Spacer(Modifier.height(20.dp))
        LevelSummarySection(vm)
    }
}

// ---------------------------------------------------------------- البحث

@Composable
private fun SearchSection(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val names by vm.names.collectAsStateWithLifecycle()

    Text("🔍 البحث بالاسم", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("T1-W… / T1-CB…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (query.isNotBlank()) {
        val results = vm.repo.baseSchedule.allMarks
            .filter { it.contains(query.trim(), ignoreCase = true) }
            .take(15)
        results.forEach { mark ->
            val element = vm.elementForMark(mark)
            Surface(
                onClick = {
                    if (element != null) {
                        vm.selectElement(element.id)
                        vm.setTabIndex(0)
                    }
                },
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(mark, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (element != null) "→ على المسقط" else "مش متسمّي على المسقط لسه",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (element != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()
        }
        if (results.isEmpty()) {
            Text(
                "مفيش نتايج",
                Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- الحاسبة

@Composable
private fun CalculatorSection() {
    var input by remember { mutableStateOf("T25-200") }

    Text("🧮 حاسبة مساحة الحديد", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("كولاوت: T25-200 أو 6T32 أو T10-200,T10-200") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    val results = SteelCalculator.parseList(input)
    if (results == null) {
        if (input.isNotBlank()) {
            Text("الكولاوت مش مفهوم", color = MaterialTheme.colorScheme.error)
        }
    } else {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                results.forEach { r ->
                    when (r) {
                        is com.corewall.qaqc.domain.CalloutResult.Spaced -> {
                            Text(
                                "T${r.diaMm}-${r.spacingMm}: قطر ${r.diaMm}mm كل ${r.spacingMm}mm",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                r.totalDescription,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is com.corewall.qaqc.domain.CalloutResult.Counted -> {
                            Text(
                                "${r.count}T${r.diaMm}: ${r.count} أسياخ قطر ${r.diaMm}mm",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                r.totalDescription,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                val spaced = results.filterIsInstance<com.corewall.qaqc.domain.CalloutResult.Spaced>()
                if (spaced.size > 1) {
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "الإجمالي: %.0f mm²/m".format(spaced.sumOf { it.areaPerMeterMm2 }),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- ملخص الدور

@Composable
private fun LevelSummarySection(vm: MainViewModel) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()

    val levelIdx = vm.logic.idx(level)

    Text("📋 ملخص دور $level", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))

    if (levelIdx == null) return

    val activeWalls = schedule.walls.filter { (_, rows) ->
        vm.logic.activeWallRow(rows, levelIdx) != null
    }
    val activeBeams = schedule.beams.filter { (_, rows) ->
        vm.logic.activeBeamRow(rows, levelIdx) != null
    }
    val gapWalls = schedule.walls.filter { (_, rows) -> vm.logic.wallGapAt(rows, levelIdx) }.keys
    val gapBeams = schedule.beams.filter { (_, rows) -> vm.logic.beamGapAt(rows, levelIdx) }.keys

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("حوائط نشطة: ${activeWalls.size} — كمرات نشطة: ${activeBeams.size}")
            val gaps = gapWalls + gapBeams
            if (gaps.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠️ فجوات بيانات في الدور ده: ${gaps.joinToString("، ")}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(6.dp))
            val named = names.size
            val total = vm.planData.elements.size
            Text("عناصر متسمّية على المسقط: $named / $total")

            Spacer(Modifier.height(10.dp))
            Text("حالات الفحص في الدور:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            val statusCounts = InspectionStatus.entries.associateWith { status ->
                vm.planData.elements.count { el ->
                    InspectionStatus.from(inspections[el.id to level]) == status &&
                        (status != InspectionStatus.NONE)
                }
            }
            InspectionStatus.entries.filter { it != InspectionStatus.NONE }.forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    ColorDot(StatusColors.of(status))
                    Spacer(Modifier.width(6.dp))
                    Text("${status.label}: ${statusCounts[status] ?: 0}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
