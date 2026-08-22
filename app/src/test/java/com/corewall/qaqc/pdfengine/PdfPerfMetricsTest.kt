package com.corewall.qaqc.pdfengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPerfMetricsTest {

    @Test
    fun snapshot_reportsCacheAndRenderStatistics() {
        val metrics = PdfPerfMetrics()
        repeat(9) { metrics.hit() }
        metrics.miss()
        metrics.cancelled(3)
        listOf(4L, 8L, 12L, 16L, 20L).forEach(metrics::rendered)

        val snapshot = metrics.snapshot(
            cachedTiles = 12,
            queuedTiles = 2,
            active = true,
            bitmapBytes = 4_194_304L
        )

        assertEquals(9, snapshot.cacheHits)
        assertEquals(1, snapshot.cacheMisses)
        assertEquals(3, snapshot.cancelledRequests)
        assertEquals(5, snapshot.renderedTiles)
        assertEquals(12, snapshot.averageTileMs)
        assertEquals(20, snapshot.p95TileMs)
        assertTrue(snapshot.cacheHitRate > 0.89f)
        assertEquals(4_194_304L, snapshot.bitmapBytes)
    }
}
