package com.corewall.qaqc.domain

import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.data.model.PlanElement
import com.corewall.qaqc.data.model.ScheduleData

/**
 * **هل الدور جاهز للصبّة؟**
 *
 * ده أغلى سؤال في الموقع: قرار الصبّ مالوش تراجع. الشاشة اللي بتستخدم
 * الحساب ده بتجمع كل الشروط في حكم واحد بدل ما المهندس يفتح خمس شاشات
 * ويحاول يفتكر حاجة نسيها.
 *
 * ### الحساب حتمي بالكامل — صفر ذكاء اصطناعي
 * قرار زي ده **ممنوع** يعتمد على نموذج لغوي. كل بند هنا محسوب من بيانات
 * حقيقية بقواعد مكتوبة، وينفع المهندس يراجع كل رقم فيه بنفسه.
 *
 * ### الفرق بين مانع وتحذير
 * - **مانع (BLOCKER)**: حاجة تخلّي الصبّ غلط فنّياً أو تعاقدياً — حديد
 *   ماتفحصش، عنصر مرفوض، أو تسليح مش معروف أصلاً.
 * - **تحذير (WARNING)**: نقص توثيقي أو تحقّق ناقص. مش بيمنع الصبّ، بس
 *   بيخلّي إثبات الجودة بعدين أصعب.
 *
 * الفرق ده مقصود: لو كل حاجة بقت "مانع"، المهندس هيتجاهل الشاشة كلها.
 */
object PourReadiness {

    enum class Level { BLOCKER, WARNING, INFO }

    data class Finding(
        val id: String,
        val level: Level,
        val title: String,
        val detail: String,
        /** أكواد العناصر المعنية — للعرض وللضغط عليها. */
        val marks: List<String> = emptyList(),
        val elementIds: List<String> = emptyList()
    )

    data class Result(
        val level: String,
        /** العناصر اللي المفروض تتصبّ في الدور ده (استبعدنا اللي اتصبّ). */
        val scopeCount: Int,
        /** منها كام معتمد وجاهز فعلاً. */
        val approvedCount: Int,
        /** اتصبّ خلاص — خارج نطاق القرار. */
        val castCount: Int,
        val findings: List<Finding>
    ) {
        val blockers: List<Finding> get() = findings.filter { it.level == Level.BLOCKER }
        val warnings: List<Finding> get() = findings.filter { it.level == Level.WARNING }
        val notes: List<Finding> get() = findings.filter { it.level == Level.INFO }

        /**
         * جاهز = مفيش موانع **وفيه عناصر أصلاً**.
         * دور من غير عناصر مش "جاهز" — ده دور مالوش شغل، وفرق مهم.
         */
        val ready: Boolean get() = blockers.isEmpty() && scopeCount > 0

        val hasNothingToPour: Boolean get() = scopeCount == 0 && castCount == 0

        /** نسبة الاعتماد — مؤشر تقدّم مش حكم. */
        val approvedPercent: Int
            get() = if (scopeCount == 0) 0 else (approvedCount * 100) / scopeCount

        val verdict: String
            get() = when {
                hasNothingToPour -> "مفيش عناصر في الدور ده"
                scopeCount == 0 -> "كل العناصر اتصبّت"
                ready && warnings.isEmpty() -> "جاهز للصبّة"
                ready -> "جاهز — مع ملاحظات"
                else -> "مش جاهز"
            }
    }

