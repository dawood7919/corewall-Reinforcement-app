package com.corewall.qaqc.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.Lens
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.ElementAttachmentEntity
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.domain.ActiveRangeResult
import com.corewall.qaqc.ui.ColorDot
import com.corewall.qaqc.ui.LevelPickerDialog
import com.corewall.qaqc.ui.counting.CountingExportDialog
import com.corewall.qaqc.ui.counting.drawingOf
import com.corewall.qaqc.ui.counting.formatEntries
import com.corewall.qaqc.ui.counting.siteOf
import com.corewall.qaqc.ui.counting.totalsByDiameter
import com.corewall.qaqc.ui.plan.ExportDialog
import com.corewall.qaqc.ui.plan.InteractivePlanCanvas
import com.corewall.qaqc.ui.plan.PlanLabel
import com.corewall.qaqc.ui.plan.PlanStroke
import com.corewall.qaqc.ui.theme.LocalCategoryColors
import com.corewall.qaqc.ui.theme.StatusColors
import com.corewall.qaqc.ui.theme.TowerNumberStyle

/**
 * الشاشة الرئيسية "البرج هو الواجهة":
 * مسقط واحد + عدسات (تسليح/عدّ/داتا) بتعيد تلوينه، برج حي على الجنب
 * بيبدّل الأدوار بسحبة إبهام، Command bar للبحث، وFAB ذكي حسب العدسة.
 */
