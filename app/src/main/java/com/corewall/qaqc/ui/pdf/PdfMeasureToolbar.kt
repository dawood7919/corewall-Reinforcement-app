package com.corewall.qaqc.ui.pdf

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.corewall.qaqc.pdfengine.MeasureKind
import com.corewall.qaqc.pdfengine.MeasureUnit
import com.corewall.qaqc.pdfengine.Scale
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as CwStroke

/**
 * شريط القياس — بيحلّ محل شريط التعليم لما وضع القياس يشتغل.
 *
 * ليه بديل مش إضافة: الأداتين بيتنافسوا على نفس اللمسة. لو الاتنين
 * ظاهرين، نقرة على الرسمة يا إما بترسم يا إما بتقيس، والمستخدم مش هيعرف
 * أنهي واحدة غير لما يجرّب. وضع واحد في المرة بيشيل الغموض ده كله.
 */
@Composable
fun PdfMeasureToolbar(
    session: MeasureSession,
    scale: Scale?,
    canUndoPoint: Boolean,
    canFinish: Boolean,
    hasSaved: Boolean,
    onKind: (MeasureKind) -> Unit,
    onUndoPoint: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onCalibrate: () -> Unit,
    onClearPage: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current

    Column(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── حالة المعايرة: أهم معلومة في الوضع ده
        Surface(
            shape = Radius.pill,
            color = if (scale == null) c.warning.container else c.surface,
            shadowElevation = Elevation.raised,
            border = androidx.compose.foundation.BorderStroke(
                CwStroke.hair,
                if (scale == null) c.warning.fg else c.outline
            ),
            modifier = Modifier.padding(bottom = Space.sm)
        ) {
            Row(
                Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Text(
                    if (scale == null) "الصفحة دي مش معايَرة — أي رقم هيبقى تخمين"
                    else "المقياس: ${scale.note.ifBlank { "معايرة" }} · ${scale.unit.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (scale == null) c.warning.onContainer else c.textSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "عايِر",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.accent,
                    modifier = Modifier
                        .clickable { onCalibrate() }
                        .padding(start = Space.xs)
                )
            }
        }

        Surface(
            shape = Radius.pill,
            color = c.surface,
            shadowElevation = Elevation.floating,
            border = androidx.compose.foundation.BorderStroke(CwStroke.hair, c.outline)
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.xs, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xxs)
            ) {
                CwIconButton(Icons.Filled.Close, "اخرج من القياس", onExit)
                Divider()

                MeasureKind.entries.forEach { kind ->
                    CwChip(
                        label = kind.label,
                        selected = !session.calibrating && session.kind == kind,
                        onClick = { onKind(kind) }
                    )
                }

                Divider()
                CwIconButton(
                    Icons.AutoMirrored.Filled.Undo, "شيل آخر نقطة", onUndoPoint,
                    enabled = canUndoPoint
                )
                CwIconButton(
                    Icons.Filled.Check,
                    if (session.calibrating) "استخدم الخط ده للمعايرة" else "خلّص القياس",
                    onFinish,
                    tint = c.accent,
                    enabled = canFinish
                )
                CwIconButton(
                    Icons.Filled.Close, "إلغاء القياس الحالي", onCancel,
                    enabled = canUndoPoint
                )
                CwIconButton(
                    Icons.Filled.DeleteSweep, "امسح قياسات الصفحة", onClearPage,
                    tint = c.danger.fg, enabled = hasSaved
                )
            }
        }
    }
}

@Composable
private fun Divider() {
    val c = LocalCwColors.current
    Box(
        Modifier
            .padding(horizontal = Space.xxs)
            .height(DIVIDER_HEIGHT)
            .width(CwStroke.hair)
            .background(c.outline)
    )
}

private val DIVIDER_HEIGHT = 28.dp

// ══════════════════════════════════════════════════════════ المعايرة

/**
 * ورقة المعايرة.
 *
 * الطريقين معروضين جنب بعض عن قصد. المقياس القياسي أسرع وبيظبط في
 * الملفات اللي اتصدّرت صح، والمعايرة بخط معلوم هي اللي بتنقذ الملفات
 * اللي اتعملها scale وقت الطباعة — والتانية دي أكتر بكتير في الموقع.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureCalibrationSheet(
    current: Scale?,
    referenceLengthPt: Double?,
    onRatio: (Int) -> Unit,
    onReference: (realLength: Double, unit: MeasureUnit) -> Unit,
    onStartReference: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var lengthText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(MeasureUnit.MM) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.lg)
        ) {
            Text("معايرة المقياس", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(Space.xxs))
            Text(
                current?.let { "المقياس الحالي: ${it.note.ifBlank { "معايرة يدوية" }}" }
                    ?: "الصفحة دي لسه من غير مقياس.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary
            )

            SectionLabel("مقياس قياسي")
            Text(
                "دقيق لو الملف اتصدّر بمقاسه الحقيقي من الأوتوكاد.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textTertiary,
                modifier = Modifier.padding(bottom = Space.sm)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Scale.COMMON_RATIOS.forEach { ratio ->
                    CwChip(
                        label = "١:$ratio",
                        selected = current?.note == "١:$ratio",
                        onClick = { onRatio(ratio) }
                    )
                }
            }

            SectionLabel("معايرة بخط معلوم")
            if (referenceLengthPt == null) {
                Text(
                    "ارسم خط على بُعد مكتوب في الرسمة (مثلاً بحر معروف)، وبعدين اكتب قيمته.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textTertiary,
                    modifier = Modifier.padding(bottom = Space.sm)
                )
                CwButton(
                    label = "ارسم خط المعايرة",
                    onClick = onStartReference,
                    style = CwButtonStyle.Secondary,
                    fillWidth = true
                )
            } else {
                val tooShort = referenceLengthPt < Scale.MIN_REFERENCE_POINTS
                Text(
                    if (tooShort)
                        "الخط قصير أوي — معايرة على خط قصير بتكبّر أي غلطة. ارسم خط أطول."
                    else "طول الخط على الورق: ${"%.1f".format(referenceLengthPt)} نقطة",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooShort) c.danger.fg else c.textTertiary,
                    modifier = Modifier.padding(bottom = Space.sm)
                )
                CwField(
                    value = lengthText,
                    onValueChange = { lengthText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "الطول الحقيقي",
                    placeholder = "مثلاً 6000",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    MeasureUnit.entries.forEach { option ->
                        CwChip(
                            label = option.label,
                            selected = unit == option,
                            onClick = { unit = option }
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
                CwButton(
                    label = "اعتمد المعايرة",
                    onClick = {
                        lengthText.toDoubleOrNull()?.let { onReference(it, unit) }
                    },
                    enabled = !tooShort && (lengthText.toDoubleOrNull() ?: 0.0) > 0.0,
                    fillWidth = true
                )
            }

            if (current != null) {
                Spacer(Modifier.height(Space.md))
                CwButton(
                    label = "شيل المعايرة",
                    onClick = onClear,
                    style = CwButtonStyle.Ghost,
                    fillWidth = true
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalCwColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = c.textSecondary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = Space.lg, bottom = Space.xs)
    )
}
