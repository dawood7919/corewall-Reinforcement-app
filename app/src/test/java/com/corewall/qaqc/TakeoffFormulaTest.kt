package com.corewall.qaqc

import com.corewall.qaqc.takeoff.PageGeometry
import com.corewall.qaqc.takeoff.TakeoffFormula
import com.corewall.qaqc.takeoff.TakeoffFormulaEngine
import com.corewall.qaqc.takeoff.TakeoffItem
import com.corewall.qaqc.takeoff.TakeoffPoint
import com.corewall.qaqc.takeoff.TakeoffTool
import com.corewall.qaqc.takeoff.takeoffSlug
import com.corewall.qaqc.takeoff.takeoffUniqueToken
import com.corewall.qaqc.takeoff.takeoffVarPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** محرّك الصيغ — التحليل النزولي التكراري وربط المراجع بأرقام لايف. */
class TakeoffFormulaTest {

    private val page = PageGeometry(
        widthPt = 2384.0, heightPt = 1684.0,
        metresPerPoint = (25.4 / 72.0) * 100.0 / 1000.0
    )
    private val geometry: (Int) -> PageGeometry = { page }

    private fun square(origin: Double, side: Double) = listOf(
        TakeoffPoint(origin, origin), TakeoffPoint(origin + side, origin),
        TakeoffPoint(origin + side, origin + side), TakeoffPoint(origin, origin + side)
    )

    private fun areaItem(id: String, side: Double, page: Int = 0) = TakeoffItem(
        id = id, drawingPath = "d.pdf", page = page, tool = TakeoffTool.AREA,
        name = id, categoryId = null, colorArgb = 0, verts = square(0.1, side)
    )

    private fun formula(expr: String, refs: Map<String, String> = emptyMap(), roundTo: Int = 2) =
        TakeoffFormula(id = "f", name = "f", expr = expr, colorArgb = 0, refs = refs, roundTo = roundTo)

    // ═════════════════════════════ الحساب الأساسي

    @Test
    fun `respects operator precedence`() {
        val r = TakeoffFormulaEngine.evaluate(formula("2 + 3 * 4", roundTo = -1), emptyList(), geometry)
        assertEquals(14.0, r.value!!, 1e-9)
        assertNull(r.error)
    }

    @Test
    fun `parentheses override precedence`() {
        val r = TakeoffFormulaEngine.evaluate(formula("(2 + 3) * 4", roundTo = -1), emptyList(), geometry)
        assertEquals(20.0, r.value!!, 1e-9)
    }

    @Test
    fun `unary minus binds tighter than addition`() {
        val r = TakeoffFormulaEngine.evaluate(formula("-5 + 3", roundTo = -1), emptyList(), geometry)
        assertEquals(-2.0, r.value!!, 1e-9)
    }

    @Test
    fun `division by zero is a reported error not a crash`() {
        val r = TakeoffFormulaEngine.evaluate(formula("1 / 0", roundTo = -1), emptyList(), geometry)
        assertNull(r.value)
        assertTrue(r.error!!.isNotBlank())
    }

    // ═════════════════════════════ الدوال

    @Test
    fun `functions compute the expected values`() {
        assertEquals(3.0, TakeoffFormulaEngine.evaluate(formula("ABS(-3)", roundTo = -1), emptyList(), geometry).value!!, 1e-9)
        assertEquals(2.0, TakeoffFormulaEngine.evaluate(formula("MIN(2,5,9)", roundTo = -1), emptyList(), geometry).value!!, 1e-9)
        assertEquals(9.0, TakeoffFormulaEngine.evaluate(formula("MAX(2,5,9)", roundTo = -1), emptyList(), geometry).value!!, 1e-9)
        assertEquals(4.0, TakeoffFormulaEngine.evaluate(formula("SQRT(16)", roundTo = -1), emptyList(), geometry).value!!, 1e-9)
        assertEquals(3.0, TakeoffFormulaEngine.evaluate(formula("CEIL(2.1)", roundTo = -1), emptyList(), geometry).value!!, 1e-9)
        assertEquals(2.0, TakeoffFormulaEngine.evaluate(formula("FLOOR(2.9)", roundTo = -1), emptyList(), geometry).value!!, 1e-9)
        assertEquals(3.14, TakeoffFormulaEngine.evaluate(formula("ROUND(3.14159, 2)", roundTo = -1), emptyList(), geometry).value!!, 1e-9)
    }

