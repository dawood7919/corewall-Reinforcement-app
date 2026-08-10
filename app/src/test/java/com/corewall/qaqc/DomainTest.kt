package com.corewall.qaqc

import com.corewall.qaqc.data.model.BeamRange
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.data.model.WallRange
import com.corewall.qaqc.domain.ActiveRangeResult
import com.corewall.qaqc.domain.CalloutResult
import com.corewall.qaqc.domain.NotesLogic
import com.corewall.qaqc.domain.ScheduleLogic
import com.corewall.qaqc.domain.SteelCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات تثبيت لمحرّك الحساب.
 *
 * الغرض مش إثبات إن الحساب "صح" — الغرض إنه **مايتغيّرش**. أي شغل تحسين
 * أداء بيعدّي على الملفات دي لازم يفضل بيطلع نفس الأرقام بالظبط، والقاعدة
 * الأهم فيهم: **مدى الحيطة نهايته خارجة، ومدى الكمرة نهايته داخلة**.
 */
class DomainTest {

    private val levels = listOf("B02", "B01", "GROUND", "L01", "L02", "L03", "L04")
    private val logic = ScheduleLogic(levels)

    // ──────────────────────────────────────────── حاسبة الحديد

    @Test
    fun `spaced callout area per meter`() {
        val r = SteelCalculator.parse("T25-200") as CalloutResult.Spaced
        assertEquals(25, r.diaMm)
        assertEquals(200, r.spacingMm)
        assertEquals(5.0, r.barsPerMeter, 1e-9)
        // π×25²/4 = 490.8739…
        assertEquals(490.8738521234052, r.barAreaMm2, 1e-9)
        assertEquals(2454.369260617026, r.areaPerMeterMm2, 1e-9)
    }

    @Test
    fun `counted callout total area`() {
        val r = SteelCalculator.parse("6T32") as CalloutResult.Counted
        assertEquals(6, r.count)
        assertEquals(32, r.diaMm)
        assertEquals(6 * SteelCalculator.barArea(32), r.totalAreaMm2, 1e-9)
    }

    @Test
    fun `at sign is accepted as a spacing separator`() {
        val dash = SteelCalculator.parse("T12-150") as CalloutResult.Spaced
        val at = SteelCalculator.parse("T12@150") as CalloutResult.Spaced
        assertEquals(dash.areaPerMeterMm2, at.areaPerMeterMm2, 1e-12)
    }

    @Test
    fun `unparseable callouts return null rather than a wrong number`() {
        assertNull(SteelCalculator.parse("-"))
        assertNull(SteelCalculator.parse(""))
        assertNull(SteelCalculator.parse("T25-0"))
        assertNull(SteelCalculator.parse("blah"))
        // جزء واحد غلط بيبطّل القايمة كلها — أحسن من مجموع ناقص بصمت.
        assertNull(SteelCalculator.parseList("T10-200,blah"))
    }

    @Test
    fun `composite callout splits into parts`() {
        val parts = SteelCalculator.parseList("T10-200,T10-200")!!
        assertEquals(2, parts.size)
        assertTrue(parts.all { it is CalloutResult.Spaced })
    }

    // ──────────────────────── مدى الحيطة: النهاية خارجة

    @Test
    fun `wall range excludes its to level`() {
        val row = WallRange(from = "GROUND", to = "L02", w = 400, v = "T20-200", h = "T12-200")
        assertTrue(logic.wallCovers(row, levels.indexOf("GROUND")))
        assertTrue(logic.wallCovers(row, levels.indexOf("L01")))
        // L02 هي النهاية — خارجة
        assertTrue(!logic.wallCovers(row, levels.indexOf("L02")))
    }

    @Test
    fun `wall range without a to level runs to the top`() {
        val row = WallRange(from = "L01", to = null, w = 400, v = "T20-200", h = "T12-200")
        assertTrue(logic.wallCovers(row, levels.indexOf("L04")))
    }

    // ──────────────────────── مدى الكمرة: النهاية داخلة

    @Test
    fun `beam range includes its to level`() {
        val row = BeamRange(
            from = "GROUND", to = "L02", w = 300, d = 600,
            bottom = listOf("4T20"), top = listOf("4T20")
        )
        assertTrue(logic.beamCovers(row, levels.indexOf("GROUND")))
        assertTrue(logic.beamCovers(row, levels.indexOf("L02")))
        assertTrue(!logic.beamCovers(row, levels.indexOf("L03")))
    }

    @Test
    fun `beam range without a to level covers one level only`() {
        val row = BeamRange(
            from = "L01", to = null, w = 300, d = 600,
            bottom = listOf("4T20"), top = listOf("4T20")
        )
        assertTrue(logic.beamCovers(row, levels.indexOf("L01")))
        assertTrue(!logic.beamCovers(row, levels.indexOf("L02")))
    }

