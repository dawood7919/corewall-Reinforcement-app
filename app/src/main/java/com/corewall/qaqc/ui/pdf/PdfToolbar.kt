package com.corewall.qaqc.ui.pdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.IconSize
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Motion
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as CwStroke

/**
 * شريط الأدوات الطايف.
 *
 * ليه طايف مش مثبّت في أسفل الشاشة: على رسمة A0 كل سنتيمتر من الشاشة
 * بيفرق. الشريط الطايف بياخد عرضه بس، وبيسيب الرسمة ظاهرة حواليه، وبيختفي
 * مع باقي الواجهة بنقرة واحدة.
 *
 * وخصائص الأداة (اللون والسُمك والشفافية) بتظهر **بس لما تكون ماسك أداة**.
 * كنترول ظاهر وانت مش محتاجه مش "غني" — هو زحمة.
 */
/** أدوات في الصف الواحد. تلتاشر زرار على صفّين متوازنين. */
private const val TOOLS_PER_ROW = 7

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfToolbar(
    tool: PdfTool,
    style: ToolStyle,
    onTool: (PdfTool) -> Unit,
    onStyle: (ToolStyle) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    /** كام شكل محدّد دلوقتي — صفر معناها مافيش شريط تحديد. */
    selectedCount: Int = 0,
    onDeleteSelected: () -> Unit = {},
    onRestyleSelected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current

    Column(
        modifier.padding(horizontal = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── شريط التحديد: بيظهر بس لما يكون فيه حاجة محدّدة.
        AnimatedVisibility(
            visible = selectedCount > 0,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit())
        ) {
            Surface(
                shape = Radius.pill,
                color = c.accentContainer,
                shadowElevation = Elevation.floating,
                modifier = Modifier.padding(bottom = Space.sm)
            ) {
                Row(
                    Modifier.padding(horizontal = Space.md, vertical = Space.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    Text(
                        "$selectedCount محدّد",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.onAccentContainer
                    )
                    CwIconButton(
                        Icons.Filled.FormatPaint, "طبّق اللون والسُمك", onRestyleSelected,
                        tint = c.onAccentContainer
                    )
                    CwIconButton(
                        Icons.Filled.DeleteOutline, "امسح المحدّد", onDeleteSelected,
                        tint = c.danger.fg
                    )
                }
            }
        }

        // ── خصائص الأداة. بتظهر كمان مع تحديد شغّال، عشان "طبّق اللون
        // والسُمك" يكون قدّامك اللون اللي هيتطبّق.
        AnimatedVisibility(
            visible = tool.isDrawing || selectedCount > 0,
            enter = fadeIn(Motion.enter()) + expandVertically(Motion.enter()),
            exit = fadeOut(Motion.exit()) + shrinkVertically(Motion.exit())
        ) {
            Surface(
                shape = Radius.shapeXl,
                color = c.surface,
                shadowElevation = Elevation.floating,
                border = androidx.compose.foundation.BorderStroke(CwStroke.hair, c.outline),
                modifier = Modifier.padding(bottom = Space.sm)
            ) {
                Column(Modifier.padding(Space.md)) {
                    ColourRow(style, onStyle)
                    Spacer(Modifier.height(Space.md))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.lg)
                    ) {
                        WidthPicker(style, onStyle, Modifier.weight(1f))
                        OpacityPicker(style, onStyle)
                    }
                }
            }
        }

        // ── الأدوات
        //
        // **بيلتفّوا على صفّين، مابيتمرّروش.** الشريط كان `horizontalScroll`:
        // تلتاشر زرار × ٤٨dp = أكتر من ٦٢٠dp على شاشة عرضها ٤١١ — يعني
        // سحابة المراجعة والمستطيل والدايرة والتراجع والمسح كلهم بره
        // الشاشة، من غير أي علامة إن فيه حاجة تانية.
        //
        // وأسوأ من كده: **الأداة الشغّالة نفسها كانت بتختفي**. لقطة الشريط
        // وأنا ماسك سحابة مراجعة طلعت مفيهاش أي أداة متظللة — الشريط
        // مابيرجعش لمكان الأداة المختارة، فالمستخدم مش عارف هو ماسك إيه.
        // اللفّ بيخلّي كل حاجة ظاهرة والمشكلة تختفي من أصلها.
        Surface(
            shape = Radius.shapeXl,
            color = c.surface,
            shadowElevation = Elevation.floating,
            border = androidx.compose.foundation.BorderStroke(CwStroke.hair, c.outline)
        ) {
            FlowRow(
                Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xxs),
                verticalArrangement = Arrangement.spacedBy(Space.xxs),
                maxItemsInEachRow = TOOLS_PER_ROW
            ) {
                ToolButton(PdfTool.PAN, tool == PdfTool.PAN, style.colorArgb) { onTool(PdfTool.PAN) }
                ToolButton(PdfTool.SELECT, tool == PdfTool.SELECT, style.colorArgb) {
                    onTool(PdfTool.SELECT)
                }

                PdfTool.drawing.forEach { t ->
                    ToolButton(t, tool == t, style.colorArgb) { onTool(t) }
                }

                CwIconButton(
                    Icons.AutoMirrored.Filled.Undo, "تراجع", onUndo,
                    enabled = canUndo
                )
                CwIconButton(
                    Icons.AutoMirrored.Filled.Redo, "إعادة", onRedo,
                    enabled = canRedo
                )
                CwIconButton(
                    Icons.Filled.DeleteSweep, "امسح تعليقات الصفحة", onClear,
                    tint = c.danger.fg, enabled = canUndo
                )
            }
        }
    }
}

