package com.corewall.qaqc.ui.takeoff

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.data.db.TakeoffCategoryEntity
import com.corewall.qaqc.data.db.TakeoffFormulaEntity
import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffFormula
import com.corewall.qaqc.takeoff.TakeoffFormulaEngine
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.takeoffUniqueToken
import com.corewall.qaqc.takeoff.takeoffVarPath
import com.corewall.qaqc.ui.design.CwButton
import com.corewall.qaqc.ui.design.CwButtonStyle
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val OPERATOR_TOKENS = listOf("+", "-", "*", "/", "(", ")")
private val FUNCTION_TOKENS = listOf("ROUND(", "ABS(", "MIN(", "MAX(", "SQRT(", "CEIL(", "FLOOR(")

/**
 * قايمة الصيغ — بتفتح على القايمة، وتاني ضغطة بتفتح المحرّر لصيغة
 * موجودة أو جديدة. الاتنين نفس الـsheet، مش شاشتين منفصلتين، عشان
 * التنقّل بينهم يبقى فوري من غير أنيميشن دخول وخروج.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffFormulasSheet(
    drawingId: Long,
    items: List<TakeoffItem>,
    categories: List<TakeoffCategoryEntity>,
    formulaRows: List<TakeoffFormulaEntity>,
    pageGeometryFor: (Int) -> PageGeometry,
    onSave: (TakeoffFormulaEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<TakeoffFormulaEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val formulas = remember(formulaRows) {
        formulaRows.map { row ->
            row to run {
                val model = TakeoffFormula(
                    id = row.id.toString(), name = row.name, expr = row.expr, unit = row.unit,
                    roundTo = row.roundTo, colorArgb = row.colorArgb,
                    refs = decodeRefsForPreview(row.refsJson).mapValues { it.value.toString() }
                )
                TakeoffFormulaEngine.evaluate(model, items, pageGeometryFor)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        val active = editing
        if (active != null || creating) {
            TakeoffFormulaEditor(
                drawingId = drawingId,
                initial = active,
                items = items,
                categories = categories,
                pageGeometryFor = pageGeometryFor,
                onSave = { entity -> onSave(entity); editing = null; creating = false },
                onDelete = { id -> onDelete(id); editing = null; creating = false },
                onCancel = { editing = null; creating = false }
            )
        } else {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = Space.lg)
                    .padding(bottom = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "الصيغ", style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary, modifier = Modifier.weight(1f)
                    )
                    CwIconButton(Icons.Filled.Add, "صيغة جديدة", { creating = true })
                }
                if (formulas.isEmpty()) {
                    Text(
                        "الصيغة بترجع بأرقام بنود تانية لايف — زي «مساحة الأرضية × السمك». " +
                            "اضغط + وابدأ.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary
                    )
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(formulas, key = { it.first.id }) { (row, result) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { editing = row }
                                .padding(vertical = Space.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(Color((row.colorArgb or 0xFF000000L).toInt()), CircleShape)
                            )
                            Spacer(Modifier.width(Space.sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.name, style = MaterialTheme.typography.bodyMedium,
                                    color = c.textPrimary
                                )
                                Text(
                                    row.expr, style = CwText.codeSmall, color = c.textTertiary,
                                    maxLines = 1
                                )
                            }
                            if (result.error != null) {
                                Text("#ERR", style = CwText.codeSmall, color = c.danger.fg)
                            } else {
                                Text(
                                    "%.${row.roundTo.coerceIn(0, 6)}f".format(result.value ?: 0.0) +
                                        if (row.unit.isNotBlank()) " ${row.unit}" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, color = c.textPrimary
                                )
                            }
                        }
                    }
                }
                CwButton("قفل", onDismiss, style = CwButtonStyle.Ghost)
            }
        }
    }
}

@Composable
private fun TakeoffFormulaEditor(
    drawingId: Long,
    initial: TakeoffFormulaEntity?,
    items: List<TakeoffItem>,
    categories: List<TakeoffCategoryEntity>,
    pageGeometryFor: (Int) -> PageGeometry,
    onSave: (TakeoffFormulaEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val c = LocalCwColors.current
    var name by remember(initial) { mutableStateOf(initial?.name ?: "صيغة ${System.currentTimeMillis() % 1000}") }
    var expr by remember(initial) { mutableStateOf(initial?.expr ?: "") }
    var unit by remember(initial) { mutableStateOf(initial?.unit ?: "") }
    var roundToText by remember(initial) { mutableStateOf((initial?.roundTo ?: 2).toString()) }
    var refs by remember(initial) {
        mutableStateOf(decodeRefsForPreview(initial?.refsJson ?: "{}"))
    }

    // كل بند فيه اسم قابل للاستخدام كمرجع — من كل صفحات الرسمة دي، مش
    // الصفحة الحالية بس. الخصومات مالهاش داعي تتعرض كمرجع مستقل.
    val referencable = remember(items, categories) {
        items.filter { it.tool.isQuantity }.map { item ->
            val catName = categories.firstOrNull { it.id.toString() == item.categoryId }?.name
            item to takeoffVarPath(catName, null, item.name)
        }
    }

    fun insert(text: String) {
        expr = if (expr.isEmpty() || expr.endsWith(" ") || expr.endsWith("(")) expr + text else "$expr $text"
    }

    val roundTo = roundToText.toIntOrNull() ?: 2
    val previewFormula = remember(expr, refs, roundTo) {
        TakeoffFormula(
            id = "preview", name = name, expr = expr, unit = unit, roundTo = roundTo,
            colorArgb = initial?.colorArgb ?: TAKEOFF_PALETTE[0],
            refs = refs.mapValues { it.value.toString() }
        )
    }
    val preview = remember(previewFormula, items) {
        if (expr.isBlank()) TakeoffFormulaEngine.Result(null, null)
        else TakeoffFormulaEngine.evaluate(previewFormula, items, pageGeometryFor)
    }

    Column(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = Space.lg)
            .padding(bottom = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        Text(
            if (initial == null) "صيغة جديدة" else "تعديل الصيغة",
            style = MaterialTheme.typography.titleMedium, color = c.textPrimary
        )

        CwField(value = name, onValueChange = { name = it }, label = "الاسم")

        Text("مراجع البنود — دوس تضيفها آخر الصيغة", style = CwText.codeSmall, color = c.textTertiary)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            if (referencable.isEmpty()) {
                Text("مفيش بنود لسه في الرسمة دي", style = CwText.codeSmall, color = c.textTertiary)
            }
            referencable.forEach { (item, path) ->
                CwChipToken(path) {
                    val token = takeoffUniqueToken(path, refs.keys)
                    refs = refs + (token to (item.id.toLongOrNull() ?: return@CwChipToken))
                    insert(token)
                }
            }
        }

        Text("عوامل ودوال", style = CwText.codeSmall, color = c.textTertiary)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            (OPERATOR_TOKENS + FUNCTION_TOKENS).forEach { token ->
                CwChipToken(token) { insert(token) }
            }
        }

        CwField(
            value = expr, onValueChange = { expr = it },
            label = "الصيغة", placeholder = "concrete_slab1 * 0.2",
            minLines = 2
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            CwField(
                value = unit, onValueChange = { unit = it },
                label = "الوحدة (اختياري)", placeholder = "م³",
                modifier = Modifier.weight(1f)
            )
            CwField(
                value = roundToText,
                onValueChange = { roundToText = it.filter { ch -> ch.isDigit() } },
                label = "خانات عشرية",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        // معاينة لايف — بتتحسب من نفس المحرّك اللي هيحسب بيه البند بعد
        // الحفظ، مش تقدير منفصل ممكن يختلف عنه.
        val previewColor = if (preview.error != null) c.danger.fg else c.accent
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.surfaceAlt, Radius.shapeSm)
                .padding(Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("النتيجة", style = CwText.codeSmall, color = c.textTertiary, modifier = Modifier.weight(1f))
            Text(
                preview.error ?: preview.value?.let { "%.${roundTo.coerceIn(0, 6)}f".format(it) + if (unit.isNotBlank()) " $unit" else "" } ?: "—",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = previewColor
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            CwButton(
                "حفظ",
                {
                    onSave(
                        TakeoffFormulaEntity(
                            id = initial?.id ?: 0,
                            drawingId = drawingId,
                            name = name.trim().ifBlank { "صيغة" },
                            expr = expr,
                            unit = unit.trim(),
                            roundTo = roundTo,
                            colorArgb = initial?.colorArgb ?: TAKEOFF_PALETTE[0],
                            refsJson = encodeRefsForSave(refs),
                            createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = expr.isNotBlank()
            )
            if (initial != null) {
                CwIconButton(Icons.Filled.Delete, "احذف الصيغة", { onDelete(initial.id) }, tint = c.danger.fg)
            }
            CwButton("إلغاء", onCancel, style = CwButtonStyle.Ghost)
        }
    }
}

@Composable
private fun CwChipToken(label: String, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Box(
        Modifier
            .background(c.surfaceAlt, Radius.pill)
            .border(Dp.Hairline, c.outline, Radius.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.xs)
    ) {
        Text(label, style = CwText.codeSmall, color = c.textPrimary)
    }
}

private val refsJson = Json { ignoreUnknownKeys = true }

private fun decodeRefsForPreview(raw: String): Map<String, Long> = runCatching {
    refsJson.decodeFromString<Map<String, Long>>(raw)
}.getOrDefault(emptyMap())

private fun encodeRefsForSave(refs: Map<String, Long>): String = refsJson.encodeToString(refs)
