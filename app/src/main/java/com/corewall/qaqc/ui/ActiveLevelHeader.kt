package com.corewall.qaqc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.ElementAttachmentEntity
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.ui.theme.StatusColors
import com.corewall.qaqc.ui.theme.TowerNumberStyle

/**
 * الهيدر الرئيسي: بيوضّح الدور الشغّال كبير وواضح، ومعاه ملخّص سريع لبيانات
 * الدور (فحص/عدّ/ملفات/مهام) — كل حاجة في التطبيق بتخص الدور ده بس.
 * الدوس عليه يفتح قائمة تبديل الدور.
 */
@Composable
fun ActiveLevelHeader(vm: MainViewModel, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }

    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()
    val barCounts by vm.barCounts.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()

    val idx = vm.levels.indexOf(level)

    // إحصائيات الدور الحالي
    val levelInspections = inspections.filterKeys { it.second == level }.values.map { InspectionStatus.from(it) }
    val approvedOrCast = levelInspections.count { it == InspectionStatus.APPROVED || it == InspectionStatus.CAST }
    val rejected = levelInspections.count { it == InspectionStatus.REJECTED }
    val namedCount = names.size
    val countedWalls = barCounts.filter { it.level == level }.map { it.elementId }.distinct().size
    val filesCount = attachments.count { it.level == level && it.type == ElementAttachmentEntity.TYPE_FILE }
    val commentsCount = attachments.count { it.level == level && it.type == ElementAttachmentEntity.TYPE_COMMENT }
    val tasksOpen = tasks.count { !it.done }
    val tasksDone = tasks.count { it.done }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Surface(
                onClick = { showPicker = true },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "الدور الشغّال",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        )
                        Text(
                            level,
                            style = TowerNumberStyle.copy(fontSize = 34.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (idx >= 0) {
                        Text(
                            "${idx + 1} / ${vm.levels.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("بدّل", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(
                    "فحص",
                    "$approvedOrCast" + if (rejected > 0) " · $rejected✕" else "",
                    StatusColors.of(InspectionStatus.APPROVED),
                    Modifier.weight(1f)
                )
                StatPill("عدّ", "$countedWalls", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatPill("ملفات", "${filesCount + commentsCount}", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                StatPill(
                    "مهام",
                    if (tasksOpen + tasksDone == 0) "0" else "$tasksDone/${tasksOpen + tasksDone}",
                    StatusColors.of(InspectionStatus.WIR_SUBMITTED),
                    Modifier.weight(1f)
                )
            }
        }
    }

    if (showPicker) {
        LevelPickerDialog(
            levels = vm.levels,
            current = level,
            onPick = { vm.setLevel(it); showPicker = false },
            onDismiss = { showPicker = false },
            title = "اختار الدور الشغّال"
        )
    }
}

@Composable
private fun StatPill(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(accent, 7)
                Spacer(Modifier.width(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
