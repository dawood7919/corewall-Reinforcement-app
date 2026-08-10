package com.corewall.qaqc

import com.corewall.qaqc.data.model.BeamRange
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.data.model.WallRange
import com.corewall.qaqc.domain.AttentionDiff
import com.corewall.qaqc.domain.FloorComparison
import com.corewall.qaqc.domain.ScheduleLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * مقارنة الأدوار وقايمة الانتباه.
 *
 * الاتنين دول بيقولوا للمهندس "التسليح اتغيّر هنا" — ولو الإجابة اتغيّرت
 * بسبب أي شغل أداء، المستخدم هيصبّ على معلومة غلط. الاختبارات دي بتثبّت
 * الإجابات على حالات صغيرة ومقروءة.
 */
class ComparisonTest {

    private val levels = listOf("GROUND", "L01", "L02", "L03")
    private val logic = ScheduleLogic(levels)

    private fun wall(from: String, to: String?, w: Int, v: String = "T20-200") =
        WallRange(from = from, to = to, w = w, v = v, h = "T12-200")

    private fun beam(from: String, to: String?, d: Int) = BeamRange(
        from = from, to = to, w = 300, d = d,
        bottom = listOf("4T20"), top = listOf("4T20")
    )

    // ──────────────────────────────────────── مقارنة دورين

    @Test
    fun `thickness change between floors is reported as changed`() {
        val schedule = ScheduleData(
            levels,
            walls = mapOf("W1" to listOf(wall("GROUND", "L01", 400), wall("L01", "L03", 350))),
            beams = emptyMap()
        )
        val result = FloorComparison.compare(schedule, logic, "GROUND", "L01")!!
        assertEquals(1, result.changed.size)
        val change = result.changed.first()
        assertEquals("W1", change.mark)
        assertTrue(change.isWall)
        val thickness = change.changes.first { it.field == "السُمك" }
        assertEquals("400mm", thickness.before)
        assertEquals("350mm", thickness.after)
        assertTrue(result.anyChange)
    }

    @Test
    fun `identical reinforcement counts as same not changed`() {
        val schedule = ScheduleData(
            levels,
            walls = mapOf("W1" to listOf(wall("GROUND", "L03", 400))),
            beams = emptyMap()
        )
        val result = FloorComparison.compare(schedule, logic, "GROUND", "L01")!!
        assertTrue(result.changed.isEmpty())
        assertEquals(1, result.sameCount)
        assertTrue(!result.anyChange)
    }

    @Test
    fun `a mark that stops existing is removed not changed`() {
        val schedule = ScheduleData(
            levels,
            walls = mapOf("W1" to listOf(wall("GROUND", "L01", 400))),
            beams = emptyMap()
        )
        val result = FloorComparison.compare(schedule, logic, "GROUND", "L01")!!
        assertEquals(1, result.removed.size)
        assertEquals("W1", result.removed.first().mark)
    }

    @Test
    fun `unknown level returns null instead of an empty result`() {
        val schedule = ScheduleData(levels, emptyMap(), emptyMap())
        // الفرق مهم: null معناها "الدور ده مش في الجدول"، والنتيجة الفاضية
        // معناها "قارنّا ومفيش فرق". عرض التانية مكان الأولى كذب.
        assertNull(FloorComparison.compare(schedule, logic, "GROUND", "MARS"))
    }

    @Test
    fun `comparison can be narrowed to a single mark`() {
        val schedule = ScheduleData(
            levels,
            walls = mapOf(
                "W1" to listOf(wall("GROUND", "L01", 400), wall("L01", "L03", 350)),
                "W2" to listOf(wall("GROUND", "L01", 300), wall("L01", "L03", 250))
            ),
            beams = emptyMap()
        )
        val all = FloorComparison.compare(schedule, logic, "GROUND", "L01")!!
        val one = FloorComparison.compare(schedule, logic, "GROUND", "L01", mark = "W1")!!
        assertEquals(2, all.changed.size)
        assertEquals(1, one.changed.size)
        assertEquals("W1", one.changed.first().mark)
    }

    // ──────────────────────────────────────── قايمة الانتباه

    @Test
    fun `attention flags a mark whose reinforcement differs from the floor below`() {
        val schedule = ScheduleData(
            levels,
            walls = mapOf("W1" to listOf(wall("GROUND", "L01", 400), wall("L01", "L03", 350))),
            beams = emptyMap()
        )
        val items = AttentionDiff.attentionFor(schedule, logic, "L01")
        assertEquals(1, items.size)
        assertEquals("W1", items.first().mark)
        assertTrue(items.first().vsPrev.isNotEmpty())
    }

    @Test
    fun `attention stays quiet when nothing changes around the floor`() {
        val schedule = ScheduleData(
            levels,
            walls = mapOf("W1" to listOf(wall("GROUND", "L03", 400))),
            beams = emptyMap()
        )
        assertTrue(AttentionDiff.attentionFor(schedule, logic, "L01").isEmpty())
    }

    @Test
    fun `attention reports a data gap even with no neighbouring difference`() {
        val schedule = ScheduleData(
            levels,
            walls = mapOf("W1" to listOf(wall("GROUND", "L01", 400), wall("L02", "L03", 400))),
            beams = emptyMap()
        )
        // L01 جوّه المدى الكلي ومفيش صف بيغطيه → فجوة
        val items = AttentionDiff.attentionFor(schedule, logic, "L01")
        assertEquals(1, items.size)
        assertTrue(items.first().gapHere)
    }

    @Test
    fun `beam depth change is picked up too`() {
        val schedule = ScheduleData(
            levels,
            walls = emptyMap(),
            beams = mapOf("CB1" to listOf(beam("GROUND", "L01", 600), beam("L02", "L03", 800)))
        )
        val result = FloorComparison.compare(schedule, logic, "L01", "L02")!!
        val change = result.changed.firstOrNull { it.mark == "CB1" }
        assertTrue("expected CB1 to change depth", change != null)
        assertTrue(change!!.changes.any { it.field == "العمق" })
    }

    @Test
    fun `gaps are surfaced ahead of ordinary marks in the attention list`() {
        val schedule = ScheduleData(
            levels,
            walls = mapOf(
                // AAA بيتغيّر بس، BBB فيها فجوة — الفجوة الأهم فلازم تبقى فوق
                "AAA" to listOf(wall("GROUND", "L01", 400), wall("L01", "L03", 350)),
                "BBB" to listOf(wall("GROUND", "L01", 400), wall("L02", "L03", 400))
            ),
            beams = emptyMap()
        )
        val items = AttentionDiff.attentionFor(schedule, logic, "L01")
        assertEquals(2, items.size)
        assertEquals("BBB", items.first().mark)
        assertTrue(items.first().gapHere)
    }
}
