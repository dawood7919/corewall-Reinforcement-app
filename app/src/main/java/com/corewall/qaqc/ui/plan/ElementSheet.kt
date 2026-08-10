package com.corewall.qaqc.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.corewall.qaqc.data.model.BeamRange
import com.corewall.qaqc.data.model.ElementCategory
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.data.model.PlanElement
import com.corewall.qaqc.data.model.WallRange
import com.corewall.qaqc.domain.ActiveRangeResult
import com.corewall.qaqc.ui.ColorDot
import com.corewall.qaqc.ui.theme.LocalCategoryColors
import com.corewall.qaqc.ui.theme.StatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

private val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH)

/** محتوى عدسة التسليح جوّه الـSheet الموحّد (وضع التسمية أو التفاصيل). */
@Composable
fun ReinforcementSheetContent(vm: MainViewModel, element: PlanElement) {
    val namingMode by vm.namingMode.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        if (namingMode) {
            NamingContent(vm, element)
        } else {
            DetailsContent(vm, element)
        }
    }
}

@Composable
private fun CategoryLabel(cat: ElementCategory) {
    val colors = LocalCategoryColors.current
    val (color, label) = when (cat) {
        ElementCategory.WALL -> colors.wall to "حائط"
        ElementCategory.COUPLING_BEAM -> colors.couplingBeam to "كابلينج بيم"
        ElementCategory.INTERNAL_BEAM -> colors.internalBeam to "بيم داخلي"
        ElementCategory.OTHER -> colors.other to "غير محدد (TODO)"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color)
        Spacer(Modifier.width(Space.sm))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------------------------------------------------------- التسمية

@Composable
private fun NamingContent(vm: MainViewModel, element: PlanElement) {
    val names by vm.names.collectAsStateWithLifecycle()
    val existing = names[element.id] ?: ""
    var input by remember(element.id) { mutableStateOf(existing) }

    Text("تسمية العنصر ${element.id}", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(Space.xs))
    CategoryLabel(element.cat)
    Spacer(Modifier.height (Space.md))

    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("الاسم المرجعي (T1-W… / T1-CB…)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    // `marks` مفتاح في الـremember عن قصد: من غيره، الأكواد اللي بتتستورد
    // وانت فاتح الشيت مابتظهرش لحد ما تقفله وتفتحه تاني.
    val marks by vm.marks.collectAsStateWithLifecycle()
    val suggestions = remember(input, names, marks) {
        vm.availableMarks(element.id)
            .filter { it.contains(input.trim(), ignoreCase = true) || input.isBlank() }
            .take(30)
    }
    if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(Space.sm))
        Text("اقتراحات:", style = MaterialTheme.typography.labelMedium)
        LazyColumn(Modifier.height(160.dp)) {
            items(suggestions) { mark ->
                Surface(
                    onClick = { input = mark },
                    color = if (mark == input) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(mark, Modifier.padding(vertical = Space.md, horizontal = Space.sm))
                }
            }
        }
    }

    Spacer(Modifier.height(Space.md))
    // الحفظ بيقفل الـSheet فوراً — الانتقال للعنصر التالي بزرار منفصل ومقصود
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Button(
            onClick = { vm.saveName(element.id, input) },
            enabled = input.isNotBlank() || existing.isNotBlank()
        ) { Text("حفظ وإغلاق") }
        OutlinedButton(onClick = { vm.openNextUnnamed() }) { Text("التالي غير المسمّى") }
        if (existing.isNotBlank()) {
            TextButton(onClick = { vm.saveName(element.id, "") }) { Text("مسح الاسم") }
        }
    }
}

// ---------------------------------------------------------------- التفاصيل

