package com.corewall.qaqc.v2.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import com.corewall.qaqc.pdfengine.PdfDocumentSession
import com.corewall.qaqc.pdfengine.SizePt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

internal data class V2TileKey(val page: Int, val level: Int, val column: Int, val row: Int)

/**
 * مجدول V2: مستهلك واحد بآخر منطقة مرئية أولاً. لا توجد coroutine لكل بلاطة؛
 * المهمة الأصلية الجارية تكمل مرة واحدة ثم تبدأ أحدث بلاطة ذات أولوية.
 */
internal class V2TileScheduler(
    private val session: PdfDocumentSession,
    private val memoryBudgetBytes: Long,
    private val onTileChanged: () -> Unit
) {
    // تغيير الطابور والكاش يرجع دائماً إلى Main بعد انتظار PDFium؛ لهذا لا
    // توجد خريطة LRU مشتركة بين خيطين ولا قفل داخل onDraw.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = object : LinkedHashMap<V2TileKey, Bitmap>(64, 0.75f, true) {}
    private val queue = PriorityQueue<Request>(compareBy<Request> { it.priority }.thenBy { it.key.hashCode() })
    private val queued = HashSet<V2TileKey>()
    private var desired = emptySet<V2TileKey>()
    private var generation = 0L
    private var worker: Job? = null
    private var byteSize = 0L

    fun bitmap(key: V2TileKey): Bitmap? = cache[key]

    fun requestVisible(viewport: WorkspaceViewport, page: Int, size: SizePt) {
        val level = levelFor(viewport.zoom)
        val keys = buildList {
            // مستوى سابق موجود دائماً للتحميل التقدمي، ثم المستوى الحاد المطلوب.
            addAll(tilesFor(viewport.visiblePageRect(), page, size, (level - 1).coerceAtLeast(0)))
            addAll(tilesFor(viewport.visiblePageRect(), page, size, level))
        }.distinct()
        val next = keys.toSet()
        if (next == desired) return

        generation++
        desired = next
        queue.clear()
        queued.clear()
        val centre = viewport.visiblePageRect().centerX() to viewport.visiblePageRect().centerY()
        keys.forEach { key ->
            if (cache.containsKey(key)) return@forEach
            queue += Request(key, distance(key, size, centre), generation)
            queued += key
        }
        evict()
        ensureWorker(size)
    }

    private fun ensureWorker(size: SizePt) {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val request = queue.poll() ?: break
                queued.remove(request.key)
                if (request.generation != generation || request.key !in desired) continue
                val bitmap = render(request.key, size)
                if (bitmap != null && request.generation == generation && request.key in desired) {
                    cache.put(request.key, bitmap)?.let { old ->
                        byteSize -= old.byteCount.toLong()
                        if (!old.isRecycled) old.recycle()
                    }
                    byteSize += bitmap.byteCount.toLong()
                    evict()
                    onTileChanged()
                } else if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            worker = null
            if (queue.isNotEmpty()) ensureWorker(size)
        }
    }

    private suspend fun render(key: V2TileKey, size: SizePt): Bitmap? {
        val scale = scaleFor(key.level)
        val pagePixelsW = ceil(size.width * scale).toInt().coerceAtLeast(1)
        val pagePixelsH = ceil(size.height * scale).toInt().coerceAtLeast(1)
        val originX = key.column * TILE_PX
        val originY = key.row * TILE_PX
        return session.renderTile(
            page = key.page,
            gridWidth = pagePixelsW,
            gridHeight = pagePixelsH,
            originX = originX,
            originY = originY,
            tileWidth = min(TILE_PX, pagePixelsW - originX),
            tileHeight = min(TILE_PX, pagePixelsH - originY)
        )
    }

    fun clear() {
        worker?.cancel()
        worker = null
        queue.clear()
        queued.clear()
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
        desired = emptySet()
        byteSize = 0L
    }

    private fun evict() {
        val iterator = cache.entries.iterator()
        while (byteSize > memoryBudgetBytes && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key in desired) continue
            byteSize -= entry.value.byteCount.toLong()
            if (!entry.value.isRecycled) entry.value.recycle()
            iterator.remove()
        }
    }

    private fun tilesFor(rect: RectF, page: Int, size: SizePt, level: Int): List<V2TileKey> {
        val scale = scaleFor(level)
        val pixelsW = ceil(size.width * scale).toInt()
        val pixelsH = ceil(size.height * scale).toInt()
        val maxCol = ((pixelsW - 1) / TILE_PX).coerceAtLeast(0)
        val maxRow = ((pixelsH - 1) / TILE_PX).coerceAtLeast(0)
        val fromCol = floor(rect.left * scale / TILE_PX).toInt().coerceIn(0, maxCol)
        val toCol = floor(rect.right * scale / TILE_PX).toInt().coerceIn(0, maxCol)
        val fromRow = floor(rect.top * scale / TILE_PX).toInt().coerceIn(0, maxRow)
        val toRow = floor(rect.bottom * scale / TILE_PX).toInt().coerceIn(0, maxRow)
        return buildList {
            for (row in fromRow..toRow) for (column in fromCol..toCol) add(V2TileKey(page, level, column, row))
        }
    }

    private fun distance(key: V2TileKey, size: SizePt, centre: Pair<Float, Float>): Float {
        val scale = scaleFor(key.level)
        val x = (key.column + 0.5f) * TILE_PX / scale
        val y = (key.row + 0.5f) * TILE_PX / scale
        return abs(x - centre.first) + abs(y - centre.second)
    }

    private fun levelFor(zoom: Float): Int = when {
        zoom < 0.75f -> 0
        zoom < 1.5f -> 1
        zoom < 3f -> 2
        else -> 3
    }

    private fun scaleFor(level: Int): Float = when (level.coerceIn(0, 3)) {
        0 -> 0.5f
        1 -> 1f
        2 -> 2f
        else -> 4f
    }

    private data class Request(val key: V2TileKey, val priority: Float, val generation: Long)

    private companion object { const val TILE_PX = 384 }
}
