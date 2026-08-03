package com.corewall.qaqc.ai.context

import com.corewall.qaqc.ai.model.BarCountCheck
import com.corewall.qaqc.ai.model.ChangesSummary
import com.corewall.qaqc.ai.model.DataGap
import com.corewall.qaqc.ai.model.DocumentationSummary
import com.corewall.qaqc.ai.model.ElementChange
import com.corewall.qaqc.ai.model.ElementReinforcement
import com.corewall.qaqc.ai.model.ElementsSummary
import com.corewall.qaqc.ai.model.FieldDelta
import com.corewall.qaqc.ai.model.FloorContext
import com.corewall.qaqc.ai.model.InspectionSummary
import com.corewall.qaqc.ai.model.ManpowerSummary
import com.corewall.qaqc.ai.model.QuantitySummary
import com.corewall.qaqc.data.db.AttendanceFileEntity
import com.corewall.qaqc.data.db.BarCountEntity
import com.corewall.qaqc.data.db.DailyAttendanceEntity
import com.corewall.qaqc.data.db.ElementAttachmentEntity
import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.data.db.TaskEntity
import com.corewall.qaqc.data.model.ElementCategory
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.data.model.PlanData
import com.corewall.qaqc.data.model.ScheduleData
import com.corewall.qaqc.domain.ActiveRangeResult
import com.corewall.qaqc.domain.AttentionDiff
import com.corewall.qaqc.domain.CalloutResult
import com.corewall.qaqc.domain.ScheduleLogic
import com.corewall.qaqc.domain.SteelCalculator

/**
 * بيبني لقطة الدور اللي بتتبعت للـ AI.
 *
 * دالة نقية (pure) — مفيش Android ولا شبكة ولا Room هنا، عشان تتختبر لوحدها
 * وتفضل الحقائق محسوبة بمنطق التطبيق نفسه.
 */
object FloorContextBuilder {

    /** أقصى عدد عناصر تسليح نبعتها — عشان الطلب يفضل صغير ورخيص. */
    private const val MAX_ELEMENTS = 40
    private const val MAX_CHANGES = 15

