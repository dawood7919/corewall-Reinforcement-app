package com.corewall.qaqc

import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffCategory
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffMath
import com.corewall.qaqc.takeoff.TakeoffPoint
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.takeoff.ViewTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حساب الحصر.
 *
 * الاختبارات دي مترجمة مباشرة من قايمة "الأخطاء اللي وقعنا فيها" في
 * مواصفة الويب — كل واحد فيهم بيمسك باج **حصل فعلاً** في النسخة الأصلية.
 */
class TakeoffMathTest {

    /** A1 أفقي بالنقط، معاير على مقياس ١:١٠٠. */
    private val page = PageGeometry(
        widthPt = 2384.0,
        heightPt = 1684.0,
        // ١:١٠٠ → ٢٥.٤/٧٢ مم للنقطة × ١٠٠ = ٣٥.٢٧٨ مم = ٠.٠٣٥٢٧٨ م
        metresPerPoint = (25.4 / 72.0) * 100.0 / 1000.0
    )

    private fun item(
        tool: TakeoffTool,
        verts: List<TakeoffPoint>,
        id: String = "x",
        parentId: String? = null,
        extraRings: List<List<TakeoffPoint>> = emptyList(),
        extraSegments: List<List<TakeoffPoint>> = emptyList(),
        visible: Boolean = true,
        categoryId: String? = "c",
        rateOverride: Double? = null
    ) = TakeoffItem(
        id = id, drawingPath = "d.pdf", page = 1, tool = tool,
        name = "t", categoryId = categoryId, colorArgb = 0, visible = visible,
        verts = verts, extraRings = extraRings, extraSegments = extraSegments,
        parentId = parentId, rateOverride = rateOverride
    )

    /**
     * مربّع منسّب: بيبدأ من ([origin], [origin]) — يعني نفس القيمة في
     * المحورين — وضلعه [side]. فـ`square(0.6, 0.1)` بيغطّي
     * x ∈ [0.6, 0.7] **و** y ∈ [0.6, 0.7].
     */
    private fun square(origin: Double, side: Double) = listOf(
        TakeoffPoint(origin, origin),
        TakeoffPoint(origin + side, origin),
        TakeoffPoint(origin + side, origin + side),
        TakeoffPoint(origin, origin + side)
    )

    // ═════════════════════════════ ١) ثبات القيمة مع التكبير

    @Test
    fun `area is identical across wildly different zoom levels`() {
        // ده اختبار الانحدار الأهم في المواصفة كلها. الطريقة: نمسك
        // **نفس المكان الفيزيائي** على الصفحة، ونحوّله من الشاشة للصفحة
        // عند تكبيرات مختلفة تماماً. النتيجة لازم تطلع واحدة.
        val corners = listOf(
            Pair(0.20, 0.20), Pair(0.60, 0.20), Pair(0.60, 0.50), Pair(0.20, 0.50)
        )
        var reference: Double? = null

        for (zoom in listOf(0.1, 1.0, 5.0, 20.0, 64.0)) {
            val view = ViewTransform(zoom = zoom, offsetX = 37.0 * zoom, offsetY = -11.0 * zoom)
            // نفس النقط، بس محسوبة كأن المستخدم لمسها على الشاشة عند
            // التكبير ده — الرحلة الكاملة شاشة ← صفحة.
            val ring = corners.map { (nx, ny) ->
                val sx = nx * page.widthPt * zoom + view.offsetX
                val sy = ny * page.heightPt * zoom + view.offsetY
                TakeoffMath.screenToPage(sx, sy, page, view)
            }
            val value = TakeoffMath.area(ring, page)
            if (reference == null) reference = value
            assertEquals("zoom $zoom changed the area", reference!!, value, 1e-9)
        }
        assertTrue("area should be a real number", reference!! > 0.0)
    }

    @Test
    fun `screen and page projections round trip`() {
        val view = ViewTransform(zoom = 3.7, offsetX = -412.0, offsetY = 88.0)
        val original = TakeoffPoint(0.317, 0.842)
        val (sx, sy) = TakeoffMath.pageToScreen(original, page, view)
        val back = TakeoffMath.screenToPage(sx, sy, page, view)
        assertEquals(original.x, back.x, 1e-12)
        assertEquals(original.y, back.y, 1e-12)
    }

    // ═════════════════════════════ ٢) صحّة الأرقام نفسها

