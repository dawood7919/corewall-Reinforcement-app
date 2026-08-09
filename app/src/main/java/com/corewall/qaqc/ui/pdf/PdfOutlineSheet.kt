package com.corewall.qaqc.ui.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.data.db.PdfBookmarkEntity
import com.corewall.qaqc.pdfengine.OutlineEntry
import com.corewall.qaqc.ui.design.CwEmptyState
import com.corewall.qaqc.ui.design.CwIconButton
import com.corewall.qaqc.ui.design.CwText
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Sizes
import com.corewall.qaqc.ui.design.Space

/**
 * الفهرس والعلامات في ورقة واحدة.
 *
 * الاتنين بيجاوبوا على نفس السؤال — "أروح فين؟" — فمنطقي يبقوا في مكان
 * واحد بتبويبتين، مش زرارين في شريط مزدحم أصلاً. الفرق بينهم إن الفهرس
 * جاي من الملف والعلامات من المستخدم، وده بيتقال في التبويبة نفسها.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfOutlineSheet(
    outline: List<OutlineEntry>,
    bookmarks: List<PdfBookmarkEntity>,
    currentPage: Int,
    onGoTo: (Int) -> Unit,
    onAddBookmark: () -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalCwColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // الفهرس فاضي؟ ابدأ على العلامات — تبويبة فاضية كتحية مش استقبال كويس.
    var tab by remember { mutableStateOf(if (outline.isEmpty()) Tab.BOOKMARKS else Tab.OUTLINE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = Radius.sheet
    ) {
        Column(Modifier.padding(bottom = Space.lg)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                TabButton("الفهرس", outline.size, tab == Tab.OUTLINE) { tab = Tab.OUTLINE }
                TabButton("علاماتي", bookmarks.size, tab == Tab.BOOKMARKS) { tab = Tab.BOOKMARKS }
                Spacer(Modifier.weight(1f))
                if (tab == Tab.BOOKMARKS) {
                    CwIconButton(
                        Icons.Filled.BookmarkBorder,
                        "علّم الصفحة الحالية",
                        onAddBookmark
                    )
                }
            }

            Spacer(Modifier.height(Space.md))

            when (tab) {
                Tab.OUTLINE -> OutlineList(outline, currentPage, onGoTo)
                Tab.BOOKMARKS -> BookmarkList(
                    bookmarks, currentPage, onGoTo, onDeleteBookmark, onAddBookmark
                )
            }
        }
    }
}

private enum class Tab { OUTLINE, BOOKMARKS }

@Composable
private fun TabButton(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(
        onClick = onClick,
        shape = Radius.pill,
        color = if (selected) c.accentContainer else Color.Transparent
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) c.accent else c.textSecondary
            )
            Text(
                "$count",
                style = CwText.codeSmall,
                color = if (selected) c.accent else c.textTertiary
            )
        }
    }
}

@Composable
private fun OutlineList(entries: List<OutlineEntry>, currentPage: Int, onGoTo: (Int) -> Unit) {
    if (entries.isEmpty()) {
        CwEmptyState(
            icon = Icons.AutoMirrored.Filled.List,
            title = "الملف ده مالوش فهرس",
            detail = "الفهرس بيتكتب في الملف نفسه وقت تصديره. لو مش موجود، استخدم علاماتك."
        )
        return
    }
    LazyColumn(Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
        items(entries, key = { "${it.page}-${it.depth}-${it.title}" }) { entry ->
            val active = entry.page == currentPage
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onGoTo(entry.page) }
                    // الإزاحة بتوري العمق. من غيرها فهرس بتلات مستويات
                    // بيبان كقايمة مسطّحة ومفيش منها فايدة.
                    .padding(
                        start = Space.lg + DEPTH_STEP * entry.depth,
                        end = Space.lg,
                        top = Space.sm,
                        bottom = Space.sm
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                Text(
                    entry.title.ifBlank { "بدون عنوان" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) LocalCwColors.current.accent
                    else LocalCwColors.current.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${entry.page + 1}",
                    style = CwText.codeSmall,
                    color = LocalCwColors.current.textTertiary
                )
            }
        }
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<PdfBookmarkEntity>,
    currentPage: Int,
    onGoTo: (Int) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: () -> Unit
) {
    val c = LocalCwColors.current
    if (bookmarks.isEmpty()) {
        CwEmptyState(
            icon = Icons.Filled.BookmarkBorder,
            title = "مفيش علامات لسه",
            detail = "علّم الصفحات اللي بترجعلها كتير — تفاصيل الكانات، جدول الأكواد، الكشف.",
            action = {
                CwIconButton(Icons.Filled.BookmarkBorder, "علّم الصفحة الحالية", onAdd)
            }
        )
        return
    }
    LazyColumn(Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
        items(bookmarks, key = { it.id }) { mark ->
            val active = mark.page == currentPage
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onGoTo(mark.page) }
                    .padding(start = Space.lg, end = Space.sm, top = Space.xs, bottom = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                Box(
                    Modifier
                        .width(Sizes.accentEdge)
                        .height(BOOKMARK_EDGE)
                        .background(if (active) c.accent else c.outline, Radius.shapeSm)
                )
                Text(
                    mark.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) c.accent else c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("${mark.page + 1}", style = CwText.codeSmall, color = c.textTertiary)
                CwIconButton(
                    Icons.Filled.DeleteOutline, "احذف العلامة",
                    { onDelete(mark.id) },
                    tint = c.textTertiary
                )
            }
        }
    }
}

private val LIST_MAX_HEIGHT = 420.dp
private val DEPTH_STEP = 14.dp
private val BOOKMARK_EDGE = 24.dp
