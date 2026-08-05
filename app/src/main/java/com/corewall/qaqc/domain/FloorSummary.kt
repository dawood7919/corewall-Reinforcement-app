package com.corewall.qaqc.domain

import com.corewall.qaqc.data.model.ElementCategory
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.data.model.PlanElement
import com.corewall.qaqc.data.model.ScheduleData

/**
 * ملخّص حالة دور واحد — **محسوب مرّة واحدة**، والشاشات بتقراه بس.
 *
 * قبل كده الحسابات دي كانت متكرّرة جوّه الشاشات نفسها (`remember` في
 * composable)، فكل شاشة بتحسبها من أول وجديد وكل واحدة ممكن تطلع برقم
 * مختلف شوية. القاعدة بتقول: احسب في الـViewModel، الشاشة تعرض بس.
 *
 * كمان: كل رقم هنا مشتقّ من بيانات حقيقية. الشاشة القديمة كانت بتعرض
 * "السلامة ٩٢٪" ثابتة في الكود و"التوثيق = عدد الملاحظات × ١٢" — أرقام
 * متلفّقة معروضة كأنها قياس. في أداة جودة ده أسوأ من الزحمة.
 */
data class FloorSummary(
    val level: String,

    // ---- العناصر ----
    val total: Int,
    val named: Int,
    val walls: Int,
    val couplingBeams: Int,
    val internalBeams: Int,

    // ---- الفحص ----
    val approved: Int,
    val cast: Int,
    val wirSubmitted: Int,
    val rejected: Int,
    val notInspected: Int,

    // ---- مشاكل ----
    val gaps: Int,

    // ---- الشغل المفتوح ----
    val openTasks: Int,
    val doneTasks: Int,
    val notes: Int,
    val photos: Int,

    // ---- العمالة النهاردة ----
    val workers: Int,
    val foremen: Int,
    val engineers: Int
) {
    /** خلّص = معتمد أو مصبوب. */
    val done: Int get() = approved + cast

    /** النسبة محسوبة على المسمّى بس — العنصر اللي مالوش كود مالوش حالة أصلاً. */
    val completionPercent: Int get() = if (named == 0) 0 else done * 100 / named

    val pendingInspection: Int get() = (named - done).coerceAtLeast(0)

    val unnamed: Int get() = (total - named).coerceAtLeast(0)

    /** فيه حاجة محتاجة تدخّل دلوقتي؟ */
    val needsAttention: Boolean get() = rejected > 0 || gaps > 0

    val peopleOnSite: Int get() = workers + foremen + engineers

    companion object {
        val EMPTY = FloorSummary(
            level = "", total = 0, named = 0, walls = 0, couplingBeams = 0, internalBeams = 0,
            approved = 0, cast = 0, wirSubmitted = 0, rejected = 0, notInspected = 0,
            gaps = 0, openTasks = 0, doneTasks = 0, notes = 0, photos = 0,
            workers = 0, foremen = 0, engineers = 0
        )

        /**
         * بيحسب الملخّص. دالة نقية — مفيش أندرويد ولا قاعدة بيانات، فتنفع
         * تتختبر لوحدها.
         */
        fun compute(
            level: String,
            elements: List<PlanElement>,
            names: Map<String, String>,
            inspections: Map<Pair<String, String>, String>,
            schedule: ScheduleData,
            logic: ScheduleLogic,
            openTasks: Int,
            doneTasks: Int,
            notes: Int,
            photos: Int,
            workers: Int,
            foremen: Int,
            engineers: Int
        ): FloorSummary {
            var named = 0
            var approved = 0
            var cast = 0
            var wir = 0
            var rejected = 0
            var notInspected = 0
            var gaps = 0

            elements.forEach { el ->
                val mark = names[el.id] ?: return@forEach
                named++
                when (InspectionStatus.from(inspections[el.id to level])) {
                    InspectionStatus.APPROVED -> approved++
                    InspectionStatus.CAST -> cast++
                    InspectionStatus.WIR_SUBMITTED -> wir++
                    InspectionStatus.REJECTED -> rejected++
                    InspectionStatus.NONE -> notInspected++
                }
                if (logic.activeRange(schedule, mark, level) is ActiveRangeResult.Gap) gaps++
            }

            return FloorSummary(
                level = level,
                total = elements.size,
                named = named,
                walls = elements.count { it.cat == ElementCategory.WALL },
                couplingBeams = elements.count { it.cat == ElementCategory.COUPLING_BEAM },
                internalBeams = elements.count { it.cat == ElementCategory.INTERNAL_BEAM },
                approved = approved,
                cast = cast,
                wirSubmitted = wir,
                rejected = rejected,
                notInspected = notInspected,
                gaps = gaps,
                openTasks = openTasks,
                doneTasks = doneTasks,
                notes = notes,
                photos = photos,
                workers = workers,
                foremen = foremen,
                engineers = engineers
            )
        }
    }
}