/**
 * زرار أداة.
 *
 * الأداة المختارة بتاخد **لون التعليم نفسه** كخلفية، مش لون النظام. كده
 * انت شايف الأداة واللون في مكان واحد من غير ما تدوّر.
 */
@Composable
private fun ToolButton(
    tool: PdfTool,
    active: Boolean,
    colorArgb: Long,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    val accent = if (tool.isDrawing) Color(colorArgb) else c.accent
    val bg by animateColorAsState(
        if (active) accent.copy(alpha = 0.16f) else Color.Transparent,
        Motion.standard(), label = "toolBg"
    )
    val tint by animateColorAsState(
        if (active) accent else c.textSecondary,
        Motion.standard(), label = "toolTint"
    )
    val scale by animateFloatAsState(
        if (active) 1f else 0.92f,
        Motion.standard(), label = "toolScale"
    )

    Surface(
        onClick = onClick,
        shape = Radius.pill,
        color = bg,
        modifier = Modifier.size(Sizes.touch)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                tool.icon,
                contentDescription = tool.label,
                tint = tint,
                modifier = Modifier
                    .size(IconSize.lg)
                    .scale(scale)
            )
        }
    }
}

@Composable
private fun ColourRow(style: ToolStyle, onStyle: (ToolStyle) -> Unit) {
    val c = LocalCwColors.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PDF_PALETTE.forEach { argb ->
            val selected = argb == style.colorArgb
            val ring by animateFloatAsState(
                if (selected) 1f else 0f, Motion.standard(), label = "ring"
            )
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    onClick = { onStyle(style.copy(colorArgb = argb)) },
                    shape = CircleShape,
                    color = Color(argb),
                    modifier = Modifier
                        .size(Sizes.avatarSm)
                        .border(
                            width = (CwStroke.thick.value * (1f + ring)).dp,
                            color = if (selected) c.textPrimary else c.outline,
                            shape = CircleShape
                        )
                ) {}
            }
        }
    }
}

/**
 * اختيار السُمك — نقط بأحجام حقيقية.
 *
 * الأرقام ("٢.٥ نقطة") مابتقولش حاجة لحد. النقطة اللي حجمها هو الحجم
 * الفعلي بتقول كل حاجة من غير قراية.
 */
@Composable
private fun WidthPicker(
    style: ToolStyle,
    onStyle: (ToolStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Column(modifier) {
        Text("السُمك", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        Spacer(Modifier.height(Space.xs))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PDF_WIDTHS.forEach { w ->
                val selected = w == style.widthPt
                Surface(
                    onClick = { onStyle(style.copy(widthPt = w)) },
                    shape = Radius.shapeSm,
                    color = if (selected) c.accentContainer else Color.Transparent,
                    modifier = Modifier.size(Sizes.control)
                ) {
                    Canvas(Modifier.size(Sizes.control)) {
                        val dot = (w / PDF_WIDTHS.last()) * size.minDimension * 0.55f
                        drawLine(
                            color = if (selected) c.accent else c.textSecondary,
                            start = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height / 2),
                            end = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height / 2),
                            strokeWidth = dot.coerceAtLeast(1.5f),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpacityPicker(style: ToolStyle, onStyle: (ToolStyle) -> Unit) {
    val c = LocalCwColors.current
    Column {
        Text("الشفافية", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        Spacer(Modifier.height(Space.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            PDF_OPACITIES.forEach { o ->
                val selected = o == style.opacity
                Surface(
                    onClick = { onStyle(style.copy(opacity = o)) },
                    shape = Radius.shapeSm,
                    color = if (selected) c.accentContainer else Color.Transparent,
                    modifier = Modifier.size(Sizes.control)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(IconSize.lg)
                                .clip(CircleShape)
                                .background(Color(style.colorArgb).copy(alpha = o))
                                .border(
                                    CwStroke.hair,
                                    if (selected) c.accent else c.outline,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * مؤشّر التكبير — بيتحوّل لقايمة اختصارات باللمس.
 * عرض النسبة لوحده معلومة؛ إنها تبقى زرار بيخليها أداة.
 */
@Composable
fun ZoomPill(
    percent: Int,
    onFitWidth: () -> Unit,
    onFitPage: () -> Unit,
    onActual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    Surface(
        shape = Radius.pill,
        color = c.surface.copy(alpha = 0.94f),
        shadowElevation = Elevation.raised,
        border = androidx.compose.foundation.BorderStroke(CwStroke.hair, c.outline),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = Space.xs, vertical = Space.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xxs)
        ) {
            Text(
                "$percent٪",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = Space.sm)
            )
            ZoomAction("عرض", onFitWidth)
            ZoomAction("صفحة", onFitPage)
            ZoomAction("١٠٠٪", onActual)
        }
    }
}

@Composable
private fun ZoomAction(label: String, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(
        onClick = onClick,
        shape = Radius.shapeSm,
        color = Color.Transparent,
        modifier = Modifier.height(Sizes.control)
    ) {
        Box(
            Modifier.padding(horizontal = Space.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = c.accent,
                maxLines = 1
            )
        }
    }
}