    /**
     * بيقيّم دور واحد. كل المعاملات قيم لحظية — الدالة نقية وينفع تتختبر
     * من غير أندرويد ولا قاعدة بيانات.
     */
    fun evaluate(
        level: String,
        elements: List<PlanElement>,
        names: Map<String, String>,
        inspections: Map<Pair<String, String>, String>,
        schedule: ScheduleData,
        logic: ScheduleLogic,
        barCounts: List<com.corewall.qaqc.data.db.BarCountEntity>,
        photoCount: Int,
        openTasks: Int
    ): Result {
        val findings = mutableListOf<Finding>()

        val unnamed = mutableListOf<String>()
        val unknownMark = mutableListOf<String>()
        val gaps = mutableListOf<String>()

        val rejected = mutableListOf<String>()
        val pendingApproval = mutableListOf<String>()   // WIR مقدّم — لسه ماتعتمدش
        val notInspected = mutableListOf<String>()      // مفيش أي فحص
        val approved = mutableListOf<String>()
        val cast = mutableListOf<String>()

        // العناصر اللي في نطاق الصبّ (elementId → mark)
        val inScope = mutableMapOf<String, String>()

        elements.forEach { el ->
            val mark = names[el.id]
            if (mark == null) {
                unnamed += el.id
                return@forEach
            }

            when (logic.activeRange(schedule, mark, level)) {
                // العنصر مش موجود في الدور ده أصلاً — ده طبيعي، مش مشكلة
                ActiveRangeResult.OutOfRange -> return@forEach

                ActiveRangeResult.UnknownMark -> {
                    unknownMark += mark
                    return@forEach
                }

                ActiveRangeResult.UnknownLevel -> return@forEach

                // فجوة = مفيش صف تسليح بيغطي الدور. مانع حقيقي:
                // مش عارف تصبّ حاجة إنت مش عارف تسليحها.
                ActiveRangeResult.Gap -> {
                    gaps += mark
                    inScope[el.id] = mark
                }

                is ActiveRangeResult.Wall, is ActiveRangeResult.Beam -> {
                    inScope[el.id] = mark
                }
            }

            when (InspectionStatus.from(inspections[el.id to level])) {
                // اتصبّ خلاص = خرج من القرار تماماً، حتى لو جدوله ناقص.
                // فجوة في عنصر متصبوب مسألة توثيق، مش مانع صبّ.
                InspectionStatus.CAST -> {
                    cast += mark
                    inScope.remove(el.id)
                    gaps.remove(mark)
                }
                InspectionStatus.APPROVED -> approved += mark
                InspectionStatus.REJECTED -> rejected += mark
                InspectionStatus.WIR_SUBMITTED -> pendingApproval += mark
                InspectionStatus.NONE -> notInspected += mark
            }
        }

        // ---------------------------------------------------------- الموانع

        if (rejected.isNotEmpty()) {
            findings += Finding(
                "rejected", Level.BLOCKER,
                "${rejected.size} عنصر مرفوض",
                "لازم إعادة عمل واعتماد قبل الصبّ.",
                marks = rejected.sorted()
            )
        }

        if (notInspected.isNotEmpty()) {
            findings += Finding(
                "not-inspected", Level.BLOCKER,
                "${notInspected.size} عنصر من غير فحص",
                "مفيش أي حالة فحص مسجّلة — الحديد ماتفحصش.",
                marks = notInspected.sorted()
            )
        }

        if (pendingApproval.isNotEmpty()) {
            findings += Finding(
                "pending-approval", Level.BLOCKER,
                "${pendingApproval.size} عنصر طلب فحصه مقدّم ولسه ماتعتمدش",
                "استنى اعتماد الاستشاري قبل الصبّ.",
                marks = pendingApproval.sorted()
            )
        }

        if (gaps.isNotEmpty()) {
            findings += Finding(
                "gaps", Level.BLOCKER,
                "${gaps.size} عنصر فيه فجوة في جدول التسليح",
                "الدور جوّه مدى العنصر بس مفيش صف بيغطيه — التسليح المطلوب " +
                    "غير معروف. ارفعها للمكتب الفني.",
                marks = gaps.sorted()
            )
        }

        if (unknownMark.isNotEmpty()) {
            findings += Finding(
                "unknown-mark", Level.BLOCKER,
                "${unknownMark.size} كود مش موجود في الجدول",
                "العنصر متسمّى بكود مالوش تسليح متعرّف في جدول المكتب.",
                marks = unknownMark.distinct().sorted()
            )
        }

        // ---------------------------------------------------------- التحذيرات

        if (unnamed.isNotEmpty()) {
            findings += Finding(
                "unnamed", Level.WARNING,
                "${unnamed.size} عنصر على المسقط من غير كود",
                "من غير كود مرجعي مش ممكن نتحقق من تسليحه. لو أي واحد فيهم " +
                    "داخل في الصبّة، سمّيه الأول.",
                elementIds = unnamed
            )
        }

        // عدّ الأسياخ: تحقّق الموقع مقابل الرسمة
        val countsByElement = barCounts.filter { it.level == level }.groupBy { it.elementId }
        val missingCount = inScope.filterKeys { it !in countsByElement.keys }.values.sorted()
        if (missingCount.isNotEmpty()) {
            findings += Finding(
                "no-bar-count", Level.WARNING,
                "${missingCount.size} عنصر من غير عدّ أسياخ",
                "العدّ الموثّق هو إثباتك إن المنفّذ مطابق للرسمة.",
                marks = missingCount
            )
        }

        // اختلاف بين عدّ الموقع وعدّ الرسمة لنفس القطر
        val mismatched = mutableListOf<String>()
        countsByElement.forEach { (elementId, rows) ->
            val mark = inScope[elementId] ?: return@forEach
            val site = rows.filter { it.source == com.corewall.qaqc.data.db.BarCountEntity.SOURCE_SITE }
                .groupBy { it.diameter }.mapValues { (_, r) -> r.sumOf { it.count } }
            val drawing = rows.filter { it.source == com.corewall.qaqc.data.db.BarCountEntity.SOURCE_DRAWING }
                .groupBy { it.diameter }.mapValues { (_, r) -> r.sumOf { it.count } }
            if (site.isNotEmpty() && drawing.isNotEmpty() && site != drawing) mismatched += mark
        }
        if (mismatched.isNotEmpty()) {
            findings += Finding(
                "count-mismatch", Level.WARNING,
                "${mismatched.size} عنصر عدّه في الموقع مختلف عن الرسمة",
                "راجع الاختلاف قبل الصبّ — ده بالظبط اللي العدّاد اتعمل عشانه.",
                marks = mismatched.sorted()
            )
        }

        if (photoCount == 0 && inScope.isNotEmpty()) {
            findings += Finding(
                "no-photos", Level.WARNING,
                "مفيش صور توثيق للدور",
                "صور الحديد قبل الصبّ هي إثباتك الوحيد بعد ما الخرسانة تتصبّ.",
            )
        }

        if (openTasks > 0) {
            findings += Finding(
                "open-tasks", Level.WARNING,
                "$openTasks مهمة مفتوحة في الدور",
                "راجعها — يمكن فيها حاجة تخص الصبّة.",
            )
        }

        // ---------------------------------------------------------- ملاحظات

        // تغيّر التسليح عن الدور اللي تحت: مش مانع، بس سبب وجيه لمراجعة
        // إضافية — أكتر غلطة شائعة إن الطاقم ينفّذ زي الدور اللي فات.
        val idx = logic.idx(level)
        if (idx != null && idx > 0) {
            val prev = logic.levels[idx - 1]
            val cmp = FloorComparison.compare(schedule, logic, prev, level)
            val changedInScope = cmp?.changed
                ?.map { it.mark }
                ?.filter { it in inScope.values }
                ?: emptyList()
            if (changedInScope.isNotEmpty()) {
                findings += Finding(
                    "changed-vs-prev", Level.INFO,
                    "${changedInScope.size} عنصر تسليحه مختلف عن دور $prev",
                    "متنفّذش زي الدور اللي فات — راجع القيم دي تحديداً.",
                    marks = changedInScope.sorted()
                )
            }
        }

        if (cast.isNotEmpty()) {
            findings += Finding(
                "already-cast", Level.INFO,
                "${cast.size} عنصر اتصبّ خلاص",
                "خارج نطاق القرار ده.",
                marks = cast.sorted()
            )
        }

        return Result(
            level = level,
            scopeCount = inScope.size,
            approvedCount = approved.count { it in inScope.values },
            castCount = cast.size,
            findings = findings
        )
    }

    /** ملخّص نصّي — للمشاركة وللوكيل. */
    fun summarize(r: Result): String = buildString {
        appendLine("جاهزية الصبّ — دور ${r.level}")
        appendLine(r.verdict)
        appendLine("العناصر في النطاق: ${r.scopeCount} · معتمد: ${r.approvedCount} · اتصبّ: ${r.castCount}")
        if (r.blockers.isNotEmpty()) {
            appendLine()
            appendLine("موانع (${r.blockers.size}):")
            r.blockers.forEach {
                appendLine("- ${it.title}: ${it.detail}")
                if (it.marks.isNotEmpty()) appendLine("  العناصر: ${it.marks.joinToString("، ")}")
            }
        }
        if (r.warnings.isNotEmpty()) {
            appendLine()
            appendLine("تحذيرات (${r.warnings.size}):")
            r.warnings.forEach {
                appendLine("- ${it.title}")
                if (it.marks.isNotEmpty()) appendLine("  العناصر: ${it.marks.joinToString("، ")}")
            }
        }
        if (r.notes.isNotEmpty()) {
            appendLine()
            appendLine("ملاحظات:")
            r.notes.forEach { appendLine("- ${it.title}") }
        }
    }
}
