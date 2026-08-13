package com.corewall.qaqc.pdfengine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * كاش المربّعات وطابور الرندر.
 *
 * ثلاث مسؤوليات، وكلها لازم تشتغل من غير ما تلمس خيط الواجهة:
 *
 * 1. **الكاش** — `SnapshotStateMap` عشان Compose يعيد التركيب لما مربّع
 *    يوصل. المهم إن وصول مربّع بيعيد تركيب **المربّع ده بس**، مش الصفحة
 *    كلها ولا الشاشة — علشان كده المفتاح `Long` مضغوط والخريطة مراقَبة.
 * 2. **الطابور** — كل مربّع مطلوب بياخد `Job`. المربّع اللي خرج من المنطقة
 *    المرئية **بيتلغي فوراً**؛ من غير كده، تمريرة سريعة في مستند كبير
 *    بتسيب طابور بمئات المربّعات اللي محدش هيشوفها، والمربّع اللي انت
 *    باصص عليه بيستنى وراهم.
 * 3. **الإخلاء** — LRU بسقف محسوب من ذاكرة الجهاز.
 */
class TileEngine(
    private val session: PdfDocumentSession,
    maxBytes: Long
) {
    /** المربّعات الجاهزة. Compose بيراقبها. */
    val tiles: SnapshotStateMap<Long, ImageBitmap> = mutableStateMapOf()

    /** الـImageBitmap لا يحرر الـBitmap الأصلي تلقائياً عند إخلاء الكاش. */
    private val bitmaps = HashMap<Long, Bitmap>()

    /** نبضة لوصول أو إخراج بلاطة؛ تتيح ملء طابور محدود تدريجياً. */
    var renderRevision by mutableIntStateOf(0)
        private set

    /** أقصى عدد مربّعات في الذاكرة. ARGB_8888 512×512 = ١ ميجا للمربّع. */
    private val maxTiles: Int = (maxBytes / BYTES_PER_TILE).toInt().coerceIn(24, 320)

    /**
     * كل الدفاتر (الطابور والـLRU والخريطة) بتتعدّل على **خيط الواجهة بس**.
     *
     * ده مش تفضيل: `sync` بتتنده من التركيب على الخيط الرئيسي، والرندر
     * بيخلص على خيط PDFium. لو الاتنين كتبوا في نفس الـHashMap، ده تلف
     * ذاكرة صامت — بيظهر كمربّعات بتختفي أو حلقة لا نهائية جوّه HashMap
     * وقت التمرير، وبيبقى مستحيل تلاقيه بعدين.
     *
     * الرندر نفسه بيقفز لخيط PDFium جوّه [PdfDocumentSession.renderTile]
     * ويرجع، فالخيط الرئيسي مابيتحبسش ولا لحظة.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val inFlight = HashMap<Long, Job>()

    /** ترتيب الاستخدام — الأقدم في الأول. */
    private val lru = LinkedHashSet<Long>()

    /** المربّعات المرئية دلوقتي — ممنوع تتخلص خالص. */
    private var pinned: Set<Long> = emptySet()

    private val measuring = HashSet<Int>()

    /**
     * بيقول للمحرّك: دي المربّعات المطلوبة دلوقتي، بالترتيب (الأهم الأول).
     *
     * بيتنده من الواجهة عند كل تغيّر في المنطقة المرئية. رخيص للاستدعاء
     * المتكرر — بيلغي القديم وبيطلب الناقص بس.
     */
    fun sync(requested: List<TileKey>, maxQueued: Int = MAX_QUEUED_SHARP) {
        val wanted = LinkedHashSet<Long>(requested.size)
        requested.forEach { wanted += it.packed }
        pinned = wanted

        // ١) إلغاء اللي خرج من المشهد
        val stale = inFlight.keys.filter { it !in wanted }
        stale.forEach { key ->
            inFlight.remove(key)?.cancel()
        }

        // ٢) طلب الناقص، بالترتيب المطلوب (المركز الأول)
        for (key in requested) {
            val packed = key.packed
            if (tiles.containsKey(packed)) { touch(packed); continue }
            if (inFlight.containsKey(packed)) continue
            if (inFlight.size >= maxQueued) break
            schedule(key)
        }

        evict()
    }

    private fun schedule(key: TileKey) {
        val page = key.page

        // الصفحة اللي مقاسها لسه تقدير مابنرسمهاش: هنرسم بمقاس غلط وبعدين
        // نرمي الشغل. بنقيسها الأول، والواجهة بتعيد الطلب لما المقاس يوصل.
        val size = session.knownSize(page)
        if (size == null) {
            if (measuring.add(page)) {
                scope.launch {
                    session.measure(page)
                    measuring.remove(page)
                }
            }
            return
        }

        val grid = TileGrid(page, key.level, size)
        if (key.row >= grid.rows || key.col >= grid.cols) return

        val job = scope.launch {
            val bmp = session.renderTile(
                page = page,
                gridWidth = grid.pixelWidth,
                gridHeight = grid.pixelHeight,
                originX = key.col * TILE_SIZE,
                originY = key.row * TILE_SIZE,
                tileWidth = grid.tileWidth(key.col),
                tileHeight = grid.tileHeight(key.row)
            )
            inFlight.remove(key.packed)
            if (bmp != null && key.packed in pinned) {
                bitmaps[key.packed]?.takeIf { it !== bmp && !it.isRecycled }?.recycle()
                bitmaps[key.packed] = bmp
                tiles[key.packed] = bmp.asImageBitmap()
                touch(key.packed)
                evict()
                renderRevision++
            } else {
                bmp?.recycle()
            }
        }
        inFlight[key.packed] = job
    }

    private fun touch(packed: Long) {
        lru.remove(packed)
        lru.add(packed)
    }

    private fun evict() {
        if (tiles.size <= maxTiles) return
        val it = lru.iterator()
        while (tiles.size > maxTiles && it.hasNext()) {
            val candidate = it.next()
            // المربّعات المرئية محميّة — إخلاؤها معناه فراغ أبيض في الشاشة
            if (candidate in pinned) continue
            it.remove()
            release(candidate)
        }
    }

    /**
     * بيرمي كل مستويات التكبير البعيدة عن المستوى الحالي.
     *
     * بيتنده بعد ما التكبير يستقر. من غيره، رحلة من ١× لـ٦٤× ورجوع بتسيب
     * ٩ مستويات كاملة في الذاكرة — وأول ٨ منهم محدش هيشوفهم تاني.
     */
    fun dropDistantLevels(currentLevel: Int, keep: Int = 2) {
        val doomed = tiles.keys.filter { packed ->
            val level = TileKey(packed).level
            kotlin.math.abs(level - currentLevel) > keep && packed !in pinned
        }
        doomed.forEach(::release)
    }

    /** بيتنده لما الشاشة تتقفل — بيلغي الطابور ويفضّي الذاكرة. */
    fun clear() {
        scope.cancel()
        inFlight.clear()
        tiles.keys.toList().forEach(::release)
        lru.clear()
        pinned = emptySet()
        measuring.clear()
    }

    private fun release(packed: Long) {
        tiles.remove(packed)
        bitmaps.remove(packed)?.takeIf { !it.isRecycled }?.recycle()
        lru.remove(packed)
        renderRevision++
    }

    companion object {
        private const val BYTES_PER_TILE = TILE_SIZE.toLong() * TILE_SIZE * 4
        /** PDFium يتعامل مع المستند بخيط واحد؛ صف قصير يمنع المربعات القديمة من حجب موضع المستخدم. */
        private const val MAX_QUEUED_SHARP = 14

        /**
         * ميزانية الذاكرة — ربع اللي التطبيق مسموح له بيه، بين ٣٢ و١٩٢ ميجا.
         *
         * الربع مش رقم عشوائي: التطبيق ده شايل كمان مسقط تفاعلي وصور موقع
         * وقاعدة بيانات. عارض PDF بياخد نص الذاكرة بيقتل باقي التطبيق.
         */
        fun budgetFor(context: Context): Long {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val heapMb = am?.memoryClass ?: 128
            val quarter = heapMb.toLong() * 1024 * 1024 / 4
            return quarter.coerceIn(32L * 1024 * 1024, 192L * 1024 * 1024)
        }
    }
}