    fun build(
        project: String,
        level: String,
        planData: PlanData,
        schedule: ScheduleData,
        logic: ScheduleLogic,
        names: Map<String, String>,
        inspections: Map<Pair<String, String>, String>,
        barCounts: List<BarCountEntity>,
        notes: List<NoteEntity>,
        tasks: List<TaskEntity>,
        attachments: List<ElementAttachmentEntity>,
        attendanceFiles: List<AttendanceFileEntity>,
        dailyAttendance: List<DailyAttendanceEntity>,
        now: Long = System.currentTimeMillis()
    ): FloorContext {
        val levels = logic.levels
        val levelIdx = levels.indexOf(level)

        // ---------- العناصر ----------
        val elements = planData.elements
        val namedElements = elements.filter { names[it.id] != null }
        val elementsSummary = ElementsSummary(
            total = elements.size,
            named = namedElements.size,
            unnamed = elements.size - namedElements.size,
            walls = elements.count { it.cat == ElementCategory.WALL },
            couplingBeams = elements.count { it.cat == ElementCategory.COUPLING_BEAM },
            internalBeams = elements.count { it.cat == ElementCategory.INTERNAL_BEAM }
        )

        // ---------- التسليح الشغّال + الفجوات ----------
        val reinforcement = mutableListOf<ElementReinforcement>()
        val gaps = mutableListOf<DataGap>()
        val unparsed = mutableListOf<String>()
        var vArea = 0.0
        var hArea = 0.0

        namedElements.take(MAX_ELEMENTS).forEach { el ->
            val mark = names[el.id] ?: return@forEach
            val status = InspectionStatus.from(inspections[el.id to level]).label
            when (val active = logic.activeRange(schedule, mark, level)) {
                is ActiveRangeResult.Wall -> {
                    val r = active.row
                    reinforcement += ElementReinforcement(
                        mark = mark, type = "WALL",
                        rangeFrom = r.from, rangeTo = r.to,
                        widthMm = r.w,
                        vertical = r.v, horizontal = r.h, ties = r.t,
                        revised = r.rev, userEdited = r.edited, note = r.note,
                        inspectionStatus = status
                    )
                    vArea += areaPerMeter(r.v, unparsed)
                    hArea += areaPerMeter(r.h, unparsed)
                }
                is ActiveRangeResult.Beam -> {
                    val r = active.row
                    reinforcement += ElementReinforcement(
                        mark = mark, type = "BEAM",
                        rangeFrom = r.from, rangeTo = r.to,
                        widthMm = r.w, depthMm = r.d,
                        bottom = r.bottom.joinToString(" / "),
                        top = r.top.joinToString(" / "),
                        side = r.side, links = r.links,
                        revised = r.rev, userEdited = r.edited, note = r.note,
                        inspectionStatus = status
                    )
                }
                ActiveRangeResult.Gap -> {
                    val missing = logic.gapLevels(schedule, mark)
                    gaps += DataGap(
                        mark = mark,
                        type = if (schedule.isWallMark(mark)) "WALL" else "BEAM",
                        missingLevels = missing
                    )
                }
                else -> Unit // خارج المدى / اسم غير معروف — مش مشكلة تستاهل تنبيه
            }
        }

        // ---------- التغييرات مقابل الدور السابق/التالي ----------
        val attention = runCatching { AttentionDiff.attentionFor(schedule, logic, level) }
            .getOrDefault(emptyList())
        val changedPrev = attention.filter { it.vsPrev.isNotEmpty() }.take(MAX_CHANGES).map { item ->
            ElementChange(
                mark = item.mark,
                type = if (item.isWall) "WALL" else "BEAM",
                fields = item.vsPrev.map { FieldDelta(it.field, it.before, it.after) }
            )
        }
        val changingNext = attention.filter { it.vsNext.isNotEmpty() }.take(MAX_CHANGES).map { item ->
            ElementChange(
                mark = item.mark,
                type = if (item.isWall) "WALL" else "BEAM",
                fields = item.vsNext.map { FieldDelta(it.field, it.before, it.after) }
            )
        }

        // ---------- عدّ الأسياخ: الموقع مقابل الرسمة ----------
        val levelCounts = barCounts.filter { it.level == level }
        val checks = levelCounts.groupBy { it.elementId }.mapNotNull { (elementId, rows) ->
            val mark = names[elementId] ?: return@mapNotNull null
            val site = totals(rows.filter { it.source == BarCountEntity.SOURCE_SITE })
            val drawing = totals(rows.filter { it.source == BarCountEntity.SOURCE_DRAWING })
            if (site.isEmpty() && drawing.isEmpty()) return@mapNotNull null
            val missing = drawing.mapNotNull { (dia, planned) ->
                val onSite = site[dia] ?: 0
                if (onSite < planned) dia to (planned - onSite) else null
            }.toMap()
            BarCountCheck(
                mark = mark,
                siteTotals = site.mapKeys { "Ø${it.key}" },
                drawingTotals = drawing.mapKeys { "Ø${it.key}" },
                matches = site == drawing,
                missingOnSite = missing.mapKeys { "Ø${it.key}" }
            )
        }

        // ---------- الفحص ----------
        fun statusOf(id: String) = InspectionStatus.from(inspections[id to level])
        val approved = namedElements.count { statusOf(it.id) == InspectionStatus.APPROVED }
        val cast = namedElements.count { statusOf(it.id) == InspectionStatus.CAST }
        val wir = namedElements.count { statusOf(it.id) == InspectionStatus.WIR_SUBMITTED }
        val rejected = namedElements.filter { statusOf(it.id) == InspectionStatus.REJECTED }
        val none = namedElements.count { statusOf(it.id) == InspectionStatus.NONE }
        val done = approved + cast
        val inspectionSummary = InspectionSummary(
            approved = approved, cast = cast, wirSubmitted = wir,
            rejected = rejected.size, notInspected = none,
            completionPercent = if (namedElements.isEmpty()) 0 else done * 100 / namedElements.size,
            rejectedMarks = rejected.mapNotNull { names[it.id] }.take(10)
        )

        // ---------- التوثيق ----------
        val levelNotes = notes.filter { it.level == level }
        val levelTasks = tasks.filter { it.level == level }
        val documentation = DocumentationSummary(
            notes = levelNotes.size,
            notesWithImages = levelNotes.count { it.imagePathsJson.length > 4 },
            attachments = attachments.count { it.level == level },
            openTasks = levelTasks.count { !it.done },
            doneTasks = levelTasks.count { it.done },
            overdueTasks = levelTasks.count { !it.done && (it.dueDate ?: Long.MAX_VALUE) < now },
            recentNoteTitles = levelNotes.sortedByDescending { it.updatedAt }
                .mapNotNull { it.title.takeIf { t -> t.isNotBlank() } }.take(5)
        )

        // ---------- العمالة ----------
        val fileIds = attendanceFiles.filter { it.level == level }.map { it.id }.toSet()
        val levelDaily = dailyAttendance.filter { it.fileId in fileIds }
        val today = startOfDay(now)
        val todayRows = levelDaily.filter { startOfDay(it.date) == today }
        val perDay = levelDaily.groupBy { startOfDay(it.date) }
            .mapValues { (_, rows) -> rows.sumOf { it.workers } }
        val manpower = ManpowerSummary(
            companies = fileIds.size,
            workersToday = todayRows.sumOf { it.workers },
            foremenToday = todayRows.sumOf { it.foremen },
            engineersToday = todayRows.sumOf { it.engineers },
            averageWorkersPerDay = if (perDay.isEmpty()) 0 else perDay.values.average().toInt(),
            daysRecorded = perDay.size
        )

        // ---------- الكميات ----------
        val quantities = QuantitySummary(
            wallVerticalAreaPerMeterMm2 = round1(vArea),
            wallHorizontalAreaPerMeterMm2 = round1(hArea),
            unparsedCallouts = unparsed.distinct().take(10),
            heaviestElements = reinforcement
                .filter { it.type == "WALL" }
                .sortedByDescending { it.widthMm }
                .take(5)
                .map { "${it.mark} (${it.widthMm}mm)" }
        )

        return FloorContext(
            project = project,
            level = level,
            levelIndex = levelIdx,
            totalLevels = levels.size,
            previousLevel = levels.getOrNull(levelIdx - 1),
            nextLevel = levels.getOrNull(levelIdx + 1),
            elements = elementsSummary,
            reinforcement = reinforcement,
            dataGaps = gaps,
            changes = ChangesSummary(changedPrev, changingNext),
            barCountChecks = checks,
            inspection = inspectionSummary,
            quantities = quantities,
            documentation = documentation,
            manpower = manpower
        )
    }

    private fun totals(rows: List<BarCountEntity>): Map<Int, Int> =
        rows.filter { it.count > 0 }
            .groupBy { it.diameter }
            .mapValues { (_, r) -> r.sumOf { it.count } }
            .toSortedMap()

    /** بيجمع مساحة/متر من كولاوت زي T25-200؛ الكولاوتات غير المفهومة بتتسجّل. */
    private fun areaPerMeter(raw: String?, unparsed: MutableList<String>): Double {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "-") return 0.0
        val parsed = SteelCalculator.parseList(value)
        if (parsed == null) { unparsed += value; return 0.0 }
        return parsed.sumOf {
            when (it) {
                is CalloutResult.Spaced -> it.areaPerMeterMm2
                is CalloutResult.Counted -> it.totalAreaMm2
            }
        }
    }

    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0

    private fun startOfDay(ts: Long): Long {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = ts
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
