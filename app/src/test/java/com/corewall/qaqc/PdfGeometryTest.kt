package com.corewall.qaqc

import com.corewall.qaqc.pdfengine.SizePt
import com.corewall.qaqc.pdfengine.TILE_SIZE
import com.corewall.qaqc.pdfengine.TileGrid
import com.corewall.qaqc.pdfengine.TileKey
import com.corewall.qaqc.pdfengine.ZoomLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * هندسة المحرّك — الاختبارات اللي بتحمي تحسين الرسم.
 *
 * الرسم بقى بيقصّ على المربّعات المرئية بدل ما يلفّ الشبكة كلها. القصّ ده
 * حسابات حدود، وأي غلط فيه بيظهر كشريط فاضي على حرف الشاشة — حاجة سهل
 * ماتلاحظهاش في لقطة وبتضايق كل يوم. الاختبارات دي بتثبّت الحساب.
 */
class PdfGeometryTest {

    // ────────────────────────────────────────────────── سلّم التكبير

    @Test
    fun `zoom level never scales an image up`() {
        // القاعدة الأساسية: المستوى المختار لازم يبقى ≥ التكبير المطلوب،
        // عشان نصغّر بدل ما نكبّر. لو انكسرت، الرسمة بتطلع ضبابية.
        var zoom = 0.1f
        while (zoom <= ZoomLadder.MAX_ZOOM) {
            val level = ZoomLadder.levelFor(zoom)
            assertTrue(
                "level $level scale ${ZoomLadder.scaleOf(level)} < zoom $zoom",
                ZoomLadder.scaleOf(level) >= zoom - 1e-4f || level == ZoomLadder.MAX_LEVEL
            )
            zoom *= 1.17f
        }
    }

    @Test
    fun `zoom level is clamped to the ladder`() {
        assertEquals(ZoomLadder.MIN_LEVEL, ZoomLadder.levelFor(0.001f))
        assertEquals(ZoomLadder.MAX_LEVEL, ZoomLadder.levelFor(9999f))
    }

    // ────────────────────────────────────────────────── مفتاح المربّع

    @Test
    fun `tile key round trips through the packed long`() {
        val key = TileKey.of(page = 731, level = 7, row = 40, col = 19)
        assertEquals(731, key.page)
        assertEquals(7, key.level)
        assertEquals(40, key.row)
        assertEquals(19, key.col)
    }

    // ────────────────────────────────────────────────── شبكة الصفحة

    @Test
    fun `grid covers the whole page with no gap and no overhang`() {
        val a1 = SizePt(1684f, 2384f)
        for (level in ZoomLadder.MIN_LEVEL..ZoomLadder.MAX_LEVEL) {
            val grid = TileGrid(0, level, a1)
            // مجموع عروض المربّعات = عرض الصفحة بالبكسل بالظبط.
            val width = (0 until grid.cols).sumOf { grid.tileWidth(it) }
            val height = (0 until grid.rows).sumOf { grid.tileHeight(it) }
            assertEquals("level $level width", grid.pixelWidth, width)
            assertEquals("level $level height", grid.pixelHeight, height)
        }
    }

    @Test
    fun `edge tiles are clipped not full size`() {
        // صفحة عرضها مربّع ونص: العمود الأخير لازم يبقى نص مربّع.
        val page = SizePt(TILE_SIZE * 1.5f, TILE_SIZE.toFloat())
        val grid = TileGrid(0, level = 2, sizePt = page) // scale = 1.0
        assertEquals(2, grid.cols)
        assertEquals(TILE_SIZE, grid.tileWidth(0))
        assertEquals(TILE_SIZE / 2, grid.tileWidth(1))
    }

    // ────────────────────────────────────────────── القصّ للمنطقة المرئية

    @Test
    fun `tilesIn returns only tiles that intersect the rect plus padding`() {
        val page = SizePt(4096f, 4096f)
        val grid = TileGrid(0, level = 2, sizePt = page) // scale 1.0 → 8×8 tiles

        // نافذة على مربّع واحد بالظبط (row 3, col 3) من غير هامش
        val keys = grid.tilesIn(
            leftPt = 3f * TILE_SIZE + 10f,
            topPt = 3f * TILE_SIZE + 10f,
            rightPt = 3f * TILE_SIZE + 20f,
            bottomPt = 3f * TILE_SIZE + 20f,
            padding = 0
        )
        assertEquals(1, keys.size)
        assertEquals(3, keys[0].row)
        assertEquals(3, keys[0].col)
    }

    @Test
    fun `tilesIn clamps to the grid instead of returning out of range tiles`() {
        val page = SizePt(1024f, 1024f)
        val grid = TileGrid(0, level = 2, sizePt = page) // 2×2 tiles

        val keys = grid.tilesIn(-9999f, -9999f, 9999f, 9999f, padding = 4)
        assertEquals(4, keys.size)
        assertTrue(keys.all { it.row in 0 until grid.rows && it.col in 0 until grid.cols })
    }

    @Test
    fun `visible window stays a small fraction of a deeply zoomed grid`() {
        // ده جوهر التحسين: على A1 عند ٦٤× الشبكة بآلاف المربّعات، بس
        // اللي بيتقاطع مع الشاشة عشرات قليلة. لو الرقم ده كبر فجأة يبقى
        // القصّ اتكسر ورجعنا نلفّ الشبكة كلها كل إطار.
        val a1 = SizePt(1684f, 2384f)
        val level = ZoomLadder.MAX_LEVEL
        val grid = TileGrid(0, level, a1)
        val total = grid.rows * grid.cols
        assertTrue("grid should be large at 64×, was $total", total > 1000)

        // شاشة ١٠٨٠×٢٤٠٠ بكسل عند نفس المستوى = المساحة دي بالنقط
        val scale = ZoomLadder.scaleOf(level)
        val visible = grid.tilesIn(
            leftPt = 500f,
            topPt = 900f,
            rightPt = 500f + 1080f / scale,
            bottomPt = 900f + 2400f / scale,
            padding = 1
        )
        assertTrue("visible tiles ${visible.size} should be far below $total", visible.size < 60)
    }
}
