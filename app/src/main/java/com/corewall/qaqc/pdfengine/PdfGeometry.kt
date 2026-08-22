package com.corewall.qaqc.pdfengine

import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

/**
 * هندسة المحرّك — المساحات والإحداثيات وسلّم التكبير.
 *
 * فيه **تلات مساحات** في المحرّك، وخلطهم هو مصدر كل باج في عارضات الـPDF:
 *
 * 1. **مساحة النقطة (point space)** — وحدة الـPDF نفسها، ٧٢ نقطة للبوصة.
 *    مقاس الصفحة والتعليقات بيتخزّنوا هنا. مابتتأثرش بالتكبير ولا بمقاس
 *    الشاشة، فهي المكان الوحيد الآمن للتخزين.
 * 2. **مساحة المستند (document space)** — كل الصفحات مرصوصة ورا بعض
 *    بمسافات بينهم، بالنقط برضه. دي اللي بنعمل عليها التمرير.
 * 3. **مساحة الشاشة (screen space)** — بكسل. = مساحة المستند × التكبير + الإزاحة.
 */

/** مقاس صفحة بالنقط. */
data class SizePt(val width: Float, val height: Float) {
    val aspect: Float get() = if (height > 0f) width / height else 1f

    companion object {
        /** A4 رأسي — بيتستخدم كتقدير مؤقت لصفحة لسه ما اتقاستش. */
        val A4 = SizePt(595f, 842f)
    }
}

/**
 * سلّم التكبير — أساس إن الصورة تفضل حادّة عند ٦٤×.
 *
 * القاعدة اللي كل حاجة مبنية عليها: **ممنوع نكبّر صورة أبداً**. لو المستخدم
 * على ١٢×، ما بنمططش رندر الـ١×؛ بنطلب من PDFium يرسم المنطقة دي عند ١٦×
 * وبعدين نصغّرها شوية. التصغير مجاني بصرياً، التكبير هو اللي بيعمل الضبابية.
 *
 * وليه سلّم أصلاً مش رندر مستمر: الرندر عند كل قيمة تكبير معناه إعادة رسم
 * مع كل بكسل من حركة الأصابع. السلّم بيخلّي عدد المستويات محدود، والكاش
 * بينفع، والزوم بيفضل ٦٠ إطار.
 */
object ZoomLadder {

    /** أصغر مستوى: ربع الحجم الطبيعي — للمصغّرات ولعرض الصفحة كاملة. */
    const val MIN_LEVEL = 0

    /** أكبر مستوى = ٦٤× الحجم الطبيعي. */
    const val MAX_LEVEL = 8

    /** التكبير عند المستوى صفر. */
    private const val BASE_SCALE = 0.25f

    /** التكبير الفعلي لمستوى — بكسل لكل نقطة. */
    fun scaleOf(level: Int): Float = BASE_SCALE * 2f.pow(level.coerceIn(MIN_LEVEL, MAX_LEVEL))

    /**
     * أنسب مستوى لتكبير معيّن: أول مستوى **أكبر من أو يساوي** المطلوب.
     * لو رجّعنا المستوى الأقل، هنبقى بنكبّر — وده بالظبط اللي بنهرب منه.
     */
    fun levelFor(zoom: Float): Int {
        if (zoom <= BASE_SCALE) return MIN_LEVEL
        val raw = ceil(log2(zoom / BASE_SCALE)).toInt()
        return raw.coerceIn(MIN_LEVEL, MAX_LEVEL)
    }

    /** أقل وأكتر تكبير مسموح بيه في الواجهة. */
    const val MIN_ZOOM = 0.08f
    const val MAX_ZOOM = 64f
}

/**
 * مفتاح مربّع — مضغوط في `Long` واحد.
 *
 * ليه مضغوط: المفتاح ده بيتعمله hash آلاف المرات في الثانية وقت التمرير
 * (بحث في الكاش لكل مربّع في كل إطار). `data class` كان هيولّد كائن جديد
 * في كل مرة ويضغط على الـGC وسط حلقة الرسم.
 *
 * التوزيع: صفحة ٢٠ بت (مليون صفحة) · مستوى ٤ بت · صف ٢٠ بت · عمود ٢٠ بت
 */
@JvmInline
value class TileKey(val packed: Long) {

    val page: Int get() = ((packed ushr 44) and 0xFFFFF).toInt()
    val level: Int get() = ((packed ushr 40) and 0xF).toInt()
    val row: Int get() = ((packed ushr 20) and 0xFFFFF).toInt()
    val col: Int get() = (packed and 0xFFFFF).toInt()

    override fun toString() = "Tile(p=$page,L=$level,r=$row,c=$col)"

    companion object {
        fun of(page: Int, level: Int, row: Int, col: Int): TileKey = TileKey(
            ((page.toLong() and 0xFFFFF) shl 44) or
                ((level.toLong() and 0xF) shl 40) or
                ((row.toLong() and 0xFFFFF) shl 20) or
                (col.toLong() and 0xFFFFF)
        )
    }
}

