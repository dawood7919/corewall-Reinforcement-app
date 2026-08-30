package com.corewall.qaqc

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.corewall.qaqc.ui.pdf.BoxHandle
import com.corewall.qaqc.ui.pdf.PdfTool
import com.corewall.qaqc.ui.pdf.annotationBounds
import com.corewall.qaqc.ui.pdf.annotationHit
import com.corewall.qaqc.ui.pdf.applyTo
import com.corewall.qaqc.ui.pdf.handleAt
import com.corewall.qaqc.ui.pdf.rectOf
import com.corewall.qaqc.ui.pdf.touches
import com.corewall.qaqc.ui.pdf.transformPoints
import com.corewall.qaqc.ui.pdf.unionBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تحديد الأشكال وتعديلها.
 *
 * الهندسة دي هي اللي بتقرّر "إيه اللي اتمسك لما دُست هنا" و"الشكل راح
 * فين لما شديت الركن" — وغلطة فيها مابتوقّعش البناء، بتخلّي التحديد
 * يمسك حاجة تانية أو الشكل يقفز. النوع ده من الغلط بيتكتشف بالإيد بعد
 * ما يوصل للموقع، فبيتثبّت هنا.
 */
class PdfObjectEditTest {

    private val square = listOf(0.2f, 0.2f, 0.6f, 0.6f)

    @Test
    fun `bounds cover every point`() {
        val b = annotationBounds(listOf(0.3f, 0.8f, 0.1f, 0.2f, 0.5f, 0.4f))
        assertNotNull(b)
        assertEquals(0.1f, b!!.left, 1e-6f)
        assertEquals(0.2f, b.top, 1e-6f)
        assertEquals(0.5f, b.right, 1e-6f)
        assertEquals(0.8f, b.bottom, 1e-6f)
    }

    @Test
    fun `bounds of a half point is null`() {
        assertNull(annotationBounds(listOf(0.3f)))
        assertNull(annotationBounds(emptyList()))
    }

    @Test
    fun `union covers both boxes`() {
        val u = unionBounds(listOf(Rect(0.1f, 0.1f, 0.2f, 0.2f), Rect(0.5f, 0.4f, 0.7f, 0.9f)))
        assertEquals(Rect(0.1f, 0.1f, 0.7f, 0.9f), u)
        assertNull(unionBounds(emptyList()))
    }

    /**
     * الفرق اللي خلّى التحديد قابل للاستعمال: مستطيل فاضي كبير لو أخد كل
     * نقرة جوّاه، أي شكل تحته يبقى مستحيل تختاره.
     */
    @Test
    fun `an outline is caught at its edge, not in its middle`() {
        assertTrue(annotationHit(PdfTool.RECT, square, 0.2f, 0.4f, 0.02f))
        assertFalse(annotationHit(PdfTool.RECT, square, 0.4f, 0.4f, 0.02f))
    }

    @Test
    fun `a filled shape is caught anywhere inside`() {
        assertTrue(annotationHit(PdfTool.HIGHLIGHT, square, 0.4f, 0.4f, 0.02f))
    }

    @Test
    fun `a stroke is caught near the line only`() {
        val diagonal = listOf(0.1f, 0.1f, 0.9f, 0.9f)
        assertTrue(annotationHit(PdfTool.PEN, diagonal, 0.5f, 0.51f, 0.03f))
        // نفس الصندوق الحاوي، بس بعيد عن الخط نفسه.
        assertFalse(annotationHit(PdfTool.PEN, diagonal, 0.15f, 0.85f, 0.03f))
    }

    @Test
    fun `a tap outside the grown bounds misses`() {
        assertFalse(annotationHit(PdfTool.RECT, square, 0.05f, 0.05f, 0.02f))
    }

    @Test
    fun `transform maps corners onto the new box`() {
        val moved = transformPoints(square, Rect(0.2f, 0.2f, 0.6f, 0.6f), Rect(0.0f, 0.0f, 0.8f, 0.4f))
        assertEquals(0.0f, moved[0], 1e-5f)
        assertEquals(0.0f, moved[1], 1e-5f)
        assertEquals(0.8f, moved[2], 1e-5f)
        assertEquals(0.4f, moved[3], 1e-5f)
    }

    /** خط أفقي تماماً ارتفاع صندوقه صفر — القسمة عليه بتطلّع NaN. */
    @Test
    fun `a flat shape slides instead of exploding`() {
        val flat = listOf(0.1f, 0.5f, 0.9f, 0.5f)
        val moved = transformPoints(flat, Rect(0.1f, 0.5f, 0.9f, 0.5f), Rect(0.1f, 0.7f, 0.9f, 0.7f))
        assertTrue(moved.none { it.isNaN() })
        assertEquals(0.7f, moved[1], 1e-5f)
        assertEquals(0.7f, moved[3], 1e-5f)
    }

    @Test
    fun `dragging a corner past the opposite one does not flip the box`() {
        val box = Rect(0.2f, 0.2f, 0.6f, 0.6f)
        val pulled = BoxHandle.BOTTOM_RIGHT.applyTo(box, -0.9f, -0.9f)
        assertTrue(pulled.right > pulled.left)
        assertTrue(pulled.bottom > pulled.top)
    }

    @Test
    fun `moving keeps the box on the page and keeps its size`() {
        val box = Rect(0.6f, 0.6f, 0.9f, 0.9f)
        val moved = BoxHandle.MOVE.applyTo(box, 0.5f, 0.5f)
        assertEquals(box.width, moved.width, 1e-5f)
        assertEquals(box.height, moved.height, 1e-5f)
        assertTrue(moved.right <= 1f + 1e-5f)
        assertTrue(moved.bottom <= 1f + 1e-5f)
    }

    @Test
    fun `corners win over the middle when grabbing`() {
        val box = Rect(0.2f, 0.2f, 0.6f, 0.6f)
        assertEquals(BoxHandle.TOP_LEFT, handleAt(box, 0.21f, 0.21f, 0.03f))
        assertEquals(BoxHandle.BOTTOM_RIGHT, handleAt(box, 0.59f, 0.59f, 0.03f))
        assertEquals(BoxHandle.MOVE, handleAt(box, 0.4f, 0.4f, 0.03f))
        assertNull(handleAt(box, 0.9f, 0.9f, 0.03f))
    }

    @Test
    fun `marquee takes anything it overlaps`() {
        val marquee = Rect(0.0f, 0.0f, 0.3f, 0.3f)
        assertTrue(Rect(0.25f, 0.25f, 0.9f, 0.9f).touches(marquee))
        assertFalse(Rect(0.4f, 0.4f, 0.9f, 0.9f).touches(marquee))
    }

    @Test
    fun `a marquee dragged up and left is still a valid rect`() {
        val r = rectOf(Offset(0.8f, 0.9f), Offset(0.2f, 0.1f))
        assertEquals(Rect(0.2f, 0.1f, 0.8f, 0.9f), r)
    }
}
