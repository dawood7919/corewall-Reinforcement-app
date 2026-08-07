package com.corewall.qaqc.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.Lens
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.domain.CalloutResult
import com.corewall.qaqc.domain.SteelCalculator
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwKeyValue
import com.corewall.qaqc.ui.design.CwKeyValueList
import com.corewall.qaqc.ui.design.CwListItem
import com.corewall.qaqc.ui.design.CwSectionHeader
import com.corewall.qaqc.ui.design.CwStatusBadge
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space

/**
 * أدوات التحليل — شاشة تالتة كانت مبنيّة ومحدش يقدر يوصلها.
 *
 * فيها تلات أدوات مالهمش علاقة ببعض غير إنهم كلهم "حسابات": بحث بالكود،
 * حاسبة مساحة حديد، وملخّص جدول الدور. اتساب مكانهم زي ما هو لأنهم شغّالين
 * فعلاً — اللي اتغيّر إن كل واحدة بقت كارت له عنوان، بدل ما يكونوا تلات
 * كتل نص ورا بعض بعناوين إيموچي.
 */
@Composable
fun ToolsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.screen, end = Space.screen,
            top = Space.md, bottom = Space.bottomInset
        ),
        verticalArrangement = Arrangement.spacedBy(Space.stack)
    ) {
        item(key = "search-header") { CwSectionHeader("دوّر بالكود") }
        item(key = "search") { SearchSection(vm) }

        item(key = "calc-header") { CwSectionHeader("حاسبة مساحة الحديد") }
        item(key = "calc") { CalculatorSection() }

        item(key = "summary-header") { CwSectionHeader("ملخّص جدول الدور") }
        item(key = "summary") { LevelSummarySection(vm) }
    }
}

// ────────────────────────────────────────────────────────────── البحث

@Composable
private fun SearchSection(vm: MainViewModel) {
    val c = LocalCwColors.current
    var query by rememberSaveable { mutableStateOf("") }

    val allMarks by vm.marks.collectAsStateWithLifecycle()
    val results = remember(query, allMarks) {
        if (query.isBlank()) emptyList()
        else allMarks
            .filter { it.contains(query.trim(), ignoreCase = true) }
            .take(15)
    }

    CwCard {
        CwField(
            value = query,
            onValueChange = { query = it },
            label = "كود العنصر",
            placeholder = "T1-W… أو T1-CB…",
            helper = "الدوس على نتيجة بيفتح العنصر على المسقط"
        )
        if (query.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            if (results.isEmpty()) {
                Text(
                    "مفيش كود بيطابق \"$query\" في الجدول",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary
                )
            } else {
                results.forEach { mark ->
                    val element = vm.elementForMark(mark)
                    CwListItem(
                        title = mark,
                        subtitle = if (element != null) null else "مش متسمّي على المسقط لسه",
                        trailing = {
                            if (element != null) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBackIos,
                                    contentDescription = null,
                                    tint = c.textTertiary,
                                    modifier = Modifier.size(IconSize.sm)
                                )
                            } else {
                                CwStatusBadge("بدون شكل", CwTone.Warning, compact = true)
                            }
                        },
                        onClick = if (element != null) ({
                            vm.selectElement(element.id)
                            vm.goToLens(Lens.REINF)
                        }) else null
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────── الحاسبة

@Composable
private fun CalculatorSection() {
    val c = LocalCwColors.current
    var input by rememberSaveable { mutableStateOf("T25-200") }
    val results = remember(input) { SteelCalculator.parseList(input) }

    CwCard {
        CwField(
            value = input,
            onValueChange = { input = it },
            label = "الكولاوت",
            placeholder = "T25-200",
            helper = "يقبل T25-200 أو 6T32 أو أكتر من واحد مفصولين بفاصلة",
            error = if (results == null && input.isNotBlank()) "الكولاوت مش مفهوم" else null
        )

        if (results != null && results.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            results.forEach { r ->
                val (line, total) = when (r) {
                    is CalloutResult.Spaced ->
                        "قطر ${r.diaMm}mm كل ${r.spacingMm}mm" to r.totalDescription
                    is CalloutResult.Counted ->
                        "${r.count} سيخ قطر ${r.diaMm}mm" to r.totalDescription
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                Text(total, style = CwText.code, color = c.textPrimary)
                Spacer(Modifier.height(Space.sm))
            }

            val spaced = results.filterIsInstance<CalloutResult.Spaced>()
            if (spaced.size > 1) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "الإجمالي %.0f mm²/m".format(spaced.sumOf { it.areaPerMeterMm2 }),
                    style = CwText.metricSmall,
                    color = c.accent
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────── ملخّص الدور

@Composable
private fun LevelSummarySection(vm: MainViewModel) {
    val c = LocalCwColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()

    val levelIdx = vm.logic.idx(level)
    if (levelIdx == null) {
        CwCard {
            Text(
                "الدور $level مش موجود في الجدول.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textTertiary
            )
        }
        return
    }

    val activeWalls = schedule.walls.count { (_, rows) -> vm.logic.activeWallRow(rows, levelIdx) != null }
    val activeBeams = schedule.beams.count { (_, rows) -> vm.logic.activeBeamRow(rows, levelIdx) != null }
    val gapWalls = schedule.walls.filter { (_, rows) -> vm.logic.wallGapAt(rows, levelIdx) }.keys
    val gapBeams = schedule.beams.filter { (_, rows) -> vm.logic.beamGapAt(rows, levelIdx) }.keys
    val gaps = gapWalls + gapBeams

    val statusCounts = InspectionStatus.entries
        .filter { it != InspectionStatus.NONE }
        .associateWith { status ->
            vm.planData.elements.count { el ->
                InspectionStatus.from(inspections[el.id to level]) == status
            }
        }

    CwCard(
        style = if (gaps.isEmpty()) CwCardStyle.Plain else CwCardStyle.Accent,
        accent = c.warning.solid
    ) {
        CwKeyValueList(
            listOf(
                CwKeyValue("حوائط في الجدول", "$activeWalls"),
                CwKeyValue("كمرات في الجدول", "$activeBeams"),
                CwKeyValue("عناصر مسمّية على المسقط", "${names.size} من ${vm.planData.elements.size}")
            )
        )

        if (gaps.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            Text(
                "فجوات في دور $level",
                style = CwText.sectionLabel,
                color = c.textTertiary
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                gaps.joinToString("، "),
                style = CwText.codeSmall,
                color = c.warning.fg,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(Space.md))
        Text("حالات الفحص", style = CwText.sectionLabel, color = c.textTertiary)
        Spacer(Modifier.height(Space.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            statusCounts.forEach { (status, count) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CwStatusBadge(status.label, toneOf(status), compact = true)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "$count",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (count == 0) c.textTertiary else c.textPrimary
                    )
                }
            }
        }
    }
}

private fun toneOf(status: InspectionStatus): CwTone = when (status) {
    InspectionStatus.APPROVED -> CwTone.Success
    InspectionStatus.CAST -> CwTone.Info
    InspectionStatus.WIR_SUBMITTED -> CwTone.Pending
    InspectionStatus.REJECTED -> CwTone.Danger
    InspectionStatus.NONE -> CwTone.Neutral
}