/** مقاس ضلع المربّع بالبكسل. ٥١٢ = مليون بكسل تقريباً = ١ ميجا في ARGB_8888. */
const val TILE_SIZE = 512

/**
 * شبكة مربّعات صفحة عند مستوى معيّن.
 *
 * الصفحة عند المستوى ده مقاسها [pixelWidth] × [pixelHeight] بكسل، ومقسّمة
 * لمربّعات [TILE_SIZE]. المربّعات على الحافة اليمين/تحت بتبقى أصغر.
 */
class TileGrid(
    val page: Int,
    val level: Int,
    val sizePt: SizePt
) {
    val scale: Float = ZoomLadder.scaleOf(level)
    val pixelWidth: Int = (sizePt.width * scale).toInt().coerceAtLeast(1)
    val pixelHeight: Int = (sizePt.height * scale).toInt().coerceAtLeast(1)
    val cols: Int = ceil(pixelWidth / TILE_SIZE.toFloat()).toInt().coerceAtLeast(1)
    val rows: Int = ceil(pixelHeight / TILE_SIZE.toFloat()).toInt().coerceAtLeast(1)

    /** عرض المربّع الفعلي — الأخير في الصف بيبقى مقصوص. */
    fun tileWidth(col: Int): Int = minOf(TILE_SIZE, pixelWidth - col * TILE_SIZE).coerceAtLeast(1)

    fun tileHeight(row: Int): Int = minOf(TILE_SIZE, pixelHeight - row * TILE_SIZE).coerceAtLeast(1)

    /**
     * المربّعات اللي بتتقاطع مع مستطيل مرئي **في مساحة النقطة** للصفحة دي.
     * [padding] عدد المربّعات الزيادة حوالين المرئي — التحميل المسبق ده
     * هو اللي بيخلّي التمرير مايبانش فيه فراغ أبيض.
     */
    fun tilesIn(
        leftPt: Float,
        topPt: Float,
        rightPt: Float,
        bottomPt: Float,
        padding: Int = 1
    ): List<TileKey> {
        val l = ((leftPt * scale) / TILE_SIZE).toInt() - padding
        val t = ((topPt * scale) / TILE_SIZE).toInt() - padding
        val r = ((rightPt * scale) / TILE_SIZE).toInt() + padding
        val b = ((bottomPt * scale) / TILE_SIZE).toInt() + padding

        val c0 = l.coerceIn(0, cols - 1)
        val c1 = r.coerceIn(0, cols - 1)
        val r0 = t.coerceIn(0, rows - 1)
        val r1 = b.coerceIn(0, rows - 1)

        val out = ArrayList<TileKey>((c1 - c0 + 1) * (r1 - r0 + 1))
        for (row in r0..r1) for (col in c0..c1) out += TileKey.of(page, level, row, col)
        return out
    }
}

/**
 * رصّ الصفحات في مساحة المستند.
 *
 * الوضع بيغيّر الرصّ بس — مش الرسم ولا الإيماءات ولا الكاش. علشان كده
 * الأوضاع الخمسة كلها نفس الكود بدل خمس شاشات.
 */
enum class ViewMode(val label: String) {
    CONTINUOUS_VERTICAL("تمرير رأسي"),
    CONTINUOUS_HORIZONTAL("تمرير أفقي"),
    SINGLE_PAGE("صفحة واحدة"),
    DOUBLE_PAGE("صفحتين")
}

/** مكان صفحة في مساحة المستند، بالنقط. */
data class PageSlot(
    val index: Int,
    val left: Float,
    val top: Float,
    val size: SizePt
) {
    val right: Float get() = left + size.width
    val bottom: Float get() = top + size.height

    fun intersects(l: Float, t: Float, r: Float, b: Float): Boolean =
        left < r && right > l && top < b && bottom > t
}

/**
 * بيحسب مكان كل صفحة. المسافة بين الصفحات بالنقط عشان تفضل ثابتة بصرياً
 * مع التكبير.
 */