@Composable
private fun DetailsContent(vm: MainViewModel, element: PlanElement) {
    val names by vm.names.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()
    val allComments by vm.comments.collectAsStateWithLifecycle()

    val mark = names[element.id]

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                mark ?: "عنصر غير مسمّى",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                "(${element.id})",
                Modifier.padding(bottom = Space.xs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryLabel(element.cat)
            Spacer(Modifier.width(Space.md))
            Text("الدور الحالي: $level", style = MaterialTheme.typography.labelMedium)
        }

        if (mark == null) {
            Spacer(Modifier.height(Space.md))
            Text(
                "العنصر ده لسه متسمّاش. فعّل وضع التسمية من فوق عشان تديله اسمه المرجعي.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.sm))
            Button(onClick = { vm.setNamingMode(true) }) { Text("فعّل وضع التسمية") }
            return@Column
        }

        val active = vm.logic.activeRange(schedule, mark, level)
        if (active is ActiveRangeResult.Gap) {
            Spacer(Modifier.height(Space.md))
            GapWarning(vm, mark)
        }

        // -------- حالة الفحص للدور الحالي --------
        Spacer(Modifier.height(Space.lg))
        Text("حالة الفحص — دور $level", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Space.sm))
        val currentStatus = InspectionStatus.from(inspections[element.id to level])
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            InspectionStatus.entries.forEach { status ->
                FilterChip(
                    selected = currentStatus == status,
                    onClick = { vm.setInspection(element.id, status.name) },
                    label = { Text(status.label) },
                    leadingIcon = { ColorDot(StatusColors.of(status)) }
                )
            }
        }

        // -------- جدول المدايات --------
        Spacer(Modifier.height(Space.lg))
        Text("مدايات التسليح", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Space.sm))

        val wallRows = schedule.walls[mark]
        val beamRows = schedule.beams[mark]
        var editRow by remember { mutableStateOf<Int?>(null) }

        val levelIdx = vm.logic.idx(level)
        wallRows?.forEachIndexed { i, row ->
            val isActive = levelIdx != null && vm.logic.wallCovers(row, levelIdx)
            WallRangeCard(row, isActive, onEdit = { editRow = i })
        }
        beamRows?.forEachIndexed { i, row ->
            val isActive = levelIdx != null && vm.logic.beamCovers(row, levelIdx)
            BeamRangeCard(row, isActive, onEdit = { editRow = i })
        }
        if (wallRows == null && beamRows == null) {
            Text(
                "الاسم \"$mark\" مش موجود في جدول التسليح!",
                color = MaterialTheme.colorScheme.error
            )
        }

        editRow?.let { i ->
            EditRangeDialog(
                vm = vm,
                mark = mark,
                rowIndex = i,
                wallRow = wallRows?.getOrNull(i),
                beamRow = beamRows?.getOrNull(i),
                onDismiss = { editRow = null }
            )
        }

        // -------- الكومنتات (معزولة لكل دور) --------
        Spacer(Modifier.height(Space.lg))
        Text("الكومنتات — دور $level", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(Space.sm))
        val comments = remember(allComments, element.id, level) {
            allComments.filter { it.elementId == element.id && it.level == level }
        }
        comments.forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(c.text)
                    Text(
                        "${timeFormat.format(Date(c.timestamp))} — دور ${c.level}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.deleteComment(c.id) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()
        }
        var newComment by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newComment,
                onValueChange = { newComment = it },
                label = { Text("اكتب كومنت…") },
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { vm.addComment(element.id, newComment); newComment = "" },
                enabled = newComment.isNotBlank()
            ) {
                Icon(Icons.Filled.Send, contentDescription = "إضافة")
            }
        }
    }
}

@Composable
private fun GapWarning(vm: MainViewModel, mark: String) {
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val gaps = remember(schedule, mark) { vm.logic.gapLevels(schedule, mark) }
    com.corewall.qaqc.ui.theme.SrtCallout(
        title = "فجوة بيانات",
        body = "الدور الحالي $level ضمن مدى العنصر لكن لا يوجد صف يغطيه.\nالأدوار الناقصة: ${gaps.joinToString("، ")}",
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RangeCardScaffold(
    isActive: Boolean,
    edited: Boolean,
    rev: Boolean,
    note: String?,
    title: String,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(isActive) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
    Surface(
        shape = Radius.shapeLg,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs)
    ) {
        Column {
            // رأس قابل للطي
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xxs)) {
                        if (isActive) StatusBadge("الشغّال الآن", MaterialTheme.colorScheme.primary)
                        if (rev) { Spacer(Modifier.width(Space.sm)); StatusBadge("REV", com.corewall.qaqc.ui.theme.LocalSrtColors.current.purple) }
                        if (edited) { Spacer(Modifier.width(Space.sm)); StatusBadge("معدَّل", com.corewall.qaqc.ui.theme.LocalSrtColors.current.orange) }
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "تعديل القيم", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Icon(
                    Icons.Filled.ExpandMore, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation)
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = Space.lg, end = Space.lg, bottom = Space.lg)) {
                    androidx.compose.material3.HorizontalDivider(color = com.corewall.qaqc.ui.theme.LocalSrtColors.current.divider)
                    Spacer(Modifier.height(Space.md))
                    content()
                    if (note != null) {
                        Spacer(Modifier.height(Space.sm))
                        com.corewall.qaqc.ui.theme.SrtCallout(
                            title = "ملاحظة على الصف",
                            body = note,
                            accent = com.corewall.qaqc.ui.theme.LocalSrtColors.current.orange
                        )
                    }
                }
            }
        }
    }
}

/** مجموعة سبك مع عنوان قسم صغير — بديل الجدول. */
@Composable
private fun SpecGroup(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = com.corewall.qaqc.ui.theme.LocalSrtColors.current.text3,
        modifier = Modifier.padding(top = Space.sm, bottom = Space.xs)
    )
    content()
}