    @Test
    fun `a known square measures the expected real area`() {
        // نص عرض الصفحة × نص ارتفاعها، على مقياس ١:١٠٠.
        val ring = listOf(
            TakeoffPoint(0.0, 0.0), TakeoffPoint(0.5, 0.0),
            TakeoffPoint(0.5, 0.5), TakeoffPoint(0.0, 0.5)
        )
        val widthM = 0.5 * page.widthPt * page.metresPerPoint
        val heightM = 0.5 * page.heightPt * page.metresPerPoint
        assertEquals(widthM * heightM, TakeoffMath.area(ring, page), 1e-9)
    }

    @Test
    fun `winding direction does not flip the sign`() {
        val clockwise = square(0.1, 0.3)
        val anticlockwise = clockwise.reversed()
        assertEquals(
            TakeoffMath.area(clockwise, page),
            TakeoffMath.area(anticlockwise, page),
            1e-12
        )
    }

    @Test
    fun `length sums its segments and does not close the path`() {
        val line = listOf(
            TakeoffPoint(0.0, 0.0), TakeoffPoint(0.5, 0.0), TakeoffPoint(0.5, 0.5)
        )
        val expected = 0.5 * page.widthPt * page.metresPerPoint +
            0.5 * page.heightPt * page.metresPerPoint
        assertEquals(expected, TakeoffMath.length(line, page), 1e-9)
    }

    @Test
    fun `an uncalibrated page yields zero instead of a wrong number`() {
        // رقم غلط في الحصر أسوأ من مفيش رقم — الرقم الغلط بيتحاسب عليه.
        val blank = PageGeometry(2384.0, 1684.0, 0.0)
        assertEquals(0.0, TakeoffMath.area(square(0.1, 0.4), blank), 0.0)
        assertEquals(0.0, TakeoffMath.length(square(0.1, 0.4), blank), 0.0)
    }

    @Test
    fun `count ignores calibration entirely`() {
        val blank = PageGeometry(2384.0, 1684.0, 0.0)
        val markers = item(TakeoffTool.COUNT, List(7) { TakeoffPoint(it * 0.1, 0.5) })
        assertEquals(7.0, TakeoffMath.grossQuantity(markers, blank), 0.0)
    }

    // ═════════════════════════════ ٣) التجميع (الرسم المستمر)

    @Test
    fun `accumulated rings add up inside one item`() {
        val a = square(0.10, 0.20)
        val b = square(0.50, 0.20)
        val single = item(TakeoffTool.AREA, a)
        val accumulated = item(TakeoffTool.AREA, a, extraRings = listOf(b))

        val one = TakeoffMath.grossQuantity(single, page)
        val two = TakeoffMath.grossQuantity(accumulated, page)
        assertEquals(2.0 * one, two, 1e-9)
    }

    @Test
    fun `disconnected length segments add up`() {
        // وزرة مقطوعة بباب: قطعتين مش متلاصقين، بند واحد.
        val first = listOf(TakeoffPoint(0.0, 0.5), TakeoffPoint(0.2, 0.5))
        val second = listOf(TakeoffPoint(0.6, 0.5), TakeoffPoint(0.9, 0.5))
        val skirting = item(TakeoffTool.LENGTH, first, extraSegments = listOf(second))
        val expected = 0.5 * page.widthPt * page.metresPerPoint // ٠.٢ + ٠.٣ = ٠.٥
        assertEquals(expected, TakeoffMath.grossQuantity(skirting, page), 1e-9)
    }

    // ═════════════════════════════ ٤) الخصومات

    @Test
    fun `deduction nets out of its parent only`() {
        val wall = item(TakeoffTool.AREA, square(0.1, 0.4), id = "wall")
        val other = item(TakeoffTool.AREA, square(0.6, 0.2), id = "other")
        val opening = item(TakeoffTool.DEDUCT, square(0.15, 0.1), id = "door", parentId = "wall")
        val all = listOf(wall, other, opening)

        val gross = TakeoffMath.grossQuantity(wall, page)
        val hole = TakeoffMath.grossQuantity(opening, page)
        assertEquals(gross - hole, TakeoffMath.netQuantity(wall, all, page), 1e-9)

        // الحيطة التانية مالهاش دعوة بالباب ده
        assertEquals(
            TakeoffMath.grossQuantity(other, page),
            TakeoffMath.netQuantity(other, all, page),
            1e-12
        )
    }

