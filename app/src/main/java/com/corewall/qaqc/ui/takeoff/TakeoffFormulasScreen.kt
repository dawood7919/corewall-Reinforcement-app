package com.corewall.qaqc.ui.takeoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.TakeoffFormulaEntity
import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffFormula
import com.corewall.qaqc.takeoff.TakeoffFormulaEngine
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.takeoff.takeoffSlug
import com.corewall.qaqc.takeoff.takeoffUniqueToken
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.launch

/**
 * الصيغ كشاشة قائمة بذاتها.
 *
 * كانت شيت فوق الرسمة، وده كان غلط لسببين: كتابة صيغة شغل تركيز مش
 * لمسة سريعة، والشيت كان بيخبّي البنود اللي الصيغة بترجع لها بالظبط.
 *
 * زرار الزائد بيفتح نافذة التحرير، وجوّاها زرار صغير جنب خانة الصيغة
 * بيفتح نافذة تانية فيها كل القياسات — بتختار منها فيتحط **اسم** البند
 * في النص (`slab_area * bar_count`). التخزين بيفضل بالـid، فتغيير اسم
 * البند بعدين مايكسرش الصيغة.
 */
@Composable
fun TakeoffFormulasScreen(
    vm: MainViewModel,
    drawingId: Long,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TakeoffFormulaEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val rows by remember(drawingId) { vm.takeoff.items(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val scaleRows by remember(drawingId) { vm.takeoff.scales(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val formulaRows by remember(drawingId) { vm.takeoff.formulas(drawingId) }
        .collectAsStateWithLifecycle(emptyList())

    val items = remember(rows) { rows.map { vm.takeoff.toModel(it) } }
    // نفس سبب شاشة البيانات: المقاس الحقيقي للصفحة داخل في الكمية.
    val pagesInUse = remember(items) { items.map { it.page }.toSet() }
    val pageGeometryFor = rememberDrawingPageGeometry(vm, drawingId, scaleRows, pagesInUse)

    val evaluated = remember(formulaRows, items, pageGeometryFor) {
        formulaRows.map { row ->
            row to TakeoffFormulaEngine.evaluate(
                TakeoffFormula(
                    id = row.id.toString(), name = row.name, expr = row.expr, unit = row.unit,
                    roundTo = row.roundTo, colorArgb = row.colorArgb,
                    refs = decodeRefs(row.refsJson).mapValues { it.value.toString() }
                ),
                items, pageGeometryFor
            )
        }
    }

    Surface(modifier.fillMaxSize(), color = c.background) {
        Box(Modifier.fillMaxSize()) {
            if (evaluated.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CwEmptyState(
                        icon = Icons.Filled.Functions,
                        title = "مفيش صيغ لسه",
                        detail = "دوس + عشان تكتب أول صيغة تجمع بين القياسات"
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    contentPadding = PaddingValues(top = Space.sm, bottom = Space.xxl)
                ) {
                    items(evaluated, key = { it.first.id }) { (row, result) ->
                        FormulaCard(
                            row = row,
                            resultText = result.error
                                ?: result.value?.let { v ->
                                    "Q = " + "%.${row.roundTo.coerceIn(0, 6)}f".format(v) +
                                        if (row.unit.isNotBlank()) " ${row.unit}" else ""
                                } ?: "Q = —",
                            isError = result.error != null,
                            onEdit = { editing = row },
                            onDelete = { scope.launch { vm.takeoff.deleteFormula(row.id) } }
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { creating = true },
                containerColor = c.accent,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(Space.lg)
            ) { Icon(Icons.Filled.Add, contentDescription = "صيغة جديدة") }
        }
    }

    if (creating || editing != null) {
        FormulaEditorDialog(
            initial = editing,
            items = items,
            pageGeometryFor = pageGeometryFor,
            onSave = { entity ->
                scope.launch { vm.takeoff.saveFormula(entity.copy(drawingId = drawingId)) }
                creating = false; editing = null
            },
            onDismiss = { creating = false; editing = null }
        )
    }
}

@Composable
private fun FormulaCard(
    row: TakeoffFormulaEntity,
    resultText: String,
    isError: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalCwColors.current
    CwCard(contentPadding = PaddingValues(Space.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xxs))
                // الناتج هو اللي الكارت موجود عشانه — سطر لوحده تحت الاسم
                // مباشرة، مش رقم مزنوق في آخر صف الأزرار.
                Text(
                    resultText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isError) c.danger.fg else c.accent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    row.expr.ifBlank { "—" },
                    style = CwText.codeSmall,
                    color = c.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CwIconButton(Icons.Filled.Edit, "تعديل", onEdit)
            CwIconButton(Icons.Filled.Delete, "حذف", onDelete, tint = c.danger.fg)
        }
    }
}

/** نافذة كتابة الصيغة — خانة نص + زرار صغير بيفتح منتقي القياسات. */
@Composable
private fun FormulaEditorDialog(
    initial: TakeoffFormulaEntity?,
    items: List<TakeoffItem>,
    pageGeometryFor: (Int) -> PageGeometry,
    onSave: (TakeoffFormulaEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var expr by remember(initial) { mutableStateOf(initial?.expr ?: "") }
    var unit by remember(initial) { mutableStateOf(initial?.unit ?: "") }
    var refs by remember(initial) { mutableStateOf(decodeRefs(initial?.refsJson ?: "{}")) }
    var picking by remember { mutableStateOf(false) }

    val roundTo = initial?.roundTo ?: 2
    val preview = remember(expr, refs, items, unit) {
        if (expr.isBlank()) TakeoffFormulaEngine.Result(null, null)
        else TakeoffFormulaEngine.evaluate(
            TakeoffFormula(
                id = "preview", name = name, expr = expr, unit = unit, roundTo = roundTo,
                colorArgb = initial?.colorArgb ?: TAKEOFF_PALETTE[0],
                refs = refs.mapValues { it.value.toString() }
            ),
            items, pageGeometryFor
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "صيغة جديدة" else "تعديل الصيغة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                CwField(value = name, onValueChange = { name = it }, label = "اسم الصيغة")

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    CwField(
                        value = expr,
                        onValueChange = { expr = it },
                        label = "الصيغة",
                        placeholder = "slab_area * bar_count",
                        minLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    // الزرار الصغير: بيفتح كل القياسات وبيحط اللي تختاره بالاسم.
                    CwIconButton(Icons.Filled.Functions, "استدعِ قياس", { picking = true })
                }

                CwField(
                    value = unit, onValueChange = { unit = it },
                    label = "الوحدة (اختياري)", placeholder = "m3"
                )

                Text(
                    preview.error ?: preview.value?.let {
                        "= " + "%.${roundTo.coerceIn(0, 6)}f".format(it) +
                            if (unit.isNotBlank()) " $unit" else ""
                    } ?: "اكتب الصيغة أو استدعِ قياس",
                    style = CwText.codeSmall,
                    color = if (preview.error != null) c.danger.fg else c.accent
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && expr.isNotBlank(),
                onClick = {
                    onSave(
                        TakeoffFormulaEntity(
                            id = initial?.id ?: 0,
                            drawingId = initial?.drawingId ?: 0,
                            name = name.trim(),
                            expr = expr.trim(),
                            unit = unit.trim(),
                            roundTo = roundTo,
                            colorArgb = initial?.colorArgb ?: TAKEOFF_PALETTE[0],
                            refsJson = encodeRefs(refs),
                            createdAt = initial?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )

    if (picking) {
        MeasurementPickerDialog(
            items = items,
            pageGeometryFor = pageGeometryFor,
            onPick = { item ->
                val base = takeoffSlug(item.name)
                // نفس البند لو اتستدعى مرتين بياخد نفس التوكن — التوكن
                // بيتفرّد بس لو اسم تاني حجزه قبل كده.
                val existing = refs.entries.firstOrNull { it.value == item.id.toLongOrNull() }?.key
                val token = existing ?: takeoffUniqueToken(base, refs.keys)
                item.id.toLongOrNull()?.let { refs = refs + (token to it) }
                expr = if (expr.isBlank() || expr.trimEnd().endsWith("(")) expr + token
                else "${expr.trimEnd()} $token"
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

/** كل القياسات القابلة للاستدعاء — بالاسم والكمية ووحدتها. */
@Composable
private fun MeasurementPickerDialog(
    items: List<TakeoffItem>,
    pageGeometryFor: (Int) -> PageGeometry,
    onPick: (TakeoffItem) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val referencable = remember(items) {
        items.filter { it.tool != TakeoffTool.DEDUCT && it.tool != TakeoffTool.DIMENSION }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر قياس") },
        text = {
            if (referencable.isEmpty()) {
                Text("مفيش قياسات في الرسمة دي لسه", color = c.textTertiary)
            } else {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(referencable, key = { it.id }) { item ->
                        val qty = netOf(item, items, pageGeometryFor(item.page))
                        Column(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(Space.xxs))
                            CwCard(
                                style = CwCardStyle.Inset,
                                onClick = { onPick(item) },
                                contentPadding = PaddingValues(Space.sm)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        // الاسم زي ما هيتكتب في الصيغة بالظبط،
                                        // مش زي ما هو متخزّن — عشان اللي تشوفه
                                        // هنا هو اللي هيتحط في النص.
                                        Text(
                                            takeoffSlug(item.name),
                                            style = CwText.codeSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = c.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "صفحة ${item.page + 1}",
                                            style = CwText.codeSmall,
                                            color = c.textTertiary
                                        )
                                    }
                                    Text(
                                        latinQuantity(item.tool, qty),
                                        style = CwText.codeSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.accent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تمام") } }
    )
}

/**
 * الكمية بوحدة لاتينية — `12.50 m2`.
 *
 * مش نفس [formatQuantity]: دي بتكتب "م²" بالعربي، والسطر ده بيتقرا جنب
 * توكن لاتيني جوّه سياق صيغة، فخلط الاتجاهين في سطر واحد بيبوّظ ترتيبه.
 */
private fun latinQuantity(tool: TakeoffTool, value: Double): String = when (tool) {
    TakeoffTool.COUNT -> "${value.toInt()} no."
    TakeoffTool.LENGTH, TakeoffTool.DIMENSION -> "%.2f m".format(value)
    TakeoffTool.VOLUME, TakeoffTool.COLUMN -> "%.2f m3".format(value)
    else -> "%.2f m2".format(value)
}

private val refsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

private fun decodeRefs(raw: String): Map<String, Long> = runCatching {
    refsJson.decodeFromString<Map<String, Long>>(raw)
}.getOrDefault(emptyMap())

private fun encodeRefs(refs: Map<String, Long>): String = refsJson.encodeToString(refs)
