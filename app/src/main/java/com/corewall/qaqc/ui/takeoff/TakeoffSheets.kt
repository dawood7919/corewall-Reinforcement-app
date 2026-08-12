package com.corewall.qaqc.ui.takeoff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.data.AppSettings
import com.corewall.qaqc.data.db.TakeoffCategoryEntity
import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffCategory
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffMath
import com.corewall.qaqc.takeoff.TakeoffPoint
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.ui.design.CwBanner
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwChip
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwSegmented
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.CwTone
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as CwStroke
import kotlin.math.hypot
import kotlin.math.roundToInt

/** مم على الورق لكل نقطة PDF: بوصة ÷ ٧٢. */
private const val MM_PER_POINT = 25.4 / 72.0

/** المقاييس المعمارية الشائعة. */
private val RATIOS = listOf(20, 25, 50, 100, 200, 500, 1000)

/**
 * معايرة المقياس.
 *
 * طريقتين، والاتنين مقصودين:
 *
 * ١. **بُعد معلوم** — المستخدم بيلمس طرفَي بُعد مكتوب في الرسمة وبيكتب
 *    قيمته. دي اللي بتنقذ الملفات اللي اتطبعت بمقياس مختلف أو اتعملها
 *    scale وقت التصدير، وده بيحصل أكتر ما حد يتخيّل. **الافتراضية** —
 *    هي اللي بتشتغل مع أي ملف أيًا كان مصدره.
 * ٢. **مقياس معماري** (١:٥٠، ١:١٠٠…) — لمسة واحدة، ودقيق تماماً **لو**
 *    الملف اتصدّر بمقاسه الحقيقي. الحساب من نقطة الـPDF مباشرة
 *    (٢٥.٤÷٧٢ مم) — مافيش دقة رسترة في المعادلة، فمفيش ثابت يقدر يخرب
 *    المعايرة لو اتغيّر بعدين.
 *
 * **لوحة عادية مش شيت مودال بقصد**: [ModalBottomSheet] بيحط ستارة (scrim)
 * فوق الشاشة كلها بتاخد أي لمسة برّه الشيت وتقفله — يعني المستخدم مايقدرش
 * يلمس الرسمة نفسها عشان يحطّ نقطتي البُعد، والمعايرة بتبقى مستحيلة عمليًا.
 * اللوحة دي بتترسم في مكانها العادي جوّه [Box] الشاشة، فمفيش ستارة، والرسمة
 * فوقها فاضية للمس زي أي وضع رسم تاني.
 */
@Composable
fun TakeoffCalibratePanel(
    points: List<TakeoffPoint>,
    pageGeometry: PageGeometry,
    onApply: (metresPerPoint: Double, note: String, allPages: Boolean) -> Unit,
    onClearPoints: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    var tabIndex by remember { mutableIntStateOf(0) }
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
    val metres = realLength.toDoubleOrNull()
    val valid = points.size >= 2 && pixelDistance > 0.0 && metres != null && metres > 0.0
    val metresPerPoint = if (valid) metres!! / pixelDistance else null
    val computedRatio = metresPerPoint?.let { (it * 1000.0 / MM_PER_POINT).roundToInt() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = c.surface,
        shape = Radius.sheet,
        shadowElevation = Elevation.overlay,
        border = BorderStroke(CwStroke.hair, c.outline)
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
                .padding(top = Space.md, bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "معايرة المقياس",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                CwIconButton(Icons.Filled.Close, "إلغاء المعايرة", onCancel)
            }

            CwSegmented(
                options = listOf("بُعد معلوم", "مقياس معماري"),
                selectedIndex = tabIndex,
                label = { it },
                onSelect = { tabIndex = it }
            )

            if (tabIndex == 0) {
                CalibrationPointsRow(pointCount = points.size)
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

                if (computedRatio != null) {
                    CwBanner(
                        title = "المقياس المحسوب ١:$computedRatio",
                        tone = CwTone.Success,
                        detail = "١ م = %.2f نقطة على الورق".format(1.0 / metresPerPoint!!)
                    )
                }

                CwChip(
                    label = if (allPages) "هيتطبّق على كل الصفحات" else "الصفحة دي بس",
                    selected = allPages,
                    onClick = { allPages = !allPages }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CwButton(
                        "تأكيد",
                        {
                            if (valid) onApply(metresPerPoint!!, "بُعد معلوم", allPages)
                        },
                        enabled = valid
                    )
                    CwButton("مسح النقط", onClearPoints, style = CwButtonStyle.Secondary)
                }
            } else {
                Text(
                    "اختر المقياس المطبوع على الرسمة — بيتطبّق فورًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
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
                CwChip(
                    label = if (allPages) "هيتطبّق على كل الصفحات" else "الصفحة دي بس",
                    selected = allPages,
                    onClick = { allPages = !allPages }
                )
            }
        }
    }
}