    @Test
    fun `hiding a deduction restores the parent to its gross value`() {
        val wall = item(TakeoffTool.AREA, square(0.1, 0.4), id = "wall")
        val hidden = item(
            TakeoffTool.DEDUCT, square(0.15, 0.1),
            id = "door", parentId = "wall", visible = false
        )
        val all = listOf(wall, hidden)
        assertEquals(
            TakeoffMath.grossQuantity(wall, page),
            TakeoffMath.netQuantity(wall, all, page),
            1e-12
        )
    }

    @Test
    fun `several deductions on one parent all come off`() {
        val slab = item(TakeoffTool.AREA, square(0.05, 0.8), id = "slab")
        val holes = (1..3).map {
            item(TakeoffTool.DEDUCT, square(0.1 * it, 0.05), id = "h$it", parentId = "slab")
        }
        val all = listOf(slab) + holes
        val expected = TakeoffMath.grossQuantity(slab, page) -
            holes.sumOf { TakeoffMath.grossQuantity(it, page) }
        assertEquals(expected, TakeoffMath.netQuantity(slab, all, page), 1e-9)
        assertEquals(3, TakeoffMath.deductionsOf(slab, all).size)
    }

    @Test
    fun `deductions do not leak into length or count items`() {
        // خصم على خط مالوش معنى — ومحدش المفروض يقدر يعمله، بس لو حصل
        // بالغلط لازم مايأثرش على الرقم.
        val line = item(TakeoffTool.LENGTH, listOf(TakeoffPoint(0.0, 0.1), TakeoffPoint(0.9, 0.1)), id = "l")
        val stray = item(TakeoffTool.DEDUCT, square(0.2, 0.1), id = "d", parentId = "l")
        val all = listOf(line, stray)
        assertEquals(
            TakeoffMath.grossQuantity(line, page),
            TakeoffMath.netQuantity(line, all, page),
            1e-12
        )
    }

    // ═════════════════════════════ ٥) اختبار اللمس

    @Test
    fun `hit test finds an accumulated ring not just the first one`() {
        // الباج ده لو رجع، تلات أرباع الشكل بتبقى مش قابلة للتحديد.
        val shape = item(TakeoffTool.AREA, square(0.10, 0.10), extraRings = listOf(square(0.60, 0.10)))
        // جوّه الحلقة التانية: [0.60, 0.70] في المحورين.
        val insideExtra = TakeoffPoint(0.65, 0.65)
        assertTrue(TakeoffMath.hitTest(shape, insideExtra, page, tapRadiusPt = 10.0))
    }

    @Test
    fun `hit test misses a point outside every part`() {
        val shape = item(TakeoffTool.AREA, square(0.10, 0.10), extraRings = listOf(square(0.60, 0.10)))
        assertTrue(!TakeoffMath.hitTest(shape, TakeoffPoint(0.40, 0.80), page, 10.0))
    }

    @Test
    fun `length hit test respects the tap radius`() {
        val line = item(TakeoffTool.LENGTH, listOf(TakeoffPoint(0.1, 0.5), TakeoffPoint(0.9, 0.5)))
        // نقطة فوق الخط بشوية — المسافة بالنقط
        val nearY = 0.5 + (5.0 / page.heightPt)
        assertTrue(TakeoffMath.hitTest(line, TakeoffPoint(0.5, nearY), page, tapRadiusPt = 10.0))
        assertTrue(!TakeoffMath.hitTest(line, TakeoffPoint(0.5, nearY), page, tapRadiusPt = 2.0))
    }

    @Test
    fun `distance to a polyline is measured to the segment not its endpoints`() {
        // نقطة فوق نص القطعة: المسافة للقطعة صغيرة، وللطرفين كبيرة.
        // لو الحساب بيقيس للطرفين، اللمس في نص خط طويل بيفشل.
        val line = listOf(TakeoffPoint(0.0, 0.5), TakeoffPoint(1.0, 0.5))
        val above = TakeoffPoint(0.5, 0.5 + 3.0 / page.heightPt)
        assertTrue(TakeoffMath.distanceToPolylinePt(above, line, page) < 4.0)
    }

    @Test
    fun `count hit test uses a real radius around each marker`() {
        val markers = item(TakeoffTool.COUNT, listOf(TakeoffPoint(0.3, 0.3), TakeoffPoint(0.7, 0.7)))
        val nearSecond = TakeoffPoint(0.7 + 2.0 / page.widthPt, 0.7)
        assertTrue(TakeoffMath.hitTest(markers, nearSecond, page, tapRadiusPt = 12.0))
        assertTrue(!TakeoffMath.hitTest(markers, TakeoffPoint(0.5, 0.5), page, tapRadiusPt = 12.0))
    }