    @Test
    fun `unknown function name is a reported error`() {
        val r = TakeoffFormulaEngine.evaluate(formula("NOPE(1)", roundTo = -1), emptyList(), geometry)
        assertNull(r.value)
        assertTrue(r.error!!.isNotBlank())
    }

    // ═════════════════════════════ المراجع — الجسر مع البنود الحقيقية

    @Test
    fun `a reference resolves to its item's live net quantity`() {
        val slab = areaItem("s1", side = 0.4)
        val items = listOf(slab)
        val f = formula("slab_area * 2", refs = mapOf("slab_area" to "s1"), roundTo = -1)
        val expected = com.corewall.qaqc.takeoff.TakeoffMath.netQuantity(slab, items, page) * 2
        val r = TakeoffFormulaEngine.evaluate(f, items, geometry)
        assertEquals(expected, r.value!!, 1e-9)
    }

    @Test
    fun `each reference is evaluated against its own item's page`() {
        // بندين على صفحتين مختلفتين بمعايرة مختلفة — كل مرجع لازم ياخد
        // هندسة صفحته هو، مش هندسة صفحة واحدة تتفرض على الكل.
        val a = areaItem("a", side = 0.2, page = 0)
        val b = areaItem("b", side = 0.2, page = 1)
        val items = listOf(a, b)
        val pageA = page
        val pageB = page.copy(metresPerPoint = page.metresPerPoint * 2.0)
        val byPage: (Int) -> PageGeometry = { if (it == 0) pageA else pageB }
        val f = formula("a_area + b_area", refs = mapOf("a_area" to "a", "b_area" to "b"), roundTo = -1)
        val expected = com.corewall.qaqc.takeoff.TakeoffMath.netQuantity(a, items, pageA) +
            com.corewall.qaqc.takeoff.TakeoffMath.netQuantity(b, items, pageB)
        val r = TakeoffFormulaEngine.evaluate(f, items, byPage)
        assertEquals(expected, r.value!!, 1e-9)
    }

    @Test
    fun `a reference to a deleted item reports REF error instead of crashing`() {
        val f = formula("ghost * 2", refs = mapOf("ghost" to "gone"))
        val r = TakeoffFormulaEngine.evaluate(f, emptyList(), geometry)
        assertNull(r.value)
        assertTrue(r.error!!.contains("REF"))
    }

    @Test
    fun `a token used in the expression but missing from refs is an unknown reference`() {
        val f = formula("typo_name * 2", refs = emptyMap())
        val r = TakeoffFormulaEngine.evaluate(f, emptyList(), geometry)
        assertNull(r.value)
        assertTrue(r.error!!.isNotBlank())
    }

    @Test
    fun `renaming the underlying item does not break the formula`() {
        // الربط بـid مش بالاسم — التوكن في نص الصيغة ممكن يفضل زي ما هو
        // حتى لو اسم البند اتغيّر، لأن refs بيربط بالـid الثابت.
        val slab = areaItem("s1", side = 0.3).copy(name = "بلاطة اتسمّت تاني")
        val items = listOf(slab)
        val f = formula("x", refs = mapOf("x" to "s1"), roundTo = -1)
        val r = TakeoffFormulaEngine.evaluate(f, items, geometry)
        assertEquals(com.corewall.qaqc.takeoff.TakeoffMath.netQuantity(slab, items, page), r.value!!, 1e-9)
    }

    @Test
    fun `rounding is applied after full evaluation not per reference`() {
        val f = formula("10 / 3", roundTo = 2)
        val r = TakeoffFormulaEngine.evaluate(f, emptyList(), geometry)
        assertEquals(3.33, r.value!!, 1e-9)
    }

    // ═════════════════════════════ التوكن

    @Test
    fun `slugify keeps arabic letters and collapses separators`() {
        assertEquals("حوائط_الدور_الارضي", takeoffSlug("حوائط -- الدور الارضي"))
    }

    @Test
    fun `slugify never returns a blank token`() {
        assertEquals("x", takeoffSlug("   ---   "))
    }

    @Test
    fun `var path joins category group and item with dots`() {
        assertEquals("concrete.ground.slab1", takeoffVarPath("concrete", "ground", "slab1"))
    }

    @Test
    fun `var path skips a missing group`() {
        assertEquals("concrete.slab1", takeoffVarPath("concrete", null, "slab1"))
    }

    @Test
    fun `unique token appends a counter on collision`() {
        val existing = setOf("area1", "area1_2")
        assertEquals("area1_3", takeoffUniqueToken("area1", existing))
        assertEquals("area1", takeoffUniqueToken("area1", emptySet()))
    }
}
