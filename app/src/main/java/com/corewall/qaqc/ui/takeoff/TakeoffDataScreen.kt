package com.corewall.qaqc.ui.takeoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwField
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Space
import kotlinx.coroutines.launch

/**
 * بيانات الحصر كشاشة قائمة بذاتها — مش شيت فوق الرسمة.
 *
 * الفصل مقصود: مراجعة الكميات شغل قراية وفرز وبحث، ومحتاج الشاشة كلها.
 * لما كانت شيت، نص الرسمة كان فاضل ظاهر تحتها من غير فايدة، والقايمة
 * نفسها كانت مخنوقة في ٨٨٪ من الارتفاع.
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

    var projectId by remember(drawingId) { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(drawingId) {
        projectId = vm.takeoff.drawingById(drawingId)?.projectId
    }

    val rows by remember(drawingId) { vm.takeoff.items(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val scaleRows by remember(drawingId) { vm.takeoff.scales(drawingId) }
        .collectAsStateWithLifecycle(emptyList())
    val categoryRows by remember(projectId) {
        projectId?.let { vm.takeoff.categories(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())
    val groupRows by remember(projectId) {
        projectId?.let { vm.takeoff.groups(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())

    val allItems = remember(rows) { rows.map { vm.takeoff.toModel(it) } }
    val categories = remember(categoryRows) { categoryRows.map { vm.takeoff.categoryToModel(it) } }
    val groups = remember(groupRows) { groupRows.map { vm.takeoff.groupToModel(it) } }

    /**
     * الصفحة بتتقاس بالنقط، والمقاس الحقيقي جوّه ملف الـPDF — وإحنا مش
     * فاتحينه هنا عن قصد (فتح PDFium لمجرد عرض قايمة تبذير). المقاس
     * الافتراضي A4 بالنقط كافي: المعايرة المتخزّنة هي اللي بتحدد المتر
     * لكل نقطة، والنسبة دي هي اللي بتحكم الرقم.
     */
    val pageGeometryFor = remember(scaleRows) {
        { page: Int ->
            val mpp = scaleRows.firstOrNull { it.page == page }?.metresPerPoint ?: 0.0
            PageGeometry(A4_WIDTH_PT, A4_HEIGHT_PT, mpp)
        }
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

            // الخصومات بتترسم كفتحة في بندها الأب، فمالهاش صف مستقل هنا.
            // `allItems` بتفضل كاملة وواصلة لـ`netOf` عشان الطرح يشتغل صح.
            val displayable = remember(allItems, query) {
                allItems.asSequence()
                    .filter { it.tool != TakeoffTool.DEDUCT }
                    .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                    .toList()
            }
            val treeRows = remember(displayable, categories, groups) {
                buildTreeRows(displayable, categories, groups)
            }

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

                treeRows.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CwEmptyState(icon = Icons.Filled.Search, title = "مفيش نتايج لـ\"$query\"")
                }

                else -> LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs)
                ) {
                    items(
                        treeRows,
                        key = { row ->
                            when (row) {
                                is TreeRow.CategoryHeader -> "cat_${row.category?.id ?: "none"}"
                                is TreeRow.GroupHeader -> "grp_${row.group.id}"
                                is TreeRow.ItemRow -> "item_${row.item.id}"
                            }
                        }
                    ) { row ->
                        when (row) {
                            is TreeRow.CategoryHeader -> CategoryHeaderRow(row.category, row.count)
                            is TreeRow.GroupHeader -> GroupHeaderRow(row.group)
                            is TreeRow.ItemRow -> MeasurementRow(
                                item = row.item,
                                quantityText = formatQuantity(
                                    row.item.tool,
                                    netOf(row.item, allItems, pageGeometryFor(row.item.page))
                                ),
                                onSelect = {
                                    vm.takeoff.requestFocus(row.item.id.toLongOrNull())
                                    onOpenDrawing()
                                },
                                onToggleVisible = {
                                    val id = row.item.id.toLongOrNull()
                                    if (id != null) {
                                        scope.launch {
                                            vm.takeoff.itemById(id)?.let {
                                                vm.takeoff.saveItem(it.copy(visible = !it.visible))
                                            }
                                        }
                                    }
                                },
                                onDelete = {
                                    val id = row.item.id.toLongOrNull()
                                    if (id != null) scope.launch { vm.takeoff.deleteItem(id) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** مقاس A4 بالنقط — الافتراضي لما الرسمة نفسها مش مفتوحة. */
private const val A4_WIDTH_PT = 595.0
private const val A4_HEIGHT_PT = 842.0
