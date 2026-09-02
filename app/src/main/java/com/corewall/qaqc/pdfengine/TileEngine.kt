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
import java.util.PriorityQueue

/**
 * ذاكرة بلاطات + مجدول رندر أحادي المستهلك.
 *
 * PDFium يرسم مستنداً واحداً على خيط أصلي واحد، لذلك إطلاق 14 coroutine لا
 * يعطي توازياً حقيقياً؛ بل يضع 14 رسمة قديمة أمام ما يراه المستخدم الآن.
 * هذا المجدول يحتفظ بطابور صغير مرتب بالأولوية ويترك مهمة native جارية واحدة
 * فقط. عند تحرك المنظر تُرمى الطلبات المنتظرة فوراً، وبعد انتهاء البلاطة
 * الجارية يبدأ أحدث موضع مرئي. بهذه الطريقة لا يلاحق التكبير طابوراً قديماً.
 */
class TileEngine(
    private val session: PdfDocumentSession,
    maxBytes: Long,
    /**
     * نسخ المستند اللي بيترسم منها. الافتراضي نسخة واحدة = السلوك القديم.
     * [create] بيبني المجمّع المتوازي.
     */
    private val pool: PdfRenderPool? = null
) {
    /** البلاطات الجاهزة فقط؛ Canvas يراقب هذه الخريطة ولا يراقب طابور الرندر. */
    val tiles: SnapshotStateMap<Long, ImageBitmap> = mutableStateMapOf()
    val metrics = PdfPerfMetrics()

    private val bitmaps = HashMap<Long, Bitmap>()
    private val maxTiles: Int = (maxBytes / BYTES_PER_TILE).toInt().coerceIn(MIN_TILES, MAX_TILES)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lru = LinkedHashSet<Long>()
    private val measuring = HashSet<Int>()

    /** كل تغيير حقيقي للمنظر يصدر جيلاً جديداً؛ نتائج الجيل القديم لا تعرض. */
    private var generation = 0L
    private var lastRequest: List<Long> = emptyList()
    private var lastKeys: List<TileKey> = emptyList()
    private var queueLimit = MAX_QUEUED_SHARP
    private var pinned: Set<Long> = emptySet()

    /**
     * البلاطات اللي بتترسم دلوقتي — واحدة لكل عامل.
     *
     * كل الحسابات دي بتحصل على [Dispatchers.Main.immediate]، والعمال
     * بيسلّموا الخيط بس عند `renderTile` اللي بيروح لخيطه الأصلي. يعني
     * الطابور والمجموعات دي مالهاش أقفال حتى مع تعدد العمال.
     */
    private val active = HashSet<Long>()

    /** عدد الرسمات المتوازية = عدد نسخ المستند المتاحة. */
    private val concurrency: Int = (pool?.size ?: 1).coerceAtLeast(1)
    private val workers = arrayOfNulls<Job>(concurrency)

    private val pending = PriorityQueue<TileRequest>(
        compareBy<TileRequest> { it.rank }.thenBy { it.key.packed }
    )
    private val queued = HashSet<Long>()

    /** نبضة وصول/إخلاء، تستخدمها طبقة الطلب لإكمال دفعة محدودة من البلاطات. */
    var renderRevision by mutableIntStateOf(0)
        private set

    /**
     * يقدم بلاطات مطلوبة مرتبة من مركز المنظر إلى حوافه.
     *
     * القائمة الأولى هي المنطقة المرئية، وما يليها prefetch. لا نلغي الرندر
     * الأصلي الجاري لأنه لا يمكن قطعه بأمان وسط PDFium؛ بدلاً من ذلك نلغي
     * **كل ما ينتظره** ونرفض نتيجته إن أصبحت قديمة.
     */
    fun sync(requested: List<TileKey>, maxQueued: Int = MAX_QUEUED_SHARP) {
        val packedRequest = requested.map { it.packed }
        val viewportChanged = packedRequest != lastRequest
        if (viewportChanged) {
            metrics.cancelled(pending.size)
            generation++
            lastRequest = packedRequest
            lastKeys = requested
            pending.clear()
            queued.clear()
        }
        queueLimit = maxQueued
        pinned = packedRequest.toSet()

        refillQueue()
        evict()
        ensureWorker()
    }

    /** يملأ الدفعة التالية من آخر منظر معروف من غير إعادة حساب Compose للمنظر. */
    private fun refillQueue() {
        lastKeys.forEachIndexed { rank, key ->
            val packed = key.packed
            if (tiles.containsKey(packed)) {
                metrics.hit()
                touch(packed)
                return@forEachIndexed
            }
            if (packed in active || packed in queued) return@forEachIndexed
            if (queued.size >= queueLimit) return@forEachIndexed
            enqueue(key, rank)
        }
    }

    private fun enqueue(key: TileKey, rank: Int) {
        val size = session.knownSize(key.page)
        if (size == null) {
            if (measuring.add(key.page)) {
                scope.launch {
                    session.measure(key.page)
                    measuring.remove(key.page)
                }
            }
            return
        }
        val grid = TileGrid(key.page, key.level, size)
        if (key.row !in 0 until grid.rows || key.col !in 0 until grid.cols) return
        queued += key.packed
        metrics.miss()
        pending += TileRequest(key, rank, generation)
    }

    private fun ensureWorker() {
        for (slot in workers.indices) {
            if (workers[slot]?.isActive == true) continue
            if (pending.isEmpty()) return
            workers[slot] = scope.launch { renderLoop(slot) }
        }
    }

    /**
     * عامل واحد: بياخد أعلى طلب في الطابور، يرسمه على نسخته من المستند،
     * ويرجع يجيب اللي بعده.
     *
     * كل عامل بيرسم من نسخة مستند مستقلة، فمفيش تزاحم على PDFium. والعمال
     * كلهم بيقروا ويكتبوا في نفس الطابور من على خيط الواجهة، فمفيش سباق.
     */
    private suspend fun renderLoop(slot: Int) {
        val renderer = pool?.sessionAt(slot) ?: session
        while (true) {
            val next = pending.poll() ?: break
            queued.remove(next.key.packed)
            if (next.generation != generation || next.key.packed in tiles) continue

            val key = next.key
            val size = session.knownSize(key.page) ?: continue
            val grid = TileGrid(key.page, key.level, size)
            active += key.packed
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val bitmap = renderer.renderTile(
                page = key.page,
                gridWidth = grid.pixelWidth,
                gridHeight = grid.pixelHeight,
                originX = key.col * TILE_SIZE,
                originY = key.row * TILE_SIZE,
                tileWidth = grid.tileWidth(key.col),
                tileHeight = grid.tileHeight(key.row)
            )
            active -= key.packed
            metrics.rendered(android.os.SystemClock.elapsedRealtime() - startedAt)

            val stillCurrent = next.generation == generation && key.packed in pinned
            if (bitmap != null && stillCurrent) {
                put(key.packed, bitmap)
            } else {
                bitmap?.takeIf { !it.isRecycled }?.recycle()
            }
        }
        workers[slot] = null
        // لو وصل طلب جديد بين آخر poll وخروج coroutine، مانسيبهوش معلّق.
        if (pending.isNotEmpty()) ensureWorker()
    }

    private fun put(packed: Long, bitmap: Bitmap) {
        bitmaps[packed]?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
        bitmaps[packed] = bitmap
        tiles[packed] = bitmap.asImageBitmap()
        touch(packed)
        // وصول بلاطة لا يغيّر المنظر؛ نكمل الطابور داخلياً بدلاً من إيقاظ
        // snapshotFlow في Canvas وإعادة تكوين قوائم البلاطات والصفحات.
        refillQueue()
        evict()
        renderRevision++
    }

    private fun touch(packed: Long) {
        lru.remove(packed)
        lru.add(packed)
    }

    private fun evict() {
        if (tiles.size <= maxTiles) return
        val iterator = lru.iterator()
        while (tiles.size > maxTiles && iterator.hasNext()) {
            val candidate = iterator.next()
            if (candidate in pinned) continue
            iterator.remove()
            release(candidate)
        }
    }

    /** يحتفظ بالمستوى الحالي والسابق للتحميل الانتقالي، ويرمي ما عداه. */
    fun dropDistantLevels(currentLevel: Int, keep: Int = 1) {
        val doomed = tiles.keys.filter { packed ->
            val level = TileKey(packed).level
            kotlin.math.abs(level - currentLevel) > keep && packed !in pinned
        }
        doomed.forEach(::release)
    }

    fun clear() {
        scope.cancel()
        workers.fill(null)
        pending.clear()
        queued.clear()
        tiles.keys.toList().forEach(::release)
        lru.clear()
        pinned = emptySet()
        active.clear()
        measuring.clear()
        // النسخ الإضافية بتتقفل هنا؛ النسخة الأساسية ملك الشاشة.
        runCatching { pool?.close() }
    }

    /** لقطة تشخيصية تستخدم في debug فقط؛ لا تُندَه من Canvas لكل إطار. */
    fun performanceSnapshot(): PdfPerfMetrics.Snapshot = metrics.snapshot(
        cachedTiles = tiles.size,
        queuedTiles = pending.size,
        active = active.isNotEmpty(),
        bitmapBytes = bitmaps.values.sumOf { it.byteCount.toLong() },
        renderers = concurrency,
        busyRenderers = active.size
    )

    private fun release(packed: Long) {
        tiles.remove(packed)
        bitmaps.remove(packed)?.takeIf { !it.isRecycled }?.recycle()
        lru.remove(packed)
        renderRevision++
    }

    private data class TileRequest(
        val key: TileKey,
        val rank: Int,
        val generation: Long
    )

    companion object {
        private const val BYTES_PER_TILE = TILE_SIZE.toLong() * TILE_SIZE * 4
        private const val MIN_TILES = 24
        private const val MAX_TILES = 320
        private const val MAX_QUEUED_SHARP = 18

        /**
         * المحرك بمجمّع رسم متوازي.
         *
         * فتح النسخ الإضافية بيحصل هنا مش في الشاشة، عشان الشاشة تفضل
         * ماتعرفش حاجة عن التوازي — وعشان القفل يبقى في مكان واحد.
         */
        fun create(context: Context, session: PdfDocumentSession): TileEngine =
            TileEngine(session, budgetFor(context), PdfRenderPool.open(context, session))

        /** ميزانية محددة للمستند، لا تتجاوز ربع heap التطبيق ولا 192MB. */
        fun budgetFor(context: Context): Long {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val heapMb = manager?.memoryClass ?: 128
            val quarter = heapMb.toLong() * 1024 * 1024 / 4
            return quarter.coerceIn(32L * 1024 * 1024, 192L * 1024 * 1024)
        }
    }
}