@Composable
private fun StatusBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = Radius.shapeSm) {
        Text(text, Modifier.padding(horizontal = Space.sm, vertical = Space.xxs), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        Modifier
            .background(color.copy(alpha = 0.2f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = Space.sm, vertical = Space.xxs),
        color = color,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
private fun SpecLine(label: String, value: String) {
    Row {
        Text(
            label,
            Modifier.width(90.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // الكولاوتات بخط مونوسبيس (هوية Reimagined)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = com.corewall.qaqc.ui.theme.PlexMono
            ),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun WallRangeCard(row: WallRange, isActive: Boolean, onEdit: () -> Unit) {
    RangeCardScaffold(
        isActive = isActive,
        edited = row.edited,
        rev = row.rev,
        note = row.note,
        title = "من ${row.from} إلى ${row.to ?: "؟"}",
        onEdit = onEdit
    ) {
        SpecGroup("الأبعاد") { SpecLine("السُمك", "${row.w} mm") }
        SpecGroup("التسليح") {
            SpecLine("رأسي V", row.v)
            SpecLine("أفقي H", row.h)
            SpecLine("أطراف T", row.t)
        }
    }
}

@Composable
private fun BeamRangeCard(row: BeamRange, isActive: Boolean, onEdit: () -> Unit) {
    RangeCardScaffold(
        isActive = isActive,
        edited = row.edited,
        rev = row.rev,
        note = row.note,
        title = if (row.to == null) "دور ${row.from} فقط" else "من ${row.from} إلى ${row.to} (شامل)",
        onEdit = onEdit
    ) {
        SpecGroup("الأبعاد") { SpecLine("المقاس", "${row.w} × ${row.d} mm") }
        SpecGroup("التسليح") {
            SpecLine("سفلي B", row.bottom.joinToString(" / "))
            SpecLine("علوي T", row.top.joinToString(" / "))
            SpecLine("جانبي", row.side)
        }
        SpecGroup("الكانات") { SpecLine("كانات", row.links) }
    }
}

// ---------------------------------------------------------------- تعديل القيم

@Composable
private fun EditRangeDialog(
    vm: MainViewModel,
    mark: String,
    rowIndex: Int,
    wallRow: WallRange?,
    beamRow: BeamRange?,
    onDismiss: () -> Unit
) {
    val fields: List<Pair<String, String>> = when {
        wallRow != null -> listOf(
            "w" to wallRow.w.toString(),
            "v" to wallRow.v,
            "h" to wallRow.h,
            "t" to wallRow.t
        )
        beamRow != null -> listOf(
            "w" to beamRow.w.toString(),
            "d" to beamRow.d.toString(),
            "B0" to beamRow.bottom.getOrElse(0) { "-" },
            "B1" to beamRow.bottom.getOrElse(1) { "-" },
            "B2" to beamRow.bottom.getOrElse(2) { "-" },
            "T0" to beamRow.top.getOrElse(0) { "-" },
            "T1" to beamRow.top.getOrElse(1) { "-" },
            "T2" to beamRow.top.getOrElse(2) { "-" },
            "side" to beamRow.side,
            "links" to beamRow.links
        )
        else -> emptyList()
    }
    val labels = mapOf(
        "w" to "السُمك/العرض (mm)", "d" to "العمق (mm)",
        "v" to "رأسي V", "h" to "أفقي H", "t" to "أطراف T",
        "B0" to "سفلي طبقة 1", "B1" to "سفلي طبقة 2", "B2" to "سفلي طبقة 3",
        "T0" to "علوي طبقة 1", "T1" to "علوي طبقة 2", "T2" to "علوي طبقة 3",
        "side" to "جانبي", "links" to "كانات"
    )
    val values = remember { mutableStateOf(fields.toMap()) }

    // القيم الأصلية من الجدول المرجعي (قبل أي تعديلات) عشان الفرق بس هو اللي يتخزن
    val baseValues = remember {
        // الأصل = المكتب + المستورد. الكمرة المستوردة مالهاش صف في جدول
        // المكتب، فلو قارنّا بيه هيتخزّن كل حقولها كتعديل من غير داعي.
        val original = vm.originalSchedule()
        val baseWall = original.walls[mark]?.getOrNull(rowIndex)
        val baseBeam = original.beams[mark]?.getOrNull(rowIndex)
        when {
            baseWall != null -> mapOf(
                "w" to baseWall.w.toString(), "v" to baseWall.v, "h" to baseWall.h, "t" to baseWall.t
            )
            baseBeam != null -> mapOf(
                "w" to baseBeam.w.toString(), "d" to baseBeam.d.toString(),
                "B0" to baseBeam.bottom.getOrElse(0) { "-" },
                "B1" to baseBeam.bottom.getOrElse(1) { "-" },
                "B2" to baseBeam.bottom.getOrElse(2) { "-" },
                "T0" to baseBeam.top.getOrElse(0) { "-" },
                "T1" to baseBeam.top.getOrElse(1) { "-" },
                "T2" to baseBeam.top.getOrElse(2) { "-" },
                "side" to baseBeam.side, "links" to baseBeam.links
            )
            else -> emptyMap()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل $mark — صف ${rowIndex + 1}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "التعديل بيتخزن محلي في التطبيق (Room) — الجدول المرجعي نفسه مش بيتغير.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.sm))
                fields.forEach { (key, _) ->
                    OutlinedTextField(
                        value = values.value[key] ?: "",
                        onValueChange = { values.value = values.value + (key to it) },
                        label = { Text(labels[key] ?: key) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.xxs)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.saveRangeEdit(mark, rowIndex, values.value, baseValues)
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    vm.clearRangeEdit(mark, rowIndex)
                    onDismiss()
                }) { Text("رجّع الأصل") }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
}
