package com.corewall.qaqc.pdfengine

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

/**
 * حالة العرض — التكبير والإزاحة والرصّ.
 *
 * التحويل كله معادلة واحدة، ومكتوبة هنا مرة واحدة عشان مايتكررش غلط:
 *
 * ```
 * شاشة = مستند × تكبير + إزاحة
 * مستند = (شاشة − إزاحة) ÷ تكبير
 * ```
 *
 * [zoom] بكسل لكل نقطة PDF. يعني ١.٠ = ٧٢ نقطة في البوصة (مقاس الطباعة
 * الطبيعي)، و٤.٠ = ٢٨٨ نقطة في البوصة. الحدود من [ZoomLadder].
 */
@Stable
class PdfViewerState(
    val pageCount: Int
) {
    var zoom by mutableFloatStateOf(1f)
        private set

    var panX by mutableFloatStateOf(0f)
        private set

    var panY by mutableFloatStateOf(0f)
        private set

    var viewport by mutableStateOf(IntSize.Zero)
        private set

    var mode by mutableStateOf(ViewMode.CONTINUOUS_VERTICAL)
        private set

    var layout by mutableStateOf(PageLayout(emptyList(), 0f, 0f))
        private set

    /** الصفحة اللي في مركز الشاشة — دي اللي بتتعرض في المؤشر. */
    var currentPage by mutableIntStateOf(0)
        private set

    /** بيتغيّر مع كل حركة — الواجهة بتراقبه عشان تعيد حساب المربّعات. */
    var revision by mutableIntStateOf(0)
        private set

    /** true وقت ما الأصابع على الشاشة — بنوقف التأثيرات الغالية ساعتها. */
    var interacting by mutableStateOf(false)

    private var hasFramed = false

    // ───────────────────────────────────────────────── التحويلات

    /** المستطيل المرئي في مساحة المستند (بالنقط). */
    fun visibleDocRect(): Rect {
        if (viewport.width == 0 || viewport.height == 0) return Rect.Zero
        val l = -panX / zoom
        val t = -panY / zoom
        return Rect(l, t, l + viewport.width / zoom, t + viewport.height / zoom)
    }

    fun docToScreenX(x: Float) = x * zoom + panX
    fun docToScreenY(y: Float) = y * zoom + panY
    fun screenToDoc(p: Offset) = Offset((p.x - panX) / zoom, (p.y - panY) / zoom)

    // ───────────────────────────────────────────────── التعديل

    /**
     * الاسم `updateViewport` مش `setViewport` عن قصد: الخاصية `viewport`
     * بتولّد `setViewport` على الـJVM، فدالة بنفس الاسم بتصطدم بيها.
     */
    fun updateViewport(size: IntSize) {
        if (size == viewport) return
        viewport = size
        if (!hasFramed && size.width > 0 && layout.contentWidth > 0f) {
            fitWidth()
            hasFramed = true
        } else {
            clamp()
        }
        bump()
    }

    /** نفس سبب [updateViewport] — الخاصية `layout` بتولّد `setLayout`. */
    fun updateLayout(next: PageLayout) {
        val wasEmpty = layout.slots.isEmpty()
        layout = next
        if (wasEmpty && next.slots.isNotEmpty() && viewport.width > 0) {
            fitWidth()
            hasFramed = true
        } else {
            clamp()
        }
        bump()
    }

    /**
     * تغيير وضع العرض — بنحافظ على الصفحة اللي المستخدم شايفها.
     * التبديل من رأسي لأفقي من غير ده بيرمي المستخدم لأول المستند،
     * وهو أسوأ إحساس ممكن في عارض.
     */
    fun setMode(next: ViewMode, sizes: List<SizePt>) {
        if (next == mode) return
        val keep = currentPage
        mode = next
        layout = PageLayout.build(sizes, next)
        goToPage(keep, animateZoom = false)
        bump()
    }

    fun setZoomRaw(next: Float) {
        zoom = next.coerceIn(ZoomLadder.MIN_ZOOM, ZoomLadder.MAX_ZOOM)
        clamp()
        bump()
    }

    /** تكبير حوالين نقطة ثابتة — النقطة اللي تحت الأصابع مابتتحركش. */
    fun zoomBy(factor: Float, focus: Offset) {
        val target = (zoom * factor).coerceIn(ZoomLadder.MIN_ZOOM, ZoomLadder.MAX_ZOOM)
        val applied = target / zoom
        if (applied == 1f) return
        // نحافظ على: (focus - pan) / zoom ثابتة قبل وبعد
        panX = focus.x - (focus.x - panX) * applied
        panY = focus.y - (focus.y - panY) * applied
        zoom = target
        clamp()
        bump()
    }

    fun panBy(dx: Float, dy: Float) {
        panX += dx
        panY += dy
        clamp()
        bump()
    }

    fun fitWidth() {
        if (viewport.width == 0 || layout.contentWidth <= 0f) return
        zoom = (viewport.width / layout.contentWidth)
            .coerceIn(ZoomLadder.MIN_ZOOM, ZoomLadder.MAX_ZOOM)
        panX = 0f
        clamp()
        bump()
    }

    fun fitPage() {
        val slot = layout.slotAt(currentPage) ?: return
        if (viewport.width == 0 || viewport.height == 0) return
        zoom = min(
            viewport.width / slot.size.width,
            viewport.height / slot.size.height
        ).coerceIn(ZoomLadder.MIN_ZOOM, ZoomLadder.MAX_ZOOM)
        centreOn(slot)
        bump()
    }

    /** ١٠٠٪ = بكسل لكل نقطة. */
    fun actualSize() {
        val focus = Offset(viewport.width / 2f, viewport.height / 2f)
        zoomBy(1f / zoom, focus)
    }

    fun goToPage(index: Int, animateZoom: Boolean = false) {
        val slot = layout.slotAt(index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) ?: return
        if (animateZoom) fitPage() else centreOn(slot)
        currentPage = slot.index
        bump()
    }

    /** بيحطّ الصفحة في المشهد: أعلى الصفحة فوق، ومتمركزة أفقياً. */
    private fun centreOn(slot: PageSlot) {
        panY = -slot.top * zoom + PAGE_TOP_MARGIN_PX
        panX = (viewport.width - slot.size.width * zoom) / 2f - slot.left * zoom
        clamp()
    }

    /** تكبير على مستطيل معيّن من مساحة المستند — للتكبير الذكي وللاختيار. */
    fun zoomToRect(rect: Rect, paddingPx: Float = 24f) {
        if (viewport.width == 0 || rect.width <= 0f || rect.height <= 0f) return
        val target = min(
            (viewport.width - paddingPx * 2) / rect.width,
            (viewport.height - paddingPx * 2) / rect.height
        ).coerceIn(ZoomLadder.MIN_ZOOM, ZoomLadder.MAX_ZOOM)
        zoom = target
        panX = viewport.width / 2f - rect.center.x * target
        panY = viewport.height / 2f - rect.center.y * target
        clamp()
        bump()
    }

    /**
     * بيمنع المحتوى يهرب برّه الشاشة.
     *
     * لو المحتوى أصغر من الشاشة في محور، بيتمركز فيه. لو أكبر، بيتقيّد
     * جواه بهامش بسيط. من غير الحدّ ده المستخدم بيقدر "يرمي" الرسمة برّه
     * الشاشة ويلاقي نفسه بيبصّ على فراغ ومش عارف يرجع.
     */
    private fun clamp() {
        if (viewport.width == 0 || viewport.height == 0) return
        val contentW = layout.contentWidth * zoom
        val contentH = layout.contentHeight * zoom
        val vw = viewport.width.toFloat()
        val vh = viewport.height.toFloat()

        panX = if (contentW <= vw) (vw - contentW) / 2f
        else panX.coerceIn(vw - contentW - EDGE_SLACK_PX, EDGE_SLACK_PX)

        panY = if (contentH <= vh) (vh - contentH) / 2f
        else panY.coerceIn(vh - contentH - EDGE_SLACK_PX, EDGE_SLACK_PX)

        // الصفحة الحالية = اللي في مركز الشاشة
        val centre = screenToDoc(Offset(vw / 2f, vh / 2f))
        val page = layout.pageAt(centre.x, centre.y)
        if (page != currentPage) currentPage = page
    }

    private fun bump() { revision++ }

    /** نسبة التكبير للعرض — "١٠٠٪" أوضح من "1.0". */
    fun zoomPercent(): Int = (zoom * 100f).toInt()

    /**
     * المربّعات المطلوبة دلوقتي، مرتّبة من مركز الشاشة للخارج.
     *
     * الترتيب مهم: الطابور بينفّذ بالترتيب، فاللي المستخدم باصص عليه
     * بيوصل الأول واللي على الحواف بيستنى.
     */
    fun requiredTiles(session: PdfDocumentSession, prefetch: Int = 1): List<TileKey> {
        val rect = visibleDocRect()
        if (rect.width <= 0f) return emptyList()
        val level = ZoomLadder.levelFor(zoom)
        val centre = rect.center

        val out = ArrayList<TileKey>(64)
        for (slot in layout.visible(rect.left, rect.top, rect.right, rect.bottom)) {
            val size = session.knownSize(slot.index) ?: slot.size
            val grid = TileGrid(slot.index, level, size)
            // من إحداثيات المستند لإحداثيات الصفحة
            val l = rect.left - slot.left
            val t = rect.top - slot.top
            val r = rect.right - slot.left
            val b = rect.bottom - slot.top
            out += grid.tilesIn(l, t, r, b, prefetch)
        }

        // الأقرب لمركز الشاشة الأول
        return out.sortedBy { key ->
            val slot = layout.slotAt(key.page) ?: return@sortedBy Float.MAX_VALUE
            val scale = ZoomLadder.scaleOf(key.level)
            val tx = slot.left + (key.col * TILE_SIZE + TILE_SIZE / 2f) / scale
            val ty = slot.top + (key.row * TILE_SIZE + TILE_SIZE / 2f) / scale
            abs(tx - centre.x) + abs(ty - centre.y)
        }
    }

    companion object {
        private const val EDGE_SLACK_PX = 48f
        private const val PAGE_TOP_MARGIN_PX = 16f
    }
}
