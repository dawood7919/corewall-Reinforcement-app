package com.corewall.qaqc.domain

import com.corewall.qaqc.data.model.BeamRange
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.data.model.WallRange

data class FieldChange(val field: String, val before: String, val after: String)

data class AttentionItem(
    val mark: String,
    val isWall: Boolean,
    /** التغييرات مقارنة بالدور اللي قبله مباشرة (null = مفيش دور قبله). */
    val vsPrev: List<FieldChange>,
    /** التغييرات مقارنة بالدور اللي بعده مباشرة (null = مفيش دور بعده). */
    val vsNext: List<FieldChange>,
    /** فجوة بيانات في الدور الحالي نفسه. */
    val gapHere: Boolean,
    val note: String? = null
)

object AttentionDiff {

    private const val ABSENT = "—"

    private fun wallFields(r: WallRange?): Map<String, String> =
        if (r == null) emptyMap()
        else linkedMapOf(
            "السُمك" to "${r.w}mm",
            "رأسي V" to r.v,
            "أفقي H" to r.h,
            "أطراف T" to r.t
        )

    private fun beamFields(r: BeamRange?): Map<String, String> =
        if (r == null) emptyMap()
        else linkedMapOf(
            "العرض" to "${r.w}mm",
            "العمق" to "${r.d}mm",
            "سفلي B" to r.bottom.joinToString(" / "),
            "علوي T" to r.top.joinToString(" / "),
            "جانبي" to r.side,
            "كانات" to r.links
        )

    private fun diff(a: Map<String, String>, b: Map<String, String>): List<FieldChange> {
        if (a.isEmpty() && b.isEmpty()) return emptyList()
        if (a.isEmpty()) return listOf(FieldChange("النطاق", ABSENT, "نشط"))
        if (b.isEmpty()) return listOf(FieldChange("النطاق", "نشط", ABSENT))
        val keys = a.keys + b.keys
        return keys.mapNotNull { k ->
            val va = a[k] ?: ABSENT
            val vb = b[k] ?: ABSENT
            if (va != vb) FieldChange(k, va, vb) else null
        }
    }

    /**
     * لكل حائط/كمرة: هل تسليحها في الدور المختار مختلف عن الدور اللي قبله
     * أو اللي بعده مباشرة؟ (Diff تلقائي بدون اختيار يدوي)
     */
    fun attentionFor(schedule: ScheduleData, logic: ScheduleLogic, level: String): List<AttentionItem> {
        val levelIdx = logic.idx(level) ?: return emptyList()
        val prevIdx = levelIdx - 1
        val nextIdx = levelIdx + 1
        val items = mutableListOf<AttentionItem>()

        for ((mark, rows) in schedule.walls) {
            val here = logic.activeWallRow(rows, levelIdx)?.value
            val prev = if (prevIdx >= 0) logic.activeWallRow(rows, prevIdx)?.value else null
            val next = if (nextIdx < logic.levels.size) logic.activeWallRow(rows, nextIdx)?.value else null
            val gapHere = logic.wallGapAt(rows, levelIdx)
            // بنقارن فقط لمّا الدور المجاور موجود فعلاً في المبنى وجوه مدى العنصر
            val vsPrev = if (prevIdx >= 0 && (here != null || prev != null))
                diff(wallFields(prev), wallFields(here)) else emptyList()
            val vsNext = if (nextIdx < logic.levels.size && (here != null || next != null))
                diff(wallFields(here), wallFields(next)) else emptyList()
            if (vsPrev.isNotEmpty() || vsNext.isNotEmpty() || gapHere) {
                items.add(AttentionItem(mark, true, vsPrev, vsNext, gapHere, here?.note))
            }
        }
        for ((mark, rows) in schedule.beams) {
            val here = logic.activeBeamRow(rows, levelIdx)?.value
            val prev = if (prevIdx >= 0) logic.activeBeamRow(rows, prevIdx)?.value else null
            val next = if (nextIdx < logic.levels.size) logic.activeBeamRow(rows, nextIdx)?.value else null
            val gapHere = logic.beamGapAt(rows, levelIdx)
            val vsPrev = if (prevIdx >= 0 && (here != null || prev != null))
                diff(beamFields(prev), beamFields(here)) else emptyList()
            val vsNext = if (nextIdx < logic.levels.size && (here != null || next != null))
                diff(beamFields(here), beamFields(next)) else emptyList()
            if (vsPrev.isNotEmpty() || vsNext.isNotEmpty() || gapHere) {
                items.add(AttentionItem(mark, false, vsPrev, vsNext, gapHere, here?.note))
            }
        }
        return items.sortedWith(compareByDescending<AttentionItem> { it.gapHere }.thenBy { it.mark })
    }
}