class PageLayout(
    val slots: List<PageSlot>,
    val contentWidth: Float,
    val contentHeight: Float,
    private val flow: PageFlow = PageFlow.VERTICAL
) {
    fun slotAt(index: Int): PageSlot? = slots.getOrNull(index)

    /**
     * الصفحات المرئية في مستطيل من مساحة المستند.
     *
     * الصفحات مرتبة على محور واحد؛ لذلك لا نمسح مستنداً من مئات الصفحات مع
     * كل إطار لمس. البحث الثنائي يحدد نافذة صغيرة ثم يفحص تقاطعها فقط.
     */
    fun visible(l: Float, t: Float, r: Float, b: Float): List<PageSlot> {
        if (slots.isEmpty()) return emptyList()
        val start = when (flow) {
            PageFlow.VERTICAL -> firstGreaterThan(t) { it.bottom }
            PageFlow.HORIZONTAL -> firstGreaterThan(l) { it.right }
        }
        val end = when (flow) {
            PageFlow.VERTICAL -> firstAtLeast(b) { it.top }
            PageFlow.HORIZONTAL -> firstAtLeast(r) { it.left }
        }
        if (start >= end) return emptyList()
        return slots.subList(start, end).filter { it.intersects(l, t, r, b) }
    }

    /** الصفحة التي يقع فيها مركز الشاشة، مع فحص الجيران فقط بدلاً من مسح كل المستند. */
    fun pageAt(x: Float, y: Float): Int {
        if (slots.isEmpty()) return 0
        val pivot = when (flow) {
            PageFlow.VERTICAL -> firstGreaterThan(y) { it.bottom }
            PageFlow.HORIZONTAL -> firstGreaterThan(x) { it.right }
        }.coerceIn(0, slots.lastIndex)
        val from = (pivot - 1).coerceAtLeast(0)
        val to = (pivot + 1).coerceAtMost(slots.lastIndex)
        for (i in from..to) {
            val slot = slots[i]
            if (x >= slot.left && x <= slot.right && y >= slot.top && y <= slot.bottom) return slot.index
        }
        return slots[pivot].index
    }

    private fun firstAtLeast(value: Float, coordinate: (PageSlot) -> Float): Int {
        var low = 0
        var high = slots.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (coordinate(slots[mid]) < value) low = mid + 1 else high = mid
        }
        return low
    }

    private fun firstGreaterThan(value: Float, coordinate: (PageSlot) -> Float): Int {
        var low = 0
        var high = slots.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (coordinate(slots[mid]) <= value) low = mid + 1 else high = mid
        }
        return low
    }

    companion object {
        const val GAP_PT = 16f

        fun build(sizes: List<SizePt>, mode: ViewMode): PageLayout {
            if (sizes.isEmpty()) return PageLayout(emptyList(), 0f, 0f)
            return when (mode) {
                ViewMode.CONTINUOUS_VERTICAL, ViewMode.SINGLE_PAGE -> stackVertical(sizes)
                ViewMode.CONTINUOUS_HORIZONTAL -> stackHorizontal(sizes)
                ViewMode.DOUBLE_PAGE -> stackSpreads(sizes)
            }
        }

        /** رأسي: الصفحات متمركزة أفقياً على أعرض صفحة. */
        private fun stackVertical(sizes: List<SizePt>): PageLayout {
            val width = sizes.maxOf { it.width }
            var y = 0f
            val slots = sizes.mapIndexed { i, s ->
                val slot = PageSlot(i, (width - s.width) / 2f, y, s)
                y += s.height + GAP_PT
                slot
            }
            return PageLayout(slots, width, (y - GAP_PT).coerceAtLeast(0f), PageFlow.VERTICAL)
        }

        /** أفقي: الصفحات متمركزة رأسياً على أطول صفحة. */
        private fun stackHorizontal(sizes: List<SizePt>): PageLayout {
            val height = sizes.maxOf { it.height }
            var x = 0f
            val slots = sizes.mapIndexed { i, s ->
                val slot = PageSlot(i, x, (height - s.height) / 2f, s)
                x += s.width + GAP_PT
                slot
            }
            return PageLayout(slots, (x - GAP_PT).coerceAtLeast(0f), height, PageFlow.HORIZONTAL)
        }

        /**
         * صفحتين جنب بعض. الصفحة الأولى لوحدها زي الكتاب —
         * الغلاف مالوش صفحة مقابلة.
         */
        private fun stackSpreads(sizes: List<SizePt>): PageLayout {
            // بنبني الفرود الأول ونعرف أعرض واحد، وبعدين نتمركز. الطريقة
            // التانية (نتمركز وإحنا بنبني) بتحتاج نقارن بالـtop عشان نعرف
            // مين مع مين — وده بيفشل أول ما صفحتين في نفس الفرد يبقوا
            // بارتفاعين مختلفين، لأن كل واحدة بتاخد top مختلف.
            data class Spread(val start: Int, val pages: List<SizePt>)

            val spreads = ArrayList<Spread>()
            var i = 0
            while (i < sizes.size) {
                // الغلاف لوحده زي الكتاب المطبوع — مالوش صفحة مقابلة
                val take = if (i == 0) 1 else minOf(2, sizes.size - i)
                spreads += Spread(i, sizes.subList(i, i + take))
                i += take
            }

            val widths = spreads.map { sp ->
                sp.pages.sumOf { it.width.toDouble() }.toFloat() + (sp.pages.size - 1) * GAP_PT
            }
            val maxWidth = widths.maxOrNull() ?: 0f

            val slots = ArrayList<PageSlot>(sizes.size)
            var y = 0f
            spreads.forEachIndexed { index, sp ->
                val spreadHeight = sp.pages.maxOf { it.height }
                var x = (maxWidth - widths[index]) / 2f
                sp.pages.forEachIndexed { k, s ->
                    slots += PageSlot(
                        index = sp.start + k,
                        left = x,
                        top = y + (spreadHeight - s.height) / 2f,
                        size = s
                    )
                    x += s.width + GAP_PT
                }
                y += spreadHeight + GAP_PT
            }
            return PageLayout(slots, maxWidth, (y - GAP_PT).coerceAtLeast(0f), PageFlow.VERTICAL)
        }
    }
}

enum class PageFlow { VERTICAL, HORIZONTAL }
