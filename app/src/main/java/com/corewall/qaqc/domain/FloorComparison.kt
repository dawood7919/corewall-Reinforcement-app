package com.corewall.qaqc.domain

import com.corewall.qaqc.data.model.BeamRange
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.data.model.WallRange

/**
 * مقارنة التسليح بين **أي** دورين — مش الجيران بس زي [AttentionDiff].
 *
 * ده اللي بيجاوب السؤال العملي في الموقع: "لما أطلع الدور الجاي،
 * هل التسليح هيتغيّر ولا أنفّذ زي ما أنا؟"
 *
 * الحساب حتمي بالكامل وبيستخدم [ScheduleLogic]، يعني بيحترم إن مدى
 * الحائط غير شامل للنهاية ومدى الكمرة شامل — الفرق ده لوحده بيغيّر
 * الإجابة بدور كامل لو اتجاهل.
 */
object FloorComparison {

    /** حالة العنصر بين الدورين. */
    enum class Kind { CHANGED, ADDED, REMOVED, SAME, GAP }

    data class MarkChange(
        val mark: String,
        val isWall: Boolean,
        val kind: Kind,
        val changes: List<FieldChange>,
        /** فجوة بيانات في دور البداية أو النهاية. */
        val gapFrom: Boolean = false,
        val gapTo: Boolean = false
    )

    data class Result(
        val fromLevel: String,
        val toLevel: String,
        val changed: List<MarkChange>,
        val added: List<MarkChange>,
        val removed: List<MarkChange>,
        val gaps: List<MarkChange>,
        val sameCount: Int
    ) {
        val anyChange: Boolean get() = changed.isNotEmpty() || added.isNotEmpty() || removed.isNotEmpty()
        val total: Int get() = changed.size + added.size + removed.size + sameCount
    }

    private const val ABSENT = "—"

    private fun wallFields(r: WallRange): Map<String, String> = linkedMapOf(
        "السُمك" to "${r.w}mm",
        "رأسي V" to r.v,
        "أفقي H" to r.h,
        "أطراف T" to r.t
    )

    private fun beamFields(r: BeamRange): Map<String, String> = linkedMapOf(
        "العرض" to "${r.w}mm",
        "العمق" to "${r.d}mm",
        "سفلي B" to r.bottom.joinToString(" / "),
        "علوي T" to r.top.joinToString(" / "),
        "جانبي" to r.side,
        "كانات" to r.links
    )

    private fun diff(a: Map<String, String>, b: Map<String, String>): List<FieldChange> =
        (a.keys + b.keys).mapNotNull { k ->
            val va = a[k] ?: ABSENT
            val vb = b[k] ?: ABSENT
            if (va != vb) FieldChange(k, va, vb) else null
        }

    /**
     * بيقارن دورين. [mark] لو اتحدّد بنقارن عنصر واحد بس.
     * بيرجع null لو أي دور مش موجود في قايمة الأدوار.
     */
    fun compare(
        schedule: ScheduleData,
        logic: ScheduleLogic,
        fromLevel: String,
        toLevel: String,
        mark: String? = null
    ): Result? {
        val fromIdx = logic.idx(fromLevel) ?: return null
        val toIdx = logic.idx(toLevel) ?: return null

        val changed = mutableListOf<MarkChange>()
        val added = mutableListOf<MarkChange>()
        val removed = mutableListOf<MarkChange>()
        val gaps = mutableListOf<MarkChange>()
        var same = 0

        fun classify(
            m: String,
            isWall: Boolean,
            fromFields: Map<String, String>?,
            toFields: Map<String, String>?,
            gapFrom: Boolean,
            gapTo: Boolean
        ) {
            when {
                gapFrom || gapTo ->
                    gaps += MarkChange(m, isWall, Kind.GAP, emptyList(), gapFrom, gapTo)
                fromFields == null && toFields == null -> Unit  // العنصر مش موجود في الدورين
                fromFields == null ->
                    added += MarkChange(m, isWall, Kind.ADDED, emptyList())
                toFields == null ->
                    removed += MarkChange(m, isWall, Kind.REMOVED, emptyList())
                else -> {
                    val d = diff(fromFields, toFields)
                    if (d.isEmpty()) same++
                    else changed += MarkChange(m, isWall, Kind.CHANGED, d)
                }
            }
        }

        schedule.walls
            .filterKeys { mark == null || it.equals(mark, ignoreCase = true) }
            .forEach { (m, rows) ->
                classify(
                    m, true,
                    logic.activeWallRow(rows, fromIdx)?.value?.let(::wallFields),
                    logic.activeWallRow(rows, toIdx)?.value?.let(::wallFields),
                    logic.wallGapAt(rows, fromIdx),
                    logic.wallGapAt(rows, toIdx)
                )
            }

        schedule.beams
            .filterKeys { mark == null || it.equals(mark, ignoreCase = true) }
            .forEach { (m, rows) ->
                classify(
                    m, false,
                    logic.activeBeamRow(rows, fromIdx)?.value?.let(::beamFields),
                    logic.activeBeamRow(rows, toIdx)?.value?.let(::beamFields),
                    logic.beamGapAt(rows, fromIdx),
                    logic.beamGapAt(rows, toIdx)
                )
            }

        return Result(
            fromLevel = fromLevel,
            toLevel = toLevel,
            changed = changed.sortedBy { it.mark },
            added = added.sortedBy { it.mark },
            removed = removed.sortedBy { it.mark },
            gaps = gaps.sortedBy { it.mark },
            sameCount = same
        )
    }

    /** الدور اللي بعد [level] مباشرة، أو null لو ده آخر دور. */
    fun nextLevel(logic: ScheduleLogic, level: String): String? {
        val i = logic.idx(level) ?: return null
        return logic.levels.getOrNull(i + 1)
    }
}
