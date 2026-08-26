package com.corewall.qaqc.ui.takeoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.corewall.qaqc.ui.design.Radius
import androidx.compose.ui.graphics.Color
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
import com.corewall.qaqc.ui.design.Stroke
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

    val formulaModels = remember(formulaRows) {
        formulaRows.map { row ->
            TakeoffFormula(
                id = row.id.toString(), name = row.name, expr = row.expr, unit = row.unit,
                roundTo = row.roundTo, colorArgb = row.colorArgb,
                refs = decodeRefs(row.refsJson).mapValues { it.value.toString() }
            )
        }
    }
    val evaluated = remember(formulaRows, items, pageGeometryFor, formulaModels) {
        formulaRows.map { row ->
            row to TakeoffFormulaEngine.evaluate(
                TakeoffFormula(
                    id = row.id.toString(), name = row.name, expr = row.expr, unit = row.unit,
                    roundTo = row.roundTo, colorArgb = row.colorArgb,
                    refs = decodeRefs(row.refsJson).mapValues { it.value.toString() }
                ),
                items, pageGeometryFor, formulaModels
            )
        }
    }

    /**
     * مجموع كل صيغة مع اللي بنفس الوحدة.
     *
     * بالوحدة مش إجمالي واحد: جمع m2 على m3 رقم مالوش معنى هندسي. الصيغ
     * اللي من غير وحدة مابتدخلش — مش معروف بتجمع إيه.
     */
    val unitTotals = remember(evaluated) {
        evaluated.mapNotNull { (row, result) ->
            val value = result.value ?: return@mapNotNull null
            val unit = row.unit.trim()
            if (unit.isBlank()) null else unit to value
        }.groupBy({ it.first }, { it.second })
            .map { (unit, values) -> unit to values.sum() }
            .sortedBy { it.first }
    }

    // الشاشة كلها LTR عن قصد: الصيغة تعبير رياضي بيتقرا من الشمال لليمين،
    // ومحاذاتها يمين كانت بتقلب ترتيب الأقواس والعوامل بصريًا.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Surface(modifier.fillMaxSize(), color = c.background) {
        Box(Modifier.fillMaxSize()) {
            if (evaluated.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CwEmptyState(
                        icon = Icons.Filled.Functions,
                        title = "No formulas yet",
                        detail = "Tap + to combine measurements into a formula"
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
                    if (unitTotals.isNotEmpty()) {
                        item(key = "unit-totals") { UnitTotalsCard(unitTotals) }
                    }
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
                            onDuplicate = {
                                scope.launch {
                                    vm.takeoff.saveFormula(
                                        row.copy(
                                            id = 0,
                                            name = uniqueFormulaName(
                                                row.name, formulaRows.map { it.name }
                                            ),
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            },
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
            ) { Icon(Icons.Filled.Add, contentDescription = "New formula") }
        }
    }
    }

    if (creating || editing != null) {
        FormulaEditorDialog(
            initial = editing,
            items = items,
            formulas = formulaModels,
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
    onDuplicate: () -> Unit,
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
            CwIconButton(Icons.Filled.Edit, "Edit", onEdit)
            // النسخ موجود عشان أغلب الصيغ بتتولد من صيغة قبلها بفرق بند
            // أو معامل — إعادة كتابتها من الأول شغل مكرر.
            CwIconButton(Icons.Filled.ContentCopy, "Duplicate", onDuplicate)
            CwIconButton(Icons.Filled.Delete, "Delete", onDelete, tint = c.danger.fg)
        }
    }
}

/**
 * نافذة كتابة الصيغة.
 *
 * الترتيب مقصود: الاسم، التعبير، إزاي تدخّل فيه حاجة، الناتج — وبعدين
 * الوحدة والتقريب. الوحدة والتقريب شكل الرقم، مش الرقم نفسه، فمكانهم
 * بعد ما تشوف إن الحساب طلع صح.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormulaEditorDialog(
    initial: TakeoffFormulaEntity?,
    items: List<TakeoffItem>,
    formulas: List<TakeoffFormula>,
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
    var pickingFormula by remember { mutableStateOf(false) }
    var pickingFunction by remember { mutableStateOf(false) }
    var roundTo by remember(initial) { mutableIntStateOf(initial?.roundTo ?: 2) }
    val preview = remember(expr, refs, items, unit, roundTo) {
        if (expr.isBlank()) TakeoffFormulaEngine.Result(null, null)
        else TakeoffFormulaEngine.evaluate(
            TakeoffFormula(
                id = "preview", name = name, expr = expr, unit = unit, roundTo = roundTo,
                colorArgb = initial?.colorArgb ?: TAKEOFF_PALETTE[0],
                refs = refs.mapValues { it.value.toString() }
            ),
            items, pageGeometryFor, formulas
        )
    }

    // كل أسماء المراجع المعروفة — بتتلوّن في النص عشان تفرّق المرجع عن
    // أي كلمة مكتوبة غلط من نظرة واحدة.
    val knownRefs = remember(items, formulas, initial) {
        (items.filter { it.tool != TakeoffTool.DEDUCT && it.tool != TakeoffTool.DIMENSION }
            .map { takeoffSlug(it.name) } +
            formulas.filter { it.id != initial?.id?.toString() }.map { takeoffSlug(it.name) })
            .filter { it.isNotBlank() }.toSet()
    }
    val highlight = remember(knownRefs, c.accent, c.warning.fg, c.textPrimary) {
        ExpressionHighlighter(knownRefs, c.accent, c.warning.fg, c.textPrimary)
    }

    fun insert(token: String) {
        expr = if (expr.isBlank() || expr.endsWith(" ") || expr.endsWith("(")) expr + token
        else "${expr.trimEnd()} $token"
    }

    /**
     * بيمسح آخر **رمز** مش آخر حرف.
     *
     * الأسماء هنا طويلة (`concrete_slab_area`)، ومسح حرف بحرف يعني عشرين
     * ضغطة. الحرف لوحده بيتمسح بس لما يكون عامل أو قوس.
     */
    fun backspace() {
        val trimmed = expr.trimEnd()
        if (trimmed.isEmpty()) { expr = ""; return }
        val last = trimmed.last()
        expr = if (last.isLetterOrDigit() || last == '_' || last == '.') {
            trimmed.dropLastWhile { it.isLetterOrDigit() || it == '_' || it == '.' }.trimEnd()
        } else {
            trimmed.dropLast(1).trimEnd()
        }
    }

    // النافذة بتتعرض في نافذة نظام منفصلة، فمزوّد الاتجاه اللي على الشاشة
    // مابيوصلهاش. من غير ده التعبير بيتقلب وانت بتكتب: `c_shape*10` بتبان
    // `10c_shape*`.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New formula" else "Edit formula") },
        text = {
            // المحتوى بقى أطول من شاشة موبايل، والـAlertDialog مابيمرّرش
            // محتواه لوحده — من غير ده الوحدة والتقريب وزراير الحفظ
            // بيتقصّوا من تحت زي ما الصف كان بيتقص من الجنب.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                CwField(value = name, onValueChange = { name = it }, label = "Formula name")

                CwField(
                    value = expr,
                    onValueChange = { expr = it },
                    label = "Expression",
                    placeholder = "slab_area * bar_count",
                    minLines = 2,
                    visualTransformation = highlight
                )

                // الاستدعاء أهم من العمليات، فهو أول حاجة تحت الخانة وفي
                // صف تلاتة بالعرض — مش مزنوق في آخر شريط بيتمرّر. الشريط
                // اللي كان بيتمرّر كان بيخبّي ∑ وfx بالظبط، وهما السبب
                // اللي النافذة موجودة عشانه.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    InsertButton(
                        "∑", "Measurement", Modifier.weight(1f)
                    ) { picking = true }
                    InsertButton(
                        "fx", "Formula", Modifier.weight(1f)
                    ) { pickingFormula = true }
                    InsertButton(
                        "ƒ()", "Function", Modifier.weight(1f)
                    ) { pickingFunction = true }
                }

                // العمليات بتلفّ لسطر جديد بدل ما تتقص. الصف اللي بيتمرّر
                // أفقيًا مافيهوش أي علامة إن فيه حاجة برّه الشاشة، فاللي
                // مش ظاهر بيبقى غير موجود من ناحية المستخدم.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    listOf("+", "-", "*", "/", "(", ")").forEach { op ->
                        KeyChip(op) { insert(op) }
                    }
                    KeyChip("⌫") { backspace() }
                }

                // الناتج جنب اللي بيولّده مباشرة — مش تحت الوحدة والتقريب.
                CwCard(
                    style = CwCardStyle.Inset,
                    contentPadding = PaddingValues(Space.sm)
                ) {
                    Text(
                        preview.error ?: preview.value?.let {
                            "= " + "%.${roundTo.coerceIn(0, 6)}f".format(it) +
                                if (unit.isNotBlank()) " $unit" else ""
                        } ?: "Write an expression, or use ∑ and fx",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (preview.error != null) c.danger.fg else c.accent
                    )
                }

                CwField(
                    value = unit, onValueChange = { unit = it },
                    label = "Unit (optional)", placeholder = "m3"
                )

                // الوحدات دي هي اللي بتتكتب فعليًا في كشف الحصر. كتابتها
                // بالإيد في كل صيغة بتخلّي "m2" و"M2" و"m²" وحدات مختلفة
                // في الجمع من غير ما حد ياخد باله.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    listOf("m", "m2", "m3", "no", "kg", "ton").forEach { u ->
                        KeyChip(u, accent = unit.trim() == u) { unit = u }
                    }
                }

                // العنوان فوق الشيبس مش جنبها: `weight(1f)` جنب صف شيبس
                // بياخد عرضه الطبيعي بيخنق النص لصفر تقريبًا، فبيتلف حرف
                // في كل سطر — وده اللي كان حاصل هنا بالظبط.
                Text("Decimals", style = CwText.codeSmall, color = c.textTertiary)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    (0..4).forEach { d ->
                        KeyChip(d.toString(), accent = roundTo == d) { roundTo = d }
                    }
                }
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    }

    if (pickingFunction) {
        FunctionPickerDialog(
            onPick = { call -> insert(call); pickingFunction = false },
            onDismiss = { pickingFunction = false }
        )
    }

    if (pickingFormula) {
        FormulaPickerDialog(
            formulas = formulas.filter { it.id != initial?.id?.toString() },
            onPick = { picked -> insert(takeoffSlug(picked.name)); pickingFormula = false },
            onDismiss = { pickingFormula = false }
        )
    }

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
        title = { Text("Pick a measurement") },
        text = {
            if (referencable.isEmpty()) {
                Text("No measurements on this drawing yet", color = c.textTertiary)
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
                                            "page ${item.page + 1}",
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
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

/**
 * مفتاح إدخال — عملية حسابية، وحدة، أو رقم خانات.
 *
 * ٤٤dp حد أدنى في الاتجاهين: دي أصغر مساحة لمس معقولة على الموبايل،
 * والمفاتيح دي حرف واحد فالنص لوحده بيطلع هدف أصغر من الإصبع.
 */
@Composable
private fun KeyChip(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(
        onClick = onClick,
        shape = Radius.shapeMd,
        color = if (accent) c.accent.copy(alpha = 0.18f) else c.surfaceAlt,
        border = if (accent) BorderStroke(Stroke.hair, c.accent.copy(alpha = 0.5f)) else null
    ) {
        Box(
            Modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .padding(horizontal = Space.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (accent) c.accent else c.textPrimary,
                maxLines = 1
            )
        }
    }
}

/**
 * زرار استدعاء — رمز فوق واسم تحته.
 *
 * الرمز لوحده (`∑`, `fx`) مابيقولش لحد بيعمل إيه أول مرة، والاسم لوحده
 * بياخد عرض. الاتنين مع بعض في تلت العرض بيخلّوا التلاتة ظاهرين دايمًا
 * من غير تمرير.
 */
@Composable
private fun InsertButton(
    symbol: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = Radius.shapeMd,
        color = c.accent.copy(alpha = 0.18f),
        border = BorderStroke(Stroke.hair, c.accent.copy(alpha = 0.5f))
    ) {
        Column(
            Modifier
                .defaultMinSize(minHeight = 52.dp)
                .padding(vertical = Space.sm, horizontal = Space.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.accent,
                maxLines = 1
            )
            Text(
                label,
                style = CwText.codeSmall,
                color = c.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * مجموع الصيغ لكل وحدة.
 *
 * ده الرقم اللي بيتنقل لكشف الكميات — الكارت هنا عشان مايتجمعش بالإيد
 * من فوق شاشة فيها عشرين صيغة.
 */
@Composable
private fun UnitTotalsCard(totals: List<Pair<String, Double>>) {
    val c = LocalCwColors.current
    CwCard(
        style = CwCardStyle.Accent,
        accent = c.accent,
        contentPadding = PaddingValues(Space.md)
    ) {
        Text(
            "Totals by unit",
            style = CwText.codeSmall,
            color = c.textTertiary
        )
        Spacer(Modifier.height(Space.xs))
        totals.forEach { (unit, value) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "%.2f".format(value),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.accent
                )
            }
        }
    }
}

/**
 * اسم مايتكررش.
 *
 * النسخة لازم يبقى ليها اسم تاني: الصيغ بتستدعي بعضها **بالاسم**، فاسمين
 * متطابقين معناهم إن الاستدعاء بيروح لواحدة منهم عشوائيًا.
 */
private fun uniqueFormulaName(base: String, taken: List<String>): String {
    val existing = taken.map { takeoffSlug(it) }.toSet()
    var candidate = "$base copy"
    var n = 2
    while (takeoffSlug(candidate) in existing) {
        candidate = "$base copy $n"
        n++
    }
    return candidate
}

/** الدوال المتاحة في المحرّك — بتوقيعها وسطر بيقول بتعمل إيه. */
private val FORMULA_FUNCTIONS = listOf(
    Triple("ROUND(", "ROUND(x, digits)", "Round x to a number of decimals"),
    Triple("ABS(", "ABS(x)", "Drop the sign"),
    Triple("MIN(", "MIN(a, b, …)", "Smallest of the values"),
    Triple("MAX(", "MAX(a, b, …)", "Largest of the values"),
    Triple("SQRT(", "SQRT(x)", "Square root"),
    Triple("CEIL(", "CEIL(x)", "Round up — bars, sheets, whole units"),
    Triple("FLOOR(", "FLOOR(x)", "Round down")
)

/**
 * منتقي الدوال.
 *
 * المحرّك بيعرف سبع دوال بس، ومحدش بيحفظها. عرضها بتوقيعها هنا بيخلّيها
 * قابلة للاستخدام من غير ما المستخدم يخمّن الاسم ويستنى `#REF!`.
 */
@Composable
private fun FunctionPickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Insert a function") },
            text = {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(FORMULA_FUNCTIONS, key = { it.first }) { (call, signature, detail) ->
                        Column(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(Space.xxs))
                            CwCard(
                                style = CwCardStyle.Inset,
                                onClick = { onPick(call) },
                                contentPadding = PaddingValues(Space.sm)
                            ) {
                                Text(
                                    signature,
                                    style = CwText.codeSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    detail,
                                    style = CwText.codeSmall,
                                    color = c.textTertiary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }
}

/** الصيغ اللي ينفع تستدعيها جوّه صيغة تانية. */
@Composable
private fun FormulaPickerDialog(
    formulas: List<TakeoffFormula>,
    onPick: (TakeoffFormula) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pick a formula") },
            text = {
                if (formulas.isEmpty()) {
                    Text("No other formulas yet", color = c.textTertiary)
                } else {
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(formulas, key = { it.id }) { f ->
                            Column(Modifier.fillMaxWidth()) {
                                Spacer(Modifier.height(Space.xxs))
                                CwCard(
                                    style = CwCardStyle.Inset,
                                    onClick = { onPick(f) },
                                    contentPadding = PaddingValues(Space.sm)
                                ) {
                                    Text(
                                        takeoffSlug(f.name),
                                        style = CwText.codeSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        f.expr.ifBlank { "—" },
                                        style = CwText.codeSmall,
                                        color = c.textTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
        )
    }
}

/**
 * تلوين نص الصيغة.
 *
 * المرجع المعروف بلون التمييز، والمرجع اللي مالوش مقابل بلون التحذير —
 * فاسم غلطان بيبان وانت بتكتبه، مش بعد الحفظ لما النتيجة تطلع `#REF!`.
 * الطول مابيتغيّرش، فتحويل المواضع هو نفسه (`Identity`) والمؤشّر بيفضل مظبوط.
 */
private class ExpressionHighlighter(
    private val known: Set<String>,
    private val refColor: Color,
    private val unknownColor: Color,
    private val plainColor: Color
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)
        builder.addStyle(SpanStyle(color = plainColor), 0, raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch.isLetter() || ch == '_') {
                var j = i
                while (j < raw.length && (raw[j].isLetterOrDigit() || raw[j] == '_' || raw[j] == '.')) j++
                val word = raw.substring(i, j)
                // الدالة زي ROUND( مش مرجع — القوس بعدها هو الفرق.
                val isCall = j < raw.length && raw[j] == '('
                if (!isCall) {
                    val color = if (word in known) refColor else unknownColor
                    builder.addStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold), i, j)
                }
                i = j
            } else i++
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