    @Test
    fun `point in ring handles a concave L shape`() {
        // الأوضة على شكل L هي الحالة اللي بتكسر الخوارزميات الساذجة.
        val lShape = listOf(
            TakeoffPoint(0.1, 0.1), TakeoffPoint(0.5, 0.1), TakeoffPoint(0.5, 0.3),
            TakeoffPoint(0.3, 0.3), TakeoffPoint(0.3, 0.5), TakeoffPoint(0.1, 0.5)
        )
        assertTrue(TakeoffMath.pointInRing(TakeoffPoint(0.2, 0.2), lShape))   // في الذراع
        assertTrue(TakeoffMath.pointInRing(TakeoffPoint(0.4, 0.2), lShape))   // في الذراع التاني
        assertTrue(!TakeoffMath.pointInRing(TakeoffPoint(0.4, 0.4), lShape))  // في الفراغ جوّه الـL
    }

    // ═════════════════════════════ ٦) تعديل الرؤوس

    @Test
    fun `nearest vertex finds the closest corner within the tap radius`() {
        val shape = item(TakeoffTool.AREA, square(0.2, 0.3))
        // الرأس التاني هو (0.5, 0.2) — قريب منها.
        val near = TakeoffPoint(0.5 + 3.0 / page.widthPt, 0.2)
        assertEquals(1, TakeoffMath.nearestVertexIndex(shape, near, page, tapRadiusPt = 10.0))
    }

    @Test
    fun `nearest vertex returns null outside the tap radius`() {
        val shape = item(TakeoffTool.AREA, square(0.2, 0.3))
        assertEquals(null, TakeoffMath.nearestVertexIndex(shape, TakeoffPoint(0.35, 0.35), page, tapRadiusPt = 10.0))
    }

    // ═════════════════════════════ ٧) تحديد بمستطيل

    @Test
    fun `fully inside requires every vertex not just one`() {
        val shape = item(TakeoffTool.AREA, square(0.2, 0.2)) // [0.2,0.4] × [0.2,0.4]
        assertTrue(TakeoffMath.fullyInside(shape, TakeoffPoint(0.1, 0.1), TakeoffPoint(0.5, 0.5)))
        // نفس المستطيل بس مقطوع نص الشكل — رأس واحد بره يكفي يرفض التحديد.
        assertTrue(!TakeoffMath.fullyInside(shape, TakeoffPoint(0.1, 0.1), TakeoffPoint(0.3, 0.5)))
    }

    // ═════════════════════════════ ٨) التكلفة (سعر × هالك)

    @Test
    fun `waste percent inflates the ordered quantity but not the stored net`() {
        val wall = item(TakeoffTool.AREA, square(0.1, 0.4), categoryId = "concrete")
        val categories = listOf(TakeoffCategory("concrete", "خرسانة", 0, wastePct = 10.0))
        val net = TakeoffMath.netQuantity(wall, listOf(wall), page)
        val ordered = TakeoffMath.quantityWithWaste(wall, listOf(wall), page, categories)
        assertEquals(net * 1.10, ordered, 1e-9)
    }

    @Test
    fun `count items never carry waste`() {
        val markers = item(TakeoffTool.COUNT, List(4) { TakeoffPoint(it * 0.1, 0.5) }, categoryId = "doors")
        val categories = listOf(TakeoffCategory("doors", "أبواب", 0, wastePct = 15.0))
        assertEquals(4.0, TakeoffMath.quantityWithWaste(markers, listOf(markers), page, categories), 0.0)
    }

    @Test
    fun `item rate override wins over the category default`() {
        val wall = item(TakeoffTool.AREA, square(0.1, 0.2), categoryId = "concrete", rateOverride = 500.0)
        val categories = listOf(TakeoffCategory("concrete", "خرسانة", 0, rate = 300.0))
        assertEquals(500.0, TakeoffMath.rateFor(wall, categories), 0.0)
    }

    @Test
    fun `cost is zero for an unpriced category`() {
        val wall = item(TakeoffTool.AREA, square(0.1, 0.2), categoryId = "concrete")
        val categories = listOf(TakeoffCategory("concrete", "خرسانة", 0))
        assertEquals(0.0, TakeoffMath.costOf(wall, listOf(wall), page, categories), 0.0)
    }
}
