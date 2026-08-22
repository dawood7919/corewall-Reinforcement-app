package com.corewall.qaqc.ui.takeoff

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffCategory
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.ui.design.CwCard
import com.corewall.qaqc.ui.design.CwCardStyle
import com.corewall.qaqc.ui.design.CwCountPill
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.launch

/**
 * بيانات الحصر كشاشة قائمة بذاتها — مش شيت فوق الرسمة.
 *
 * الفصل مقصود: مراجعة الكميات شغل قراية وفرز وبحث، ومحتاج الشاشة كلها.
 *
 * التنظيم كارت لكل فئة بدل قايمة مسطّحة برؤوس: الكارت بيدّي الفئة حدود
 * بصرية واضحة، وبيخلّي طيّها ممكن — ومراجعة الحصر بتحصل فئة فئة، مش
 * بالتمرير في كل حاجة مرة واحدة.
 *
 * الضغط على قياس بيسجّل طلب تركيز في [com.corewall.qaqc.takeoff.TakeoffStore]
 * وبيرجع للرسمة — الشاشة دي ماعندهاش تحكّم في الكاميرا، والمحرّر هو اللي
 * بيقرا الطلب وينفّذه.
 */
@Composable
fun TakeoffDataScreen(
    vm: MainViewModel,
    drawingId: Long,
    onOpenDrawing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<TakeoffItem?>(null) }
    // مطويّة بالمفتاح مش بالفهرس — إضافة فئة مالهاش تحرّك طيّ فئة تانية.
    // `remember` مش `rememberSaveable`: المجموعة مش من الأنواع اللي الـBundle
    // بيشيلها، والحافظ التلقائي بيرمي عليها وقت التشغيل.
    var collapsed by remember { mutableStateOf(setOf<String>()) }

    var projectId by remember(drawingId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(drawingId) { projectId = vm.takeoff.drawingById(drawingId)?.projectId }

    val rows by remember(drawingId) { vm.takeoff.items(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val scaleRows by remember(drawingId) { vm.takeoff.scales(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val categoryRows by remember(projectId) {
        projectId?.let { vm.takeoff.categories(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())

    val allItems = remember(rows) { rows.map { vm.takeoff.toModel(it) } }
    val categories = remember(categoryRows) { categoryRows.map { vm.takeoff.categoryToModel(it) } }

    // مقاس الصفحة بيتقرا من الملف — المساحة بتتأثر بيه تربيعيًا، وافتراض
    // A4 كان بيدّي أرقام أصغر من اللي على الرسمة بمعامل ثابت.
    val pagesInUse = remember(allItems) { allItems.map { it.page }.toSet() }
    val pageGeometryFor = rememberDrawingPageGeometry(vm, drawingId, scaleRows, pagesInUse)

    // الخصومات بتترسم كفتحة في بندها الأب، فمالهاش كارت مستقل. `allItems`
    // بتفضل كاملة وواصلة لـ`netOf` عشان الطرح يشتغل صح.
    val buckets = remember(allItems, categories, query) {
        bucketsFor(allItems, categories, query)
    }

    Surface(modifier.fillMaxSize(), color = c.background) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = Space.lg)
        ) {
            Spacer(Modifier.height(Space.sm))
            CwField(
                value = query,
                onValueChange = { query = it },
                label = "بحث بالاسم",
                leading = { Icon(Icons.Filled.Search, contentDescription = null, tint = c.textTertiary) }
            )
            Spacer(Modifier.height(Space.sm))

            when {
                allItems.none { it.tool != TakeoffTool.DEDUCT } -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CwEmptyState(
                        icon = Icons.Filled.Search,
                        title = "مفيش قياسات في الرسمة دي لسه",
                        detail = "افتح الرسمة وابدأ أول قياس"
                    )
                }

                buckets.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CwEmptyState(icon = Icons.Filled.Search, title = "مفيش نتايج لـ\"$query\"")
                }

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    contentPadding = PaddingValues(bottom = Space.xl)
                ) {
                    items(buckets, key = { it.key }) { bucket ->
                        CategoryCard(
                            bucket = bucket,
                            allItems = allItems,
                            pageGeometryFor = pageGeometryFor,
                            expanded = bucket.key !in collapsed,
                            onToggleExpand = {
                                collapsed = if (bucket.key in collapsed) collapsed - bucket.key
                                else collapsed + bucket.key
                            },
                            onOpen = { item ->
                                vm.takeoff.requestFocus(item.id.toLongOrNull())
                                onOpenDrawing()
                            },
                            onEdit = { editingItem = it },
                            onToggleVisible = { item ->
                                val id = item.id.toLongOrNull() ?: return@CategoryCard
                                scope.launch {
                                    vm.takeoff.itemById(id)?.let {
                                        vm.takeoff.saveItem(it.copy(visible = !it.visible))
                                    }
                                }
                            },
                            onHideAll = { hide ->
                                scope.launch {
                                    bucket.items.forEach { item ->
                                        val id = item.id.toLongOrNull() ?: return@forEach
                                        vm.takeoff.itemById(id)?.let {
                                            if (it.visible == hide) vm.takeoff.saveItem(it.copy(visible = !hide))
                                        }
                                    }
                                }
                            },
                            onDelete = { item ->
                                val id = item.id.toLongOrNull() ?: return@CategoryCard
                                scope.launch { vm.takeoff.deleteItem(id) }
                            }
                        )
                    }
                }
            }
        }
    }

    editingItem?.let { editing ->
        TakeoffEditItemSheet(
            item = editing,
            categories = categoryRows,
            onCreateCategory = { name, color, onCreated ->
                val pid = projectId
                if (pid != null) {
                    scope.launch { onCreated(vm.takeoff.createCategory(pid, name, color).id) }
                }
            },
            onSave = { name, categoryId, colorArgb, zone, progressPercent, rateOverride ->
                val itemId = editing.id.toLongOrNull()
                if (itemId != null) {
                    scope.launch {
                        vm.takeoff.itemById(itemId)?.let { row ->
                            vm.takeoff.saveItem(
                                row.copy(
                                    name = name, categoryId = categoryId, colorArgb = colorArgb,
                                    zone = zone, progressPercent = progressPercent,
                                    rateOverride = rateOverride
                                )
                            )
                        }
                    }
                }
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }
}

/** فئة واحدة وبنودها — وحدة العرض في الشاشة دي. */
private data class CategoryBucket(
    val key: String,
    val category: TakeoffCategory?,
    val items: List<TakeoffItem>
)

/**
 * تجميع البنود على فئاتها.
 *
 * الفئة بتتطبّع قبل التجميع: بند بيشاور على فئة مش موجودة (اتحذفت، أو
 * أول إطار قبل ما الفئات توصل) بيروح لدلو "بلا فئة" الواحد — مش دلو
 * لوحده بنفس المفتاح.
 */
private fun bucketsFor(
    allItems: List<TakeoffItem>,
    categories: List<TakeoffCategory>,
    query: String
): List<CategoryBucket> {
    val catMap = categories.associateBy { it.id }
    val displayable = allItems.asSequence()
        .filter { it.tool != TakeoffTool.DEDUCT }
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .toList()
    return displayable
        .groupBy { item -> item.categoryId?.takeIf(catMap::containsKey) }
        .toList()
        .sortedWith(compareBy({ it.first == null }, { catMap[it.first]?.name ?: "" }))
        .map { (catId, items) ->
            CategoryBucket(
                key = "cat_${catId ?: "none"}",
                category = catId?.let(catMap::get),
                items = items.sortedBy { it.name }
            )
        }
}

@Composable
private fun CategoryCard(
    bucket: CategoryBucket,
    allItems: List<TakeoffItem>,
    pageGeometryFor: (Int) -> PageGeometry,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpen: (TakeoffItem) -> Unit,
    onEdit: (TakeoffItem) -> Unit,
    onToggleVisible: (TakeoffItem) -> Unit,
    onHideAll: (Boolean) -> Unit,
    onDelete: (TakeoffItem) -> Unit
) {
    val c = LocalCwColors.current
    val accent = bucket.category?.let { Color((it.colorArgb or 0xFF000000L).toInt()) }
    val anyVisible = bucket.items.any { it.visible }

    CwCard(
        style = if (accent != null) CwCardStyle.Accent else CwCardStyle.Plain,
        accent = accent,
        contentPadding = PaddingValues(Space.md)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent ?: c.textTertiary)
            )
            Text(
                bucket.category?.name ?: "بلا فئة",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            CwCountPill(bucket.items.size)
            CwIconButton(
                if (anyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (anyVisible) "إخفاء كل الفئة" else "إظهار كل الفئة",
                { onHideAll(anyVisible) }
            )
            CwIconButton(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                if (expanded) "اطوِ" else "افرد",
                onToggleExpand
            )
        }

        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth()) {
                bucket.items.forEach { item ->
                    Spacer(Modifier.height(Space.xs))
                    MeasurementCardRow(
                        item = item,
                        quantityText = formatQuantity(
                            item.tool, netOf(item, allItems, pageGeometryFor(item.page))
                        ),
                        onOpen = { onOpen(item) },
                        onEdit = { onEdit(item) },
                        onToggleVisible = { onToggleVisible(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementCardRow(
    item: TakeoffItem,
    quantityText: String,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggleVisible: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalCwColors.current
    CwCard(style = CwCardStyle.Inset, onClick = onOpen, contentPadding = PaddingValues(Space.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name.ifBlank { "بلا اسم" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.visible) c.textPrimary else c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${toolLabel(item.tool)} · صفحة ${item.page + 1}",
                    style = CwText.codeSmall,
                    color = c.textTertiary,
                    maxLines = 1
                )
            }
            Text(
                quantityText,
                style = CwText.codeSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (item.visible) c.textPrimary else c.textTertiary
            )
            CwIconButton(Icons.Filled.Edit, "تعديل", onEdit)
            CwIconButton(
                if (item.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (item.visible) "إخفاء" else "إظهار",
                onToggleVisible
            )
            CwIconButton(Icons.Filled.Delete, "حذف", onDelete, tint = c.danger.fg)
        }
    }
}

private fun toolLabel(tool: TakeoffTool): String = when (tool) {
    TakeoffTool.AREA -> "مساحة"
    TakeoffTool.LENGTH -> "طول"
    TakeoffTool.COUNT -> "عدّ"
    TakeoffTool.VOLUME -> "حجم"
    TakeoffTool.COLUMN -> "أعمدة"
    TakeoffTool.DIMENSION -> "بُعد"
    TakeoffTool.DEDUCT -> "خصم"
}
