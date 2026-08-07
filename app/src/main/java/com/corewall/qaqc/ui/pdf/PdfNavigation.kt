package com.corewall.qaqc.ui.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.pdfengine.PdfViewerState
import com.corewall.qaqc.ui.design.Elevation
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.design.Stroke as CwStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * كاش المصغّرات.
 *
 * منفصل عن كاش المربّعات عن قصد: المصغّرة عمرها المفيد أطول بكتير (بتفضل
 * ظاهرة في الشريط طول ما انت في الملف)، وحجمها تافه. لو حطّيناها في نفس
 * الكاش، التمرير في التكبير العالي كان هيطردها ويعيد رسمها كل شوية.
 */
class ThumbnailCache(
    private val session: PdfDocumentSession,
    private val maxPx: Int = 260
) {
    val thumbs: SnapshotStateMap<Int, ImageBitmap> = mutableStateMapOf()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = HashSet<Int>()

    fun request(page: Int) {
        if (page < 0 || page >= session.pageCount) return
        if (thumbs.containsKey(page) || !pending.add(page)) return
        scope.launch {
            session.measure(page)
            val size = session.sizeOrEstimate(page)
            val scale = maxPx / maxOf(size.width, size.height)
            val w = (size.width * scale).toInt().coerceAtLeast(1)
            val h = (size.height * scale).toInt().coerceAtLeast(1)
            val bmp = session.renderTile(page, w, h, 0, 0, w, h)
            pending.remove(page)
            if (bmp != null) thumbs[page] = bmp.asImageBitmap()
        }
    }

    fun clear() {
        scope.cancel()
        thumbs.clear()
        pending.clear()
    }
}

/**
 * شريط المصغّرات.
 *
 * بيتمركز لوحده على الصفحة الحالية. من غير ده، في مستند ٣٠٠ صفحة الشريط
 * بيفضل على الأول والمستخدم لازم يدوّر على نفسه — وده بيخلّي الشريط عبء
 * مش اختصار.
 */
@Composable
fun ThumbnailRail(
    session: PdfDocumentSession,
    cache: ThumbnailCache,
    currentPage: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    val listState = rememberLazyListState()

    LaunchedEffect(currentPage) {
        if (currentPage >= 0) {
            runCatching { listState.animateScrollToItem(currentPage.coerceAtLeast(0)) }
        }
    }

    Surface(
        modifier.fillMaxWidth(),
        color = c.surface.copy(alpha = 0.96f),
        shadowElevation = Elevation.floating
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            items(count = session.pageCount, key = { it }) { page ->
                ThumbnailCell(
                    page = page,
                    image = cache.thumbs[page],
                    selected = page == currentPage,
                    onRequest = { cache.request(page) },
                    onClick = { onPick(page) }
                )
            }
        }
    }
}

@Composable
private fun ThumbnailCell(
    page: Int,
    image: ImageBitmap?,
    selected: Boolean,
    onRequest: () -> Unit,
    onClick: () -> Unit
) {
    val c = LocalCwColors.current
    LaunchedEffect(page) { onRequest() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = Radius.shapeSm,
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(
                if (selected) CwStroke.thick else CwStroke.hair,
                if (selected) c.accent else c.outline
            ),
            modifier = Modifier
                .width(THUMB_WIDTH)
                .aspectRatio(0.72f)
        ) {
            if (image != null) {
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = "صفحة ${page + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                Box(Modifier.fillMaxSize().background(c.surfaceAlt))
            }
        }
        Spacer(Modifier.height(Space.xxs))
        Text(
            "${page + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) c.accent else c.textTertiary
        )
    }
}

private val THUMB_WIDTH = 56.dp

/**
 * الخريطة المصغّرة — بتظهر عند التكبير العالي بس.
 *
 * عند ١٠× انت شايف ٥٪ من الرسمة ومفيش أي إشارة لمكانك منها. الخريطة
 * بتوريك الصفحة كاملة ومستطيل بيقول انت فين — وده الفرق بين "بتستكشف"
 * و"تايه".
 */
@Composable
fun MiniMap(
    state: PdfViewerState,
    session: PdfDocumentSession,
    cache: ThumbnailCache,
    modifier: Modifier = Modifier
) {
    val c = LocalCwColors.current
    val page = state.currentPage
    LaunchedEffect(page) { cache.request(page) }

    val slot = state.layout.slotAt(page) ?: return
    val image = cache.thumbs[page]

    Surface(
        modifier
            .width(MINIMAP_WIDTH)
            .aspectRatio(slot.size.width / slot.size.height.coerceAtLeast(1f)),
        shape = Radius.shapeSm,
        color = Color.White,
        shadowElevation = Elevation.floating,
        border = androidx.compose.foundation.BorderStroke(CwStroke.hair, c.outline)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (image != null) {
                androidx.compose.foundation.Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
            // مستطيل المشهد
            Canvas(Modifier.fillMaxSize()) {
                val visible = state.visibleDocRect()
                // من مساحة المستند لمساحة الصفحة المنسّبة
                val nx = ((visible.left - slot.left) / slot.size.width).coerceIn(0f, 1f)
                val ny = ((visible.top - slot.top) / slot.size.height).coerceIn(0f, 1f)
                val nw = (visible.width / slot.size.width).coerceIn(0.02f, 1f)
                val nh = (visible.height / slot.size.height).coerceIn(0.02f, 1f)

                val r = androidx.compose.ui.geometry.Rect(
                    nx * size.width,
                    ny * size.height,
                    ((nx + nw).coerceAtMost(1f)) * size.width,
                    ((ny + nh).coerceAtMost(1f)) * size.height
                )
                drawRect(
                    color = c.accent.copy(alpha = 0.18f),
                    topLeft = r.topLeft,
                    size = Size(r.width, r.height)
                )
                drawRect(
                    color = c.accent,
                    topLeft = r.topLeft,
                    size = Size(r.width, r.height),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

private val MINIMAP_WIDTH = 96.dp