@Composable
fun HomeScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val lens by vm.lens.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val barCounts by vm.barCounts.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val namingMode by vm.namingMode.collectAsStateWithLifecycle()
    val selectedId by vm.selectedElementId.collectAsStateWithLifecycle()

    val catColors = LocalCategoryColors.current
    val labelColor = MaterialTheme.colorScheme.onBackground
    val gapColor = Color(0xFFFF9F0A)
    val matchColor = Color(0xFF34C759)
    val mismatchColor = Color(0xFFFF453A)
    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    var showReinfExport by remember { mutableStateOf(false) }
    var showCountExport by remember { mutableStateOf(false) }

    // بيانات العدسات — كلها معزولة بالدور الشغّال بس
    val activeByElement = remember(schedule, level, names) {
        vm.planData.elements.associate { el ->
            el.id to (names[el.id]?.let { vm.logic.activeRange(schedule, it, level) })
        }
    }
    val countsByElement = remember(barCounts, level) {
        barCounts.filter { it.level == level }.groupBy { it.elementId }
    }
    val attByElement = remember(attachments, level) {
        attachments.filter { it.level == level }.groupBy { it.elementId }
    }

    Box(modifier.fillMaxSize()) {
        InteractivePlanCanvas(
            planData = vm.planData,
            selectedId = selectedId,
            backgroundColor = MaterialTheme.colorScheme.background,
            selectionColor = accent,
            fillFor = { el ->
                when (lens) {
                    Lens.REINF -> {
                        val status = InspectionStatus.from(
                            names[el.id]?.let { inspections[el.id to level] }
                        )
                        val fill = if (settings.showStatuses && status != InspectionStatus.NONE)
                            StatusColors.of(status) else catColors.of(el.cat)
                        fill.copy(
                            alpha = if (activeByElement[el.id] is ActiveRangeResult.OutOfRange) 0.18f else 1f
                        )
                    }
                    Lens.COUNT -> catColors.of(el.cat)
                        .copy(alpha = if (countsByElement.containsKey(el.id)) 1f else 0.4f)
                    Lens.DATA -> catColors.of(el.cat)
                        .copy(alpha = if (attByElement.containsKey(el.id)) 1f else 0.35f)
                }
            },
            strokeFor = { el ->
                when (lens) {
                    Lens.REINF -> when {
                        activeByElement[el.id] is ActiveRangeResult.Gap ->
                            PlanStroke(gapColor, 3f, dashed = true)
                        namingMode && names[el.id] == null ->
                            PlanStroke(labelColor.copy(alpha = 0.9f), 1.5f, dashed = true)
                        else -> null
                    }
                    Lens.COUNT -> null
                    Lens.DATA -> if (attByElement.containsKey(el.id))
                        PlanStroke(accent, 2f, dashed = false) else null
                }
            },
            labelFor = { el ->
                when (lens) {
                    Lens.REINF -> {
                        val mark = names[el.id]
                        if (settings.showNames && mark != null)
                            PlanLabel(mark, labelColor, scaleWithPlan = false)
                        else null
                    }
                    Lens.COUNT -> {
                        val entries = countsByElement[el.id] ?: return@InteractivePlanCanvas null
                        val site = siteOf(entries)
                        val drawing = drawingOf(entries)
                        val text = formatEntries(site.ifEmpty { drawing })
                        if (text.isEmpty()) null
                        else PlanLabel(
                            text,
                            when {
                                site.isEmpty() -> dimColor
                                drawing.isEmpty() -> labelColor
                                totalsByDiameter(site) == totalsByDiameter(drawing) -> matchColor
                                else -> mismatchColor
                            },
                            scaleWithPlan = true
                        )
                    }
                    Lens.DATA -> {
                        val items = attByElement[el.id] ?: return@InteractivePlanCanvas null
                        val comments = items.count { it.type == ElementAttachmentEntity.TYPE_COMMENT }
                        val filesCount = items.size - comments
                        val parts = buildList {
                            if (comments > 0) add("💬$comments")
                            if (filesCount > 0) add("📎$filesCount")
                        }
                        if (parts.isEmpty()) null
                        else PlanLabel(parts.joinToString(" "), accent, scaleWithPlan = false)
                    }
                }
            },
            onTapElement = { vm.selectElement(it.id) },
            modifier = Modifier.fillMaxSize()
        )

        // ---------- الطبقة العلوية: Command bar + العدسات ----------
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp)
        ) {
            CommandBar(vm)
            Spacer(Modifier.height(8.dp))
            // العدسات — بتبدّل شكل نفس المسقط، والدور ثابت من الهيدر فوق
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Lens.entries.forEach { l ->
                    FilterChip(
                        selected = lens == l,
                        onClick = { vm.setLens(l) },
                        label = { Text(l.label) }
                    )
                }
            }
            if (lens == Lens.REINF && namingMode) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        val total = vm.planData.elements.size
                        val named = names.size
                        Text(
                            "وضع التسمية: $named / $total",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else named.toFloat() / total },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // ---------- FAB ذكي + Legend ----------
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (lens == Lens.REINF) {
                SmallFloatingActionButton(
                    onClick = { showReinfExport = true },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(Icons.Filled.IosShare, contentDescription = "تصدير")
                }
                Spacer(Modifier.height(8.dp))
            }
            when (lens) {
                Lens.REINF -> ExtendedFloatingActionButton(
                    onClick = { vm.setNamingMode(!namingMode) },
                    icon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                    text = { Text(if (namingMode) "إنهاء التسمية" else "تسمية") },
                    containerColor = if (namingMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer
                )
                Lens.COUNT -> ExtendedFloatingActionButton(
                    onClick = { showCountExport = true },
                    icon = { Icon(Icons.Filled.IosShare, contentDescription = null) },
                    text = { Text("تصدير العدّ") }
                )
                Lens.DATA -> ExtendedFloatingActionButton(
                    onClick = { vm.setTabIndex(2) },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    text = { Text("ملفات الدور") }
                )
            }
        }

        // ---------- Legend ----------
        Surface(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 16.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (lens) {
                    Lens.REINF -> {
                        LegendItem(catColors.wall, "حائط")
                        LegendItem(catColors.couplingBeam, "كابلينج")
                        LegendItem(StatusColors.of(InspectionStatus.APPROVED), "مقبول")
                        LegendItem(StatusColors.of(InspectionStatus.CAST), "صُب")
                        LegendItem(gapColor, "فجوة")
                    }
                    Lens.COUNT -> {
                        LegendItem(matchColor, "مطابق")
                        LegendItem(mismatchColor, "مختلف")
                        LegendItem(dimColor, "دروينج بس")
                    }
                    Lens.DATA -> {
                        LegendItem(accent, "عليه بيانات")
                    }
                }
            }
        }
    }

    if (showReinfExport) ExportDialog(vm = vm, onDismiss = { showReinfExport = false })
    if (showCountExport) CountingExportDialog(vm = vm, onDismiss = { showCountExport = false })
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color, 8)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ---------------------------------------------------------------- Command Bar

@Composable
private fun CommandBar(vm: MainViewModel) {
    var query by remember { mutableStateOf("") }
    val names by vm.names.collectAsStateWithLifecycle()

    Column {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 2.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(Modifier.padding(vertical = 10.dp)) {
                            if (query.isEmpty()) {
                                Text(
                                    "ابحث عن عنصر أو دور…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "مسح")
                    }
                }
            }
        }

        if (query.isNotBlank()) {
            val q = query.trim()
            val markResults = vm.repo.baseSchedule.allMarks
                .filter { it.contains(q, ignoreCase = true) }
                .take(8)
            val levelResults = vm.levels.filter { it.contains(q, ignoreCase = true) }.take(5)

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                LazyColumn(Modifier.height(((markResults.size + levelResults.size).coerceAtMost(6) * 48).dp)) {
                    items(levelResults) { lvl ->
                        Surface(
                            onClick = { vm.setLevel(lvl); query = "" },
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "⬆ روح لدور $lvl",
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    items(markResults) { mark ->
                        val element = vm.elementForMark(mark)
                        Surface(
                            onClick = {
                                if (element != null) {
                                    vm.selectElement(element.id)
                                    query = ""
                                }
                            },
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mark, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (element != null) "→ فتح" else "مش متسمّي لسه",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (element != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

