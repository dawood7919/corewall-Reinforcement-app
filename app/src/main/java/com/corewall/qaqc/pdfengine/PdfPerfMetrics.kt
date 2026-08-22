package com.corewall.qaqc.pdfengine

import kotlin.math.ceil

/**
 * عدادات أداء خفيفة لمحرك PDF. لا تستخدم State ولا تسجل في كل إطار، لذلك
 * يمكن قراءتها من تشخيص debug من دون أن تصبح هي نفسها سبباً للتقطيع.
 */
class PdfPerfMetrics {
    private val renderSamplesMs = LongArray(MAX_SAMPLES)
    private var sampleCount = 0
    private var sampleCursor = 0

    var cacheHits: Long = 0
        private set
    var cacheMisses: Long = 0
        private set
    var cancelledRequests: Long = 0
        private set
    var renderedTiles: Long = 0
        private set

    fun hit() { cacheHits++ }
    fun miss() { cacheMisses++ }
    fun cancelled(count: Int) { cancelledRequests += count.coerceAtLeast(0) }

    fun rendered(elapsedMs: Long) {
        renderedTiles++
        renderSamplesMs[sampleCursor] = elapsedMs.coerceAtLeast(0L)
        sampleCursor = (sampleCursor + 1) % MAX_SAMPLES
        sampleCount = (sampleCount + 1).coerceAtMost(MAX_SAMPLES)
    }

    fun snapshot(cachedTiles: Int, queuedTiles: Int, active: Boolean, bitmapBytes: Long): Snapshot {
        val samples = renderSamplesMs.copyOf(sampleCount).sorted()
        val average = if (samples.isEmpty()) 0L else samples.average().toLong()
        val p95 = if (samples.isEmpty()) 0L else {
            val index = (ceil(samples.size * 0.95).toInt() - 1).coerceIn(0, samples.lastIndex)
            samples[index]
        }
        val total = cacheHits + cacheMisses
        return Snapshot(
            cacheHits = cacheHits,
            cacheMisses = cacheMisses,
            cacheHitRate = if (total == 0L) 0f else cacheHits.toFloat() / total,
            cancelledRequests = cancelledRequests,
            renderedTiles = renderedTiles,
            averageTileMs = average,
            p95TileMs = p95,
            cachedTiles = cachedTiles,
            queuedTiles = queuedTiles,
            activeRender = active,
            bitmapBytes = bitmapBytes
        )
    }

    data class Snapshot(
        val cacheHits: Long,
        val cacheMisses: Long,
        val cacheHitRate: Float,
        val cancelledRequests: Long,
        val renderedTiles: Long,
        val averageTileMs: Long,
        val p95TileMs: Long,
        val cachedTiles: Int,
        val queuedTiles: Int,
        val activeRender: Boolean,
        val bitmapBytes: Long
    )

    private companion object { const val MAX_SAMPLES = 120 }
}
