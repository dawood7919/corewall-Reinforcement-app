package com.corewall.qaqc.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.model.ElementCategory
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.data.model.PlanElement
import com.corewall.qaqc.domain.ActiveRangeResult
import com.corewall.qaqc.ui.ColorDot
import com.corewall.qaqc.ui.LevelSelector
import com.corewall.qaqc.ui.theme.LocalCategoryColors
import com.corewall.qaqc.ui.theme.StatusColors
import kotlin.math.min

@Composable
fun PlanScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val namingMode by vm.namingMode.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val inspections by vm.inspections.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var showExport by remember { mutableStateOf(false) }

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
            PlanCanvas(
                vm = vm,
                level = level,
                namingMode = namingMode,
                names = names,
                inspections = inspections,
                showNames = settings.showNames,
                showStatuses = settings.showStatuses,
                onTapElement = { vm.selectElement(it.id) }
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

/**
 * المسقط التفاعلي: Pinch-to-zoom + Pan بإصبعين، دبل-تاب للتكبير السريع،
 * ولمسة واحدة على أي عنصر تفتح تفاصيله.
 */
@Composable
fun PlanCanvas(
    vm: MainViewModel,
    level: String,
    namingMode: Boolean,
    names: Map<String, String>,
    inspections: Map<Pair<String, String>, String>,
    showNames: Boolean,
    showStatuses: Boolean,
    onTapElement: (PlanElement) -> Unit
) {
    val planData = vm.planData
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val logic = vm.logic
    val catColors = LocalCategoryColors.current
    val selectedId by vm.selectedElementId.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onBackground
    val selectionColor = MaterialTheme.colorScheme.primary
    val gapColor = Color(0xFFFF9F0A)

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val vb = planData.viewBoxRect // minX, minY, w, h

    // fit-to-canvas: fitP = (plan - vbMin) * base + baseOff
    fun baseTransform(size: IntSize): Pair<Float, Offset> {
        if (size.width == 0 || size.height == 0) return 1f to Offset.Zero
        val base = (min(size.width / vb[2], size.height / vb[3]) * 0.97).toFloat()
        val off = Offset(
            ((size.width - vb[2] * base) / 2).toFloat(),
            ((size.height - vb[3] * base) / 2).toFloat()
        )
        return base to off
    }

    fun screenRect(el: PlanElement, base: Float, baseOff: Offset): Rect {
        val x = ((el.x - vb[0]) * base + baseOff.x).toFloat() * scale + offset.x
        val y = ((el.y - vb[1]) * base + baseOff.y).toFloat() * scale + offset.y
        val w = (el.width * base).toFloat() * scale
        val h = (el.height * base).toFloat() * scale
        return Rect(x, y, x + w, y + h)
    }

    fun hitTest(pos: Offset): PlanElement? {
        val (base, baseOff) = baseTransform(canvasSize)
        val slop = 12f
        return planData.elements.lastOrNull { el ->
            val r = screenRect(el, base, baseOff)
            Rect(r.left - slop, r.top - slop, r.right + slop, r.bottom + slop).contains(pos)
        }
    }

    // نتيجة المدى الشغّال لكل عنصر متسمّي في الدور الحالي
    val activeByElement = remember(schedule, level, names) {
        planData.elements.associate { el ->
            val mark = names[el.id]
            el.id to (mark?.let { logic.activeRange(schedule, it, level) })
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 15f)
                    val z = newScale / scale
                    offset = Offset(
                        offset.x * z + centroid.x * (1 - z) + pan.x,
                        offset.y * z + centroid.y * (1 - z) + pan.y
                    )
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { pos ->
                        if (scale > 2.2f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            val z = 3f
                            offset = Offset(
                                offset.x * z + pos.x * (1 - z),
                                offset.y * z + pos.y * (1 - z)
                            )
                            scale *= z
                        }
                    },
                    onTap = { pos -> hitTest(pos)?.let(onTapElement) }
                )
            }
    ) {
        val (base, baseOff) = baseTransform(IntSize(size.width.toInt(), size.height.toInt()))

        for (el in planData.elements) {
            val r = screenRect(el, base, baseOff)
            val mark = names[el.id]
            val active = activeByElement[el.id]
            val statusName = mark?.let { inspections[el.id to level] }
            val status = InspectionStatus.from(statusName)

            val baseColor = catColors.of(el.cat)
            val outOfRange = active is ActiveRangeResult.OutOfRange
            val gap = active is ActiveRangeResult.Gap

            val fill = when {
                showStatuses && status != InspectionStatus.NONE -> StatusColors.of(status)
                else -> baseColor
            }
            drawRect(
                color = fill.copy(alpha = if (outOfRange) 0.18f else 1f),
                topLeft = r.topLeft,
                size = Size(r.width, r.height)
            )

            if (gap) {
                // فجوة بيانات في الجدول عند الدور ده — تحذير مرئي واضح
                drawRect(
                    color = gapColor,
                    topLeft = r.topLeft,
                    size = Size(r.width, r.height),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                    )
                )
            }

            if (namingMode && mark == null) {
                drawRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = r.topLeft,
                    size = Size(r.width, r.height),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                    )
                )
            }

            if (el.id == selectedId) {
                drawRect(
                    color = selectionColor,
                    topLeft = Offset(r.left - 3, r.top - 3),
                    size = Size(r.width + 6, r.height + 6),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            if (showNames && mark != null && (r.width > 46f || r.height > 46f)) {
                val layout = textMeasurer.measure(
                    AnnotatedString(mark),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = labelColor
                    )
                )
                val vertical = el.height > el.width * 1.5
                val cx = r.left + r.width / 2
                val cy = r.top + r.height / 2
                val topLeft = Offset(cx - layout.size.width / 2, cy - layout.size.height / 2)
                if (vertical) {
                    rotate(degrees = -90f, pivot = Offset(cx, cy)) {
                        drawText(layout, topLeft = topLeft)
                    }
                } else {
                    drawText(layout, topLeft = topLeft)
                }
            }
        }
    }
}
