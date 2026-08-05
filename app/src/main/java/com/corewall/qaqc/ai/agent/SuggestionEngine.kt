package com.corewall.qaqc.ai.agent

import com.corewall.qaqc.data.FilesManager
import com.corewall.qaqc.data.db.DocumentEntity
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.domain.FloorComparison

/** اقتراح بيتعرض في نافذة المساعد. */
data class Suggestion(
    val id: String,
    val title: String,
    val detail: String,
    val severity: Severity,
    /** السؤال اللي هيتبعت للمساعد لو المستخدم ضغط. */
    val prompt: String
) {
    enum class Severity { CRITICAL, WARNING, INFO, IDEA }
}

/**
 * محرّك الاقتراحات الاستباقية.
 *
 * **كله حتمي — مفيش أي نداء شبكة.** ده مقصود: الاقتراحات لازم تظهر
 * فوراً، وتشتغل وإنت أوفلاين في بدروم، ومتكلّفش فلوس كل ما تفتح شاشة.
 * الـAI بيدخل بس لما تضغط على اقتراح وتطلب تفصيل.
 *
 * الترتيب بالخطورة: حاجة بتوقّف الشغل أو بتخلي سجل الجودة ناقص الأول،
 * وبعدها التحسينات.
 */
object SuggestionEngine {

    fun build(
        host: AgentHost,
        files: FilesManager,
        documents: List<DocumentEntity>
    ): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        val level = host.currentLevel
        val idx = host.logic.idx(level)

        // ---------------------------------------------- عناصر مرفوضة
        val rejected = host.planData.elements.filter {
            InspectionStatus.from(host.inspections[it.id to level]) == InspectionStatus.REJECTED
        }
        if (rejected.isNotEmpty()) {
            val marks = rejected.mapNotNull { host.names[it.id] }
            out += Suggestion(
                id = "rejected",
                title = "${rejected.size} عنصر مرفوض في دور $level",
                detail = marks.take(4).joinToString("، ").ifBlank { "عناصر من غير أسماء" } +
                    " — إعادة عمل قبل الصبّة.",
                severity = Suggestion.Severity.CRITICAL,
                prompt = "إيه العناصر المرفوضة في دور $level وإيه المطلوب فيها بالظبط؟"
            )
        }

        // ---------------------------------------------- فجوات جدول المكتب
        if (idx != null) {
            val gaps = host.schedule.allMarks.filter { m ->
                host.schedule.walls[m]?.let { host.logic.wallGapAt(it, idx) } == true ||
                    host.schedule.beams[m]?.let { host.logic.beamGapAt(it, idx) } == true
            }
            if (gaps.isNotEmpty()) {
                out += Suggestion(
                    id = "gaps",
                    title = "${gaps.size} فجوة بيانات في جدول التسليح",
                    detail = "${gaps.take(4).joinToString("، ")} — الدور جوّه مدى العنصر بس مفيش صف بيغطيه.",
                    severity = Suggestion.Severity.CRITICAL,
                    prompt = "إيه الفجوات في جدول التسليح لدور $level وإيه تأثيرها على التنفيذ؟"
                )
            }
        }

        // ---------------------------------------------- تغيّر التسليح فوق
        val next = FloorComparison.nextLevel(host.logic, level)
        if (next != null) {
            val cmp = FloorComparison.compare(host.schedule, host.logic, level, next)
            if (cmp != null && cmp.anyChange) {
                out += Suggestion(
                    id = "next-floor",
                    title = "التسليح بيتغيّر في $next",
                    detail = "${cmp.changed.size} عنصر تسليحه مختلف عن الدور ده" +
                        (if (cmp.added.isNotEmpty()) "، و${cmp.added.size} عنصر جديد بيظهر" else "") +
                        " — راجعه قبل ما تجهّز الحديد.",
                    severity = Suggestion.Severity.WARNING,
                    prompt = "قارن تسليح دور $level بدور $next وقولي إيه اللي اتغيّر بالظبط."
                )
            }
        }

        // ---------------------------------------------- عناصر من غير اسم
        val unnamed = host.planData.elements.count { host.names[it.id] == null }
        if (unnamed > 0) {
            out += Suggestion(
                id = "unnamed",
                title = "$unnamed عنصر على المسقط من غير كود",
                detail = "من غير كود مرجعي، العنصر مالوش تسليح متعرّف ومابيدخلش في أي تقرير.",
                severity = Suggestion.Severity.WARNING,
                prompt = "إيه العناصر اللي لسه من غير أكواد وإزاي أعرف كود كل واحد؟"
            )
        }

