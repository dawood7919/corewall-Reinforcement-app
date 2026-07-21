package com.corewall.qaqc.domain

import com.corewall.qaqc.data.model.BeamRange
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.data.model.WallRange

/**
 * منطق المدايات:
 * - الحوائط: المدى غير شامل للنهاية (from <= level < to).
 * - الكمرات: المدى شامل للنهاية (from <= level <= to)، ولو to غايبة فالمدى دور واحد.
 * - أسماء نهاية خارج قايمة الأدوار ("TOP ROOF", "FM LMR") تعني الاستمرار فوق آخر دور.
 */
class ScheduleLogic(val levels: List<String>) {

    private val levelIndex: Map<String, Int> = levels.withIndex().associate { (i, l) -> l to i }

    fun idx(level: String): Int? = levelIndex[level]

    /** بداية المدى (index) — لو الاسم مش معروف بنرجّع null (بيانات غلط). */
    private fun fromIdx(from: String): Int? = levelIndex[from]

    /** نهاية المدى exclusive بالـ index. */
    private fun wallEndExclusive(r: WallRange): Int {
        val to = r.to ?: return levels.size
        return levelIndex[to] ?: levels.size // "TOP ROOF" / "FM LMR" => لفوق خالص
    }

    private fun beamEndExclusive(r: BeamRange): Int {
        val to = r.to ?: r.from // كمرة بدون to = دور واحد
        val toIdx = levelIndex[to] ?: return levels.size
        return toIdx + 1 // شامل النهاية
    }

    fun wallCovers(r: WallRange, levelIdx: Int): Boolean {
        val f = fromIdx(r.from) ?: return false
        return levelIdx in f until wallEndExclusive(r)
    }

    fun beamCovers(r: BeamRange, levelIdx: Int): Boolean {
        val f = fromIdx(r.from) ?: return false
        return levelIdx in f until beamEndExclusive(r)
    }

    fun activeWallRow(rows: List<WallRange>, levelIdx: Int): IndexedValue<WallRange>? =
        rows.withIndex().firstOrNull { wallCovers(it.value, levelIdx) }

    fun activeBeamRow(rows: List<BeamRange>, levelIdx: Int): IndexedValue<BeamRange>? =
        rows.withIndex().firstOrNull { beamCovers(it.value, levelIdx) }

    /** المدى الكلي للعنصر (من أول from لآخر نهاية) بالـ index — [start, endExclusive]. */
    fun wallOverallSpan(rows: List<WallRange>): IntRange? {
        val starts = rows.mapNotNull { fromIdx(it.from) }
        if (starts.isEmpty()) return null
        return starts.min() until rows.maxOf { wallEndExclusive(it) }
    }

    fun beamOverallSpan(rows: List<BeamRange>): IntRange? {
        val starts = rows.mapNotNull { fromIdx(it.from) }
        if (starts.isEmpty()) return null
        return starts.min() until rows.maxOf { beamEndExclusive(it) }
    }

    /**
     * فجوة بيانات حقيقية: الدور واقع جوّه المدى الكلي للعنصر
     * لكن مفيش أي صف بيغطيه — لازم تتعرض كتحذير، مش تختفي بصمت.
     */
    fun wallGapAt(rows: List<WallRange>, levelIdx: Int): Boolean {
        val span = wallOverallSpan(rows) ?: return false
        return levelIdx in span && activeWallRow(rows, levelIdx) == null
    }

    fun beamGapAt(rows: List<BeamRange>, levelIdx: Int): Boolean {
        val span = beamOverallSpan(rows) ?: return false
        return levelIdx in span && activeBeamRow(rows, levelIdx) == null
    }

    /** كل الأدوار اللي فيها فجوة بيانات لعنصر معيّن. */
    fun gapLevels(schedule: ScheduleData, mark: String): List<String> {
        val result = mutableListOf<String>()
        val wallRows = schedule.walls[mark]
        val beamRows = schedule.beams[mark]
        for (i in levels.indices) {
            val gap = when {
                wallRows != null -> wallGapAt(wallRows, i)
                beamRows != null -> beamGapAt(beamRows, i)
                else -> false
            }
            if (gap) result.add(levels[i])
        }
        return result
    }

    /** المدى الشغّال لعنصر باسمه المرجعي في دور معيّن — نفس منطق النسخة المرجعية. */
    fun activeRange(schedule: ScheduleData, mark: String, level: String): ActiveRangeResult {
        val levelIdx = idx(level) ?: return ActiveRangeResult.UnknownLevel
        schedule.walls[mark]?.let { rows ->
            val active = activeWallRow(rows, levelIdx)
            if (active != null) return ActiveRangeResult.Wall(active.index, active.value)
            return if (wallGapAt(rows, levelIdx)) ActiveRangeResult.Gap else ActiveRangeResult.OutOfRange
        }
        schedule.beams[mark]?.let { rows ->
            val active = activeBeamRow(rows, levelIdx)
            if (active != null) return ActiveRangeResult.Beam(active.index, active.value)
            return if (beamGapAt(rows, levelIdx)) ActiveRangeResult.Gap else ActiveRangeResult.OutOfRange
        }
        return ActiveRangeResult.UnknownMark
    }
}

sealed interface ActiveRangeResult {
    data class Wall(val rowIndex: Int, val row: WallRange) : ActiveRangeResult
    data class Beam(val rowIndex: Int, val row: BeamRange) : ActiveRangeResult

    /** الدور جوّه مدى العنصر لكن مفيش صف بيغطيه — فجوة بيانات في الجدول الأصلي. */
    data object Gap : ActiveRangeResult

    /** الدور خارج مدى العنصر أصلاً (العنصر مش موجود في الدور ده). */
    data object OutOfRange : ActiveRangeResult
    data object UnknownMark : ActiveRangeResult
    data object UnknownLevel : ActiveRangeResult
}