/** ① ــــ ② — تقدّم لمس نقطتي البُعد، زي مؤشّر معايرة النسخة المرجعية. */
@Composable
private fun CalibrationPointsRow(pointCount: Int, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    Row(
        modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalibrationBadge(index = 1, filled = pointCount >= 1)
        Box(
            Modifier
                .weight(1f)
                .height(CwStroke.thick)
                .background(if (pointCount >= 2) c.accent else c.outline)
        )
        CalibrationBadge(index = 2, filled = pointCount >= 2)
    }
}

@Composable
private fun CalibrationBadge(index: Int, filled: Boolean) {
    val c = LocalCwColors.current
    Box(
        Modifier
            .size(32.dp)
            .background(if (filled) c.accent else c.surfaceAlt, CircleShape)
            .border(CwStroke.hair, if (filled) c.accent else c.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$index",
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) c.onAccent else c.textTertiary
        )
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
    categories: List<TakeoffCategory> = emptyList(),
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val visible = remember(items) { items.filter { it.visible && it.tool.isQuantity } }
    val totals = remember(visible, pageGeometry) {
        TakeoffTool.entries.filter { it.isQuantity }.associateWith { tool ->
            visible.filter { it.tool == tool }
                .sumOf { TakeoffMath.netQuantity(it, items, pageGeometry) }
        }
    }
    val totalCost = remember(visible, pageGeometry, categories) {
        visible.sumOf { TakeoffMath.costOf(it, items, pageGeometry, categories) }
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

            if (totalCost > 0.0) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "التكلفة التقديرية", style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "%.0f".format(totalCost), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = c.accent
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
    TakeoffTool.VOLUME -> "الأحجام"
    TakeoffTool.COLUMN -> "الأعمدة"
    TakeoffTool.DIMENSION -> "الأبعاد"
}

/**
 * اسم وتصنيف ولون **قبل** ما البند يتحفظ.
 *
 * ده الفرق بين بند اسمه "مساحة ٣" ولون اتلفّ عليه من باليت، وبند
 * المستخدم فعلاً سمّاه وحطّه في فئته. بيظهر بعد ما الشكل يخلص رسمه —
 * مش قبل ما يبدأ زي النسخة الأصلية — عشان في الموبايل نافذة بتقفل نص
 * الشاشة قبل ما تشوف حتى شكل إيه بترسم أحسن تتأجّل لحد ما تشوفه.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffNameSheet(
    tool: TakeoffTool,
    suggestedName: String,
    categories: List<TakeoffCategoryEntity>,
    onCreateCategory: (name: String, colorArgb: Long, onCreated: (Long) -> Unit) -> Unit,
    onConfirm: (
        name: String, categoryId: Long?, colorArgb: Long,
        thickness: Double?, colLength: Double?, colWidth: Double?, colHeight: Double?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(suggestedName) }
    var categoryId by remember { mutableStateOf<Long?>(categories.firstOrNull()?.id) }
    var colorArgb by remember(categoryId) {
        mutableStateOf(
            categories.firstOrNull { it.id == categoryId }?.colorArgb
                ?: TAKEOFF_PALETTE[0]
        )
    }
    var creatingCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    val decimalOptions = androidx.compose.foundation.text.KeyboardOptions(
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
    )
    var thicknessText by remember { mutableStateOf("") }
    var colLengthText by remember { mutableStateOf("") }
    var colWidthText by remember { mutableStateOf("") }
    var colHeightText by remember { mutableStateOf("") }

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
            Text(toolTitle(tool), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)

            CwField(value = name, onValueChange = { name = it }, label = "الاسم")

            Text("الفئة", style = CwText.codeSmall, color = c.textTertiary)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                categories.forEach { cat ->
                    CwChip(
                        label = cat.name,
                        selected = cat.id == categoryId,
                        onClick = { categoryId = cat.id; colorArgb = cat.colorArgb }
                    )
                }
                CwChip(
                    label = "+ فئة جديدة",
                    selected = creatingCategory,
                    onClick = { creatingCategory = !creatingCategory }
                )
            }

            if (creatingCategory) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    CwField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = "اسم الفئة",
                        modifier = Modifier.weight(1f)
                    )
                    CwButton(
                        "إنشاء",
                        {
                            val cName = newCategoryName
                            if (cName.isNotBlank()) {
                                val newColor = TAKEOFF_PALETTE[categories.size % TAKEOFF_PALETTE.size]
                                onCreateCategory(cName, newColor) { newId ->
                                    categoryId = newId
                                    colorArgb = newColor
                                }
                                newCategoryName = ""
                                creatingCategory = false
                            }
                        }
                    )
                }
            }

            Text("اللون", style = CwText.codeSmall, color = c.textTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TAKEOFF_PALETTE.forEach { argb ->
                    val fullArgb = argb or 0xFF000000L
                    val chosen = fullArgb == (colorArgb or 0xFF000000L)
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color(fullArgb.toInt()), CircleShape)
                            .border(
                                if (chosen) 2.dp else Dp.Hairline,
                                if (chosen) c.accent else c.outline,
                                CircleShape
                            )
                            .clickable { colorArgb = fullArgb }
                    )
                }
            }

            // السمك والأبعاد — بس للأداتين اللي فعلاً محتاجاهم. الحجم
            // مش زي المعايرة (مقاس على الورق)؛ ده رقم حقيقي المستخدم
            // بيكتبه بإيده لأنه مالوش وجود على الرسمة نفسها.
            if (tool == TakeoffTool.VOLUME) {
                CwField(
                    value = thicknessText,
                    onValueChange = { thicknessText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "السمك بالمتر",
                    placeholder = "0.20",
                    keyboardOptions = decimalOptions
                )
            }
            if (tool == TakeoffTool.COLUMN) {
                Text("أبعاد العمود بالمتر", style = CwText.codeSmall, color = c.textTertiary)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    CwField(
                        value = colLengthText,
                        onValueChange = { colLengthText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = "الطول", placeholder = "0.40",
                        keyboardOptions = decimalOptions,
                        modifier = Modifier.weight(1f)
                    )
                    CwField(
                        value = colWidthText,
                        onValueChange = { colWidthText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = "العرض", placeholder = "0.40",
                        keyboardOptions = decimalOptions,
                        modifier = Modifier.weight(1f)
                    )
                }
                CwField(
                    value = colHeightText,
                    onValueChange = { colHeightText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "الارتفاع", placeholder = "3.00",
                    keyboardOptions = decimalOptions
                )
            }

            val thickness = thicknessText.toDoubleOrNull()
            val colLength = colLengthText.toDoubleOrNull()
            val colWidth = colWidthText.toDoubleOrNull()
            val colHeight = colHeightText.toDoubleOrNull()
            val dimsValid = when (tool) {
                TakeoffTool.VOLUME -> thickness != null && thickness > 0.0
                TakeoffTool.COLUMN ->
                    colLength != null && colWidth != null && colHeight != null &&
                        colLength > 0.0 && colWidth > 0.0 && colHeight > 0.0
                else -> true
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CwButton(
                    "حفظ",
                    {
                        onConfirm(
                            name.trim().ifBlank { suggestedName }, categoryId, colorArgb,
                            thickness, colLength, colWidth, colHeight
                        )
                    },
                    enabled = dimsValid
                )
                CwButton("إلغاء", onDismiss, style = CwButtonStyle.Ghost)
            }
        }
    }
}

/**
 * تعديل بند موجود — اسمه وفئته ولونه ومكانه ونسبة تنفيذه وسعره الخاص.
 *
 * مقصود إنها منفصلة عن [TakeoffNameSheet]: دي بتفتح على بند **موجود
 * بالفعل** وممكن تتفتح أي وقت، مش لحظة إنشاء بس — زي "نسّق البلاط ده
 * كان ٩٠٪ وخلص، وحطّه في منطقة تانية".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffEditItemSheet(
    item: TakeoffItem,
    categories: List<TakeoffCategoryEntity>,
    onCreateCategory: (name: String, colorArgb: Long, onCreated: (Long) -> Unit) -> Unit,
    onSave: (
        name: String, categoryId: Long?, colorArgb: Long,
        zone: String, progressPercent: Double?, rateOverride: Double?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(item.id) { mutableStateOf(item.name) }
    var categoryId by remember(item.id) { mutableStateOf(item.categoryId?.toLongOrNull()) }
    var colorArgb by remember(item.id) { mutableStateOf(item.colorArgb) }
    var zone by remember(item.id) { mutableStateOf(item.zone) }
    var progressText by remember(item.id) {
        mutableStateOf(item.progressPercent?.let { "%.0f".format(it) } ?: "")
    }
    var rateText by remember(item.id) {
        mutableStateOf(item.rateOverride?.let { "%.2f".format(it) } ?: "")
    }
    var creatingCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    val decimalOptions = androidx.compose.foundation.text.KeyboardOptions(
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
    )

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
            Text("تعديل البند", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)

            CwField(value = name, onValueChange = { name = it }, label = "الاسم")

            Text("الفئة", style = CwText.codeSmall, color = c.textTertiary)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                categories.forEach { cat ->
                    CwChip(
                        label = cat.name,
                        selected = cat.id == categoryId,
                        onClick = { categoryId = cat.id; colorArgb = cat.colorArgb }
                    )
                }
                CwChip(
                    label = "+ فئة جديدة",
                    selected = creatingCategory,
                    onClick = { creatingCategory = !creatingCategory }
                )
            }
            if (creatingCategory) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    CwField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = "اسم الفئة",
                        modifier = Modifier.weight(1f)
                    )
                    CwButton(
                        "إنشاء",
                        {
                            val cName = newCategoryName
                            if (cName.isNotBlank()) {
                                val newColor = TAKEOFF_PALETTE[categories.size % TAKEOFF_PALETTE.size]
                                onCreateCategory(cName, newColor) { newId ->
                                    categoryId = newId
                                    colorArgb = newColor
                                }
                                newCategoryName = ""
                                creatingCategory = false
                            }
                        }
                    )
                }
            }

            Text("اللون", style = CwText.codeSmall, color = c.textTertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TAKEOFF_PALETTE.forEach { argb ->
                    val fullArgb = argb or 0xFF000000L
                    val chosen = fullArgb == (colorArgb or 0xFF000000L)
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color(fullArgb.toInt()), CircleShape)
                            .border(
                                if (chosen) 2.dp else Dp.Hairline,
                                if (chosen) c.accent else c.outline,
                                CircleShape
                            )
                            .clickable { colorArgb = fullArgb }
                    )
                }
            }

            CwField(
                value = zone, onValueChange = { zone = it },
                label = "المكان", placeholder = "الدور الأرضي — بلوك A"
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CwField(
                    value = progressText,
                    onValueChange = { progressText = it.filter { ch -> ch.isDigit() } },
                    label = "نسبة التنفيذ ٪",
                    placeholder = "٠-١٠٠",
                    keyboardOptions = decimalOptions,
                    modifier = Modifier.weight(1f)
                )
                CwField(
                    value = rateText,
                    onValueChange = { rateText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = "سعر خاص (اختياري)",
                    placeholder = "افتراضي الفئة",
                    keyboardOptions = decimalOptions,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CwButton(
                    "حفظ",
                    {
                        val progress = progressText.toDoubleOrNull()?.coerceIn(0.0, 100.0)
                        val rate = rateText.toDoubleOrNull()
                        onSave(
                            name.trim().ifBlank { item.name }, categoryId, colorArgb,
                            zone.trim(), progress, rate
                        )
                    }
                )
                CwButton("إلغاء", onDismiss, style = CwButtonStyle.Ghost)
            }
        }
    }
}

/** نص ملاحظة على الرسمة — تعليق مش بند حصر، بيتفتح بلمسة واحدة. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffTextAnnotationSheet(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }

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
            Text("ملاحظة على الرسمة", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            CwField(value = text, onValueChange = { text = it }, label = "النص", minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                CwButton("حفظ", { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank())
                CwButton("إلغاء", onDismiss, style = CwButtonStyle.Ghost)
            }
        }
    }
}

private fun toolTitle(tool: TakeoffTool): String = when (tool) {
    TakeoffTool.AREA -> "مساحة جديدة"
    TakeoffTool.LENGTH -> "طول جديد"
    TakeoffTool.COUNT -> "عدّ جديد"
    TakeoffTool.DEDUCT -> "خصم جديد"
    TakeoffTool.VOLUME -> "حجم جديد"
    TakeoffTool.COLUMN -> "عمود جديد"
    TakeoffTool.DIMENSION -> "بُعد جديد"
}

/**
 * إعدادات مظهر الرسم — سُمك الخطوط، حجم علامة العدّ/العمود، حجم النص،
 * ونصف قطر الالتقاط. كلها متخزّنة في [AppSettings] العامة (نفس ملف
 * إعدادات التطبيق)، فمش هتتصفّر لو المستخدم قفل وفتح الرسمة.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffSettingsSheet(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "إعدادات الرسم",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                CwIconButton(Icons.Filled.Close, "إغلاق", onDismiss)
            }

            SettingsSliderRow(
                label = "سُمك خطوط الأشكال",
                valueLabel = "%.1f".format(settings.takeoffStrokeWidth),
                value = settings.takeoffStrokeWidth,
                range = 2f..7f,
                onChange = { v -> onUpdate { it.copy(takeoffStrokeWidth = v) } }
            )
            SettingsSliderRow(
                label = "حجم علامة العدّ والعمود",
                valueLabel = "%.1f".format(settings.takeoffMarkerRadius),
                value = settings.takeoffMarkerRadius,
                range = 6f..18f,
                onChange = { v -> onUpdate { it.copy(takeoffMarkerRadius = v) } }
            )
            SettingsSliderRow(
                label = "حجم الاسم والكمية فوق الشكل",
                valueLabel = "${(settings.takeoffTextScale * 100).roundToInt()}٪",
                value = settings.takeoffTextScale,
                range = 0.7f..1.8f,
                onChange = { v -> onUpdate { it.copy(takeoffTextScale = v) } }
            )
            SettingsSliderRow(
                label = "نصف قطر الالتقاط للرأس القريب",
                valueLabel = "%.0f نقطة".format(settings.takeoffSnapRadiusPt),
                value = settings.takeoffSnapRadiusPt,
                range = 6f..30f,
                onChange = { v -> onUpdate { it.copy(takeoffSnapRadiusPt = v) } }
            )

            Spacer(Modifier.height(Space.xs))
            CwButton(
                "رجّع الافتراضي",
                {
                    onUpdate {
                        it.copy(
                            takeoffStrokeWidth = 3.6f,
                            takeoffMarkerRadius = 8.5f,
                            takeoffTextScale = 1f,
                            takeoffSnapRadiusPt = 14f
                        )
                    }
                },
                style = CwButtonStyle.Secondary
            )
        }
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    val c = LocalCwColors.current
    Column(Modifier.padding(vertical = Space.xs)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