    // ──────────────────────────────────────── الفجوات والمدى الشغّال

    @Test
    fun `a hole inside the overall span is reported as a gap`() {
        val rows = listOf(
            WallRange(from = "B02", to = "GROUND", w = 400, v = "T20-200", h = "T12-200"),
            WallRange(from = "L02", to = "L04", w = 400, v = "T16-200", h = "T12-200")
        )
        val schedule = ScheduleData(levels, mapOf("W1" to rows), emptyMap())

        // GROUND و L01 مش مغطّيين، وواقعين جوّه المدى الكلي → فجوة
        assertEquals(ActiveRangeResult.Gap, logic.activeRange(schedule, "W1", "GROUND"))
        assertEquals(ActiveRangeResult.Gap, logic.activeRange(schedule, "W1", "L01"))
        assertEquals(listOf("GROUND", "L01"), logic.gapLevels(schedule, "W1"))
    }

    @Test
    fun `outside the overall span is out of range not a gap`() {
        val rows = listOf(WallRange(from = "L02", to = "L04", w = 400, v = "T16-200", h = "T12-200"))
        val schedule = ScheduleData(levels, mapOf("W1" to rows), emptyMap())
        assertEquals(ActiveRangeResult.OutOfRange, logic.activeRange(schedule, "W1", "B02"))
    }

    @Test
    fun `unknown mark and unknown level are distinguished`() {
        val schedule = ScheduleData(levels, emptyMap(), emptyMap())
        assertEquals(ActiveRangeResult.UnknownMark, logic.activeRange(schedule, "NOPE", "L01"))
        assertEquals(ActiveRangeResult.UnknownLevel, logic.activeRange(schedule, "NOPE", "MARS"))
    }

    @Test
    fun `active row returns the first matching row index`() {
        val rows = listOf(
            WallRange(from = "B02", to = "GROUND", w = 400, v = "T20-200", h = "T12-200"),
            WallRange(from = "GROUND", to = "L04", w = 350, v = "T16-200", h = "T12-200")
        )
        val schedule = ScheduleData(levels, mapOf("W1" to rows), emptyMap())
        val active = logic.activeRange(schedule, "W1", "L01") as ActiveRangeResult.Wall
        assertEquals(1, active.rowIndex)
        assertEquals(350, active.row.w)
    }

    // ──────────────────────────────────────────── منطق الملاحظات

    @Test
    fun `checklist parses markdown lines and keeps their line numbers`() {
        val body = "مقدّمة\n- [ ] بند أول\nكلام\n- [x] بند تاني"
        val items = NotesLogic.checklist(body)
        assertEquals(2, items.size)
        assertEquals("بند أول", items[0].text)
        assertEquals(1, items[0].line)
        assertTrue(!items[0].done)
        assertTrue(items[1].done)
        assertEquals(3, items[1].line)
    }

    @Test
    fun `toggling an item leaves the surrounding text untouched`() {
        val body = "مقدّمة\n- [ ] بند\nخاتمة"
        val next = NotesLogic.toggleItem(body, 1)
        assertEquals("مقدّمة\n- [x] بند\nخاتمة", next)
        assertEquals(body, NotesLogic.toggleItem(next, 1))
    }

    @Test
    fun `text to checklist and back is lossless for the item text`() {
        val plain = "افحص الشدة\nصوّر التسليح"
        val list = NotesLogic.toChecklist(plain)
        assertEquals("- [ ] افحص الشدة\n- [ ] صوّر التسليح", list)
        assertEquals(plain, NotesLogic.toPlainText(list))
    }

    @Test
    fun `search requires every term to match`() {
        val note = com.corewall.qaqc.data.db.NoteEntity(
            elementId = "s1", level = "L03", title = "فحص حيطة",
            body = "التسليح مطابق", createdAt = 0, updatedAt = 0
        )
        assertTrue(NotesLogic.matches(note, "حيطة", emptyList()))
        assertTrue(NotesLogic.matches(note, "حيطة تسليح", emptyList()))
        assertTrue(!NotesLogic.matches(note, "حيطة كمرة", emptyList()))
        // التصنيفات جزء من الكوم اللي بيتدوّر فيه
        assertTrue(NotesLogic.matches(note, "عاجل", listOf("عاجل")))
    }

    @Test
    fun `sorting puts pinned first then newest`() {
        fun note(id: Long, pinned: Boolean, updated: Long) =
            com.corewall.qaqc.data.db.NoteEntity(
                id = id, elementId = "s1", level = "L01",
                createdAt = 0, updatedAt = updated, pinned = pinned
            )
        val sorted = NotesLogic.sorted(
            listOf(note(1, false, 300), note(2, true, 100), note(3, false, 500))
        )
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }
}
