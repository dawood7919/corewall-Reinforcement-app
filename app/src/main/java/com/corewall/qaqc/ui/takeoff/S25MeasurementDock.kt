package com.corewall.qaqc.ui.takeoff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

/**
 * مساحة تحكم للقياس تُبقي الوظائف الأساسية في الثلث السفلي من شاشة الهاتف.
 * تصميمها مستقل عن هوية أي منتج خارجي: يقدّم أدوات القياس المباشرة، القراءة
 * الحية، والإجراءات السياقية فقط، بدلاً من عرض وظائف إدارة المشروع هنا.
 */
@Composable
fun S25MeasurementDock(
    pointerActive: Boolean,
    activeTool: TakeoffTool,
    deducting: Boolean,
    calibrated: Boolean,
    snapEnabled: Boolean,
    liveReadout: String?,
    hasDraft: Boolean,
    selected: Boolean,
    addingToShape: Boolean,
    canAddToShape: Boolean,
    canDeleteVertex: Boolean,
    multiCount: Int,
    onPointer: () -> Unit,
    onUndo: () -> Unit,
    onToggleSnap: () -> Unit,
    onPick: (TakeoffTool) -> Unit,
    onMore: () -> Unit,
    onDone: () -> Unit,
    onAddToShape: () -> Unit,
    onToggleVisible: () -> Unit,
    onDeleteVertex: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteMulti: () -> Unit,
    onEditSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.sm),
        shape = Radius.shapeXl,
        color = c.surfaceRaised,
        shadowElevation = Elevation.floating,
        border = BorderStroke(1.dp, c.outline.copy(alpha = 0.82f))
    ) {
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.md)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                MeasurementStatePill(
                    label = if (calibrated) "المقياس جاهز" else "عاير الرسم",
                    tone = if (calibrated) c.success.fg else c.warning.fg
                )
                if (liveReadout != null) {
                    Text(
                        liveReadout,
                        style = CwText.codeSmall,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        if (deducting) "ارسم فتحة لخصمها من البند المحدد" else "اختر أداة ثم ابدأ القياس",
                        style = CwText.codeSmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                DockCompactAction(
                    Icons.Filled.Calculate,
                    if (snapEnabled) "التقاط" else "حر",
                    if (snapEnabled) c.accent else c.textSecondary,
                    onToggleSnap
                )
                if (hasDraft) {
                    DockCompactAction(Icons.Filled.Undo, "تراجع", c.textPrimary, onUndo)
                    DockCompactAction(Icons.Filled.Check, "إنهاء", c.success.fg, onDone)
                }
            }

            Box(Modifier.padding(top = Space.sm).fillMaxWidth().height(1.dp).background(c.divider))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                DockTool(Icons.Filled.PanTool, "تحديد", pointerActive, Modifier.weight(1f), onPointer)
                DockTool(Icons.Filled.Square, "مساحة", !pointerActive && !deducting && activeTool == TakeoffTool.AREA, Modifier.weight(1f)) { onPick(TakeoffTool.AREA) }
                DockTool(Icons.Filled.Timeline, "طول", !pointerActive && activeTool == TakeoffTool.LENGTH, Modifier.weight(1f)) { onPick(TakeoffTool.LENGTH) }
                DockTool(Icons.Filled.PinDrop, "عد", !pointerActive && activeTool == TakeoffTool.COUNT, Modifier.weight(1f)) { onPick(TakeoffTool.COUNT) }
                DockTool(Icons.Filled.MoreHoriz, "أدوات", !pointerActive && (activeTool == TakeoffTool.VOLUME || activeTool == TakeoffTool.COLUMN || activeTool == TakeoffTool.DIMENSION), Modifier.weight(1f), onMore)
            }

            if (selected || multiCount > 0 || canDeleteVertex || addingToShape) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (addingToShape) DockAction(Icons.Filled.Square, "جزء جديد", c.accent, onPointer)
                    if (canDeleteVertex) DockAction(Icons.Filled.Delete, "حذف الرأس", c.danger.fg, onDeleteVertex)
                    if (selected && !addingToShape && canAddToShape) DockAction(Icons.Filled.AddCircleOutline, "أضف جزءاً", c.accent, onAddToShape)
                    if (selected && !addingToShape) {
                        DockAction(Icons.Filled.OpenWith, "تحرير", c.textPrimary, onEditSelected)
                        DockAction(Icons.Filled.Visibility, "إخفاء", c.textPrimary, onToggleVisible)
                        DockAction(Icons.Filled.Delete, "حذف", c.danger.fg, onDeleteSelected)
                    }
                    if (multiCount > 0) DockAction(Icons.Filled.DeleteSweep, "حذف $multiCount", c.danger.fg, onDeleteMulti)
                }
            }
        }
    }
}

@Composable
private fun DockTool(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = Radius.shapeLg,
        color = if (active) c.accentContainer else c.surfaceAlt,
        border = if (active) BorderStroke(1.dp, c.accent) else null
    ) {
        Column(
            Modifier.padding(vertical = Space.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, null, tint = if (active) c.accent else c.textSecondary, modifier = Modifier.size(IconSize.md))
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) c.accent else c.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun DockAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(
        onClick = onClick,
        shape = Radius.shapeMd,
        color = c.surfaceAlt
    ) {
        Row(
            Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xxs)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(IconSize.sm))
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
        }
    }
}

@Composable
private fun DockCompactAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(onClick = onClick, shape = Radius.shapeMd, color = c.success.container) {
        Row(
            Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xxs)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(IconSize.sm))
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@Composable
private fun MeasurementStatePill(label: String, tone: Color) {
    val c = LocalCwColors.current
    Surface(shape = Radius.shapeMd, color = c.surfaceAlt) {
        Text(
            label,
            style = CwText.codeSmall,
            color = tone,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xxs),
            maxLines = 1
        )
    }
}