        // ---------------------------------------------- فحوصات ناقصة
        val none = host.planData.elements.count {
            InspectionStatus.from(host.inspections[it.id to level]) == InspectionStatus.NONE
        }
        val cast = host.planData.elements.count {
            InspectionStatus.from(host.inspections[it.id to level]) == InspectionStatus.CAST
        }
        if (none > 0 && cast > 0) {
            out += Suggestion(
                id = "inspection-gap",
                title = "$none عنصر من غير حالة فحص",
                detail = "فيه $cast عنصر متصبوب بالفعل في نفس الدور — سجل الجودة ناقص.",
                severity = Suggestion.Severity.WARNING,
                prompt = "إيه العناصر اللي من غير حالة فحص في دور $level؟"
            )
        }

        // ---------------------------------------------- مستندات معلّقة أو فاشلة
        val levelDocs = documents.filter { it.level == level }
        levelDocs.count { it.status == "PENDING" }.takeIf { it > 0 }?.let {
            out += Suggestion(
                id = "docs-pending",
                title = "$it مستند مستني التحليل",
                detail = "لحد ما يتحلّل، محتواه مش داخل في إجابات المساعد.",
                severity = Suggestion.Severity.INFO,
                prompt = "إيه المستندات المستنية التحليل في دور $level؟"
            )
        }
        levelDocs.count { it.status == "FAILED" }.takeIf { it > 0 }?.let {
            out += Suggestion(
                id = "docs-failed",
                title = "$it مستند فشل تحليله",
                detail = "افتح المعرفة وشوف السبب — غالباً موديل مش داعم الصور أو ملف تالف.",
                severity = Suggestion.Severity.WARNING,
                prompt = "ليه فشل تحليل المستندات في دور $level وإيه الحل؟"
            )
        }

        // ---------------------------------------------- ملفات مرفوعة من غير معرفة
        val onDisk = runCatching { files.list(files.levelDir(level)).count { it.isFile } }.getOrDefault(0)
        val known = levelDocs.size
        if (onDisk > known) {
            out += Suggestion(
                id = "unregistered",
                title = "${onDisk - known} ملف مش داخل في المعرفة",
                detail = "الملفات دي موجودة في مجلد الدور بس المساعد مايعرفش محتواها.",
                severity = Suggestion.Severity.INFO,
                prompt = "إيه الملفات اللي في دور $level ولسه ماتحللتش؟"
            )
        }

        // ---------------------------------------------- عدّ الأسياخ
        val counted = host.barCounts.filter { it.level == level }.map { it.elementId }.distinct()
        val approvedOrCast = host.planData.elements.filter {
            InspectionStatus.from(host.inspections[it.id to level]) in
                setOf(InspectionStatus.APPROVED, InspectionStatus.CAST)
        }
        if (approvedOrCast.isNotEmpty() && counted.isEmpty()) {
            out += Suggestion(
                id = "no-counts",
                title = "مفيش عدّ أسياخ مسجّل",
                detail = "فيه ${approvedOrCast.size} عنصر معتمد أو متصبوب من غير عدّ موثّق من الموقع.",
                severity = Suggestion.Severity.INFO,
                prompt = "إيه العناصر اللي محتاجة عدّ أسياخ في دور $level؟"
            )
        }

        // ---------------------------------------------- مهام متأخرة
        val overdue = host.tasks.filter {
            !it.done && it.dueDate != null && it.dueDate!! < System.currentTimeMillis()
        }
        if (overdue.isNotEmpty()) {
            out += Suggestion(
                id = "overdue",
                title = "${overdue.size} مهمة فات موعدها",
                detail = overdue.take(2).joinToString("، ") { it.title },
                severity = Suggestion.Severity.WARNING,
                prompt = "إيه المهام المتأخرة وإيه الأولوية فيها؟"
            )
        }

        // ---------------------------------------------- اقتراحات دايماً مفيدة
        if (out.size < 4) {
            out += Suggestion(
                id = "daily-report",
                title = "ولّد التقرير اليومي",
                detail = "تقرير جاهز من بيانات الدور الحالية.",
                severity = Suggestion.Severity.IDEA,
                prompt = "اعملي تقرير يومي لدور $level"
            )
            out += Suggestion(
                id = "readiness",
                title = "الدور جاهز للصبّة؟",
                detail = "افتح شاشة الجاهزية — الحكم محسوب بالكامل.",
                severity = Suggestion.Severity.IDEA,
                prompt = "هل دور $level جاهز للصبّة؟"
            )
        }

        val order = listOf(
            Suggestion.Severity.CRITICAL,
            Suggestion.Severity.WARNING,
            Suggestion.Severity.INFO,
            Suggestion.Severity.IDEA
        )
        return out.sortedBy { order.indexOf(it.severity) }.take(8)
    }
}
