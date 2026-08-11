package com.corewall.qaqc.ui.takeoff

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffMath
import com.corewall.qaqc.takeoff.TakeoffPoint
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlin.math.hypot

/** مم على الورق لكل نقطة PDF: بوصة ÷ ٧٢. */
private const val MM_PER_POINT = 25.4 / 72.0

/** المقاييس المعمارية الشائعة. */
private val RATIOS = listOf(20, 25, 50, 100, 200, 500, 1000)

/**
 * معايرة المقياس.
 *
 * طريقتين، والاتنين مقصودين:
 *
 * ١. **مقياس معماري** (١:٥٠، ١:١٠٠…) — لمسة واحدة، ودقيق تماماً **لو**
 *    الملف اتصدّر بمقاسه الحقيقي. الحساب من نقطة الـPDF مباشرة
 *    (٢٥.٤÷٧٢ مم) — مافيش دقة رسترة في المعادلة، فمفيش ثابت يقدر يخرب
 *    المعايرة لو اتغيّر بعدين.
 * ٢. **بُعد معلوم** — المستخدم بيلمس طرفَي بُعد مكتوب في الرسمة وبيكتب
 *    قيمته. دي اللي بتنقذ الملفات اللي اتطبعت بمقياس مختلف أو اتعملها
 *    scale وقت التصدير، وده بيحصل أكتر ما حد يتخيّل.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffCalibrateSheet(
    points: List<TakeoffPoint>,
    pageGeometry: PageGeometry,
    onApply: (metresPerPoint: Double, note: String, allPages: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var realLength by remember { mutableStateOf("") }
    var allPages by remember { mutableStateOf(true) }

    // المسافة بين النقطتين **بنقط الصفحة** — الأساس اللي المعايرة بتتحسب منه.
    val pixelDistance = remember(points, pageGeometry) {
        if (points.size < 2) 0.0
        else hypot(
            (points[1].x - points[0].x) * pageGeometry.widthPt,
            (points[1].y - points[0].y) * pageGeometry.heightPt
        )
    }

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
                .padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text("معايرة المقياس", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)

            Text("مقياس معماري", style = CwText.codeSmall, color = c.textTertiary)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                RATIOS.forEach { ratio ->
                    CwChip(
                        label = "١:$ratio",
                        selected = false,
                        onClick = {
                            // مم للنقطة × النسبة ÷ ١٠٠٠ = متر لكل نقطة
                            onApply(MM_PER_POINT * ratio / 1000.0, "١:$ratio", allPages)
                        }
                    )
                }
            }

            Spacer(Modifier.height(Space.xs))
            Text("أو بُعد معلوم", style = CwText.codeSmall, color = c.textTertiary)

            Text(
                when (points.size) {
                    0 -> "المس أول طرف للبُعد على الرسمة."
                    1 -> "المس الطرف التاني."
                    else -> "المسافة على الورق: ${"%.1f".format(pixelDistance)} نقطة"
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )

            if (points.size >= 2) {
                CwField(
                    value = realLength,
                    onValueChange = { realLength = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "الطول الحقيقي بالمتر",
                    placeholder = "6.00",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
            }

            CwChip(
                label = if (allPages) "هيتطبّق على كل الصفحات" else "الصفحة دي بس",
                selected = allPages,
                onClick = { allPages = !allPages }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                val metres = realLength.toDoubleOrNull()
                val valid = points.size >= 2 && pixelDistance > 0.0 &&
                    metres != null && metres > 0.0
                CwButton(
                    "طبّق",
                    {
                        if (valid) onApply(metres!! / pixelDistance, "بُعد معلوم", allPages)
                    },
                    enabled = valid
                )
                CwButton("إلغاء", onDismiss, style = CwButtonStyle.Ghost)
            }
        }
    }
}

/**
 * الإجماليات.
 *
 * مجمّعة بالأداة لأن جمع مساحة على طول مالوش معنى. الخصومات مابتظهرش
 * كبنود — هي أصلاً مطروحة من أبوها، وعرضها كسطر لوحدها بيخلّي المستخدم
 * يحسبها مرتين.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffTotalsSheet(
    items: List<TakeoffItem>,
    pageGeometry: PageGeometry,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val visible = remember(items) { items.filter { it.visible && it.tool != TakeoffTool.DEDUCT } }
    val totals = remember(visible, pageGeometry) {
        TakeoffTool.entries.filter { it != TakeoffTool.DEDUCT }.associateWith { tool ->
            visible.filter { it.tool == tool }
                .sumOf { TakeoffMath.netQuantity(it, items, pageGeometry) }
        }
    }

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
            Text("الإجماليات", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(Space.sm))

            if (!pageGeometry.calibrated) {
                Text(
                    "الصفحة مش معايرة — المساحات والأطوال هتطلع صفر لحد ما تعاير.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.danger.fg
                )
                Spacer(Modifier.height(Space.sm))
            }

            totals.forEach { (tool, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        toolLabel(tool),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatQuantity(tool, value),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )
                }
            }

            Spacer(Modifier.height(Space.md))
            Text("البنود", style = CwText.codeSmall, color = c.textTertiary)

            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(visible, key = { it.id }) { item ->
                    val net = TakeoffMath.netQuantity(item, items, pageGeometry)
                    val holes = TakeoffMath.deductionsOf(item, items).count { it.visible }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textPrimary
                            )
                            if (holes > 0) {
                                Text(
                                    "بعد خصم $holes فتحة",
                                    style = CwText.codeSmall,
                                    color = c.textTertiary
                                )
                            }
                        }
                        Text(
                            formatQuantity(item.tool, net),
                            style = CwText.codeSmall,
                            color = c.textSecondary
                        )
                    }
                }
            }
        }
    }
}

private fun toolLabel(tool: TakeoffTool): String = when (tool) {
    TakeoffTool.AREA -> "المساحات"
    TakeoffTool.LENGTH -> "الأطوال"
    TakeoffTool.COUNT -> "الأعداد"
    TakeoffTool.DEDUCT -> "الخصومات"
}
