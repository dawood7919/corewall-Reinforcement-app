package com.corewall.qaqc.ai.agent

import com.corewall.qaqc.data.FilesManager
import com.corewall.qaqc.data.db.DocumentEntity
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.domain.AttentionDiff
import com.corewall.qaqc.domain.FloorComparison

/**
 * لقطة حيّة من حالة التطبيق، بتتبعت مع **كل** طلب.
 *
 * ده اللي بيخلّي الوكيل "شايف التطبيق طول الوقت" من غير ما يستدعي أدوات
 * عشان يعرف إيه الموجود أصلاً. مقصود إنها **مختصرة**: الملخّصات والأعداد
 * بس، والتفاصيل بتتجاب بأداة لما يحتاجها. لو حطّينا كل حاجة هنا، كل سؤال
 * هيتكلّف زي تحليل مستند كامل.
 */
object AppSnapshot {

    private const val MAX_CHARS = 6_000

    fun build(
        host: AgentHost,
        files: FilesManager,
        documents: List<DocumentEntity>,
        onScreen: String
    ): String = buildString {
        val level = host.currentLevel
        val logic = host.logic
        val idx = logic.idx(level)

        appendLine("## حالة التطبيق دلوقتي")
        appendLine("- الشاشة المفتوحة: $onScreen")
        appendLine("- الدور الشغّال: $level" + (idx?.let { " (رقم ${it + 1} من ${host.levels.size})" } ?: ""))
        appendLine("- كل الأدوار من تحت لفوق: ${host.levels.joinToString(" ← ")}")
        FloorComparison.nextLevel(logic, level)?.let { appendLine("- الدور اللي بعده مباشرة: $it") }
        logic.idx(level)?.takeIf { it > 0 }?.let { appendLine("- الدور اللي قبله: ${host.levels[it - 1]}") }
        appendLine()

        // ---------------------------------------- العناصر والفحوصات
        val named = host.names
        val total = host.planData.elements.size
        appendLine("## عناصر الدور")
        appendLine("- عناصر المسقط: $total، متسمّى منها: ${named.size}، من غير اسم: ${total - named.size}")

        val statusCounts = InspectionStatus.entries.associateWith { st ->
            host.planData.elements.count { el ->
                val s = host.inspections[el.id to level]
                InspectionStatus.from(s) == st
            }
        }
        // نفس السبب: الحالات اللي عدّها صفر مابتضيفش معلومة.
        appendLine(
            "- حالات الفحص: " + statusCounts.entries.filter { it.value > 0 }
                .joinToString("، ") { (st, n) -> "${st.label} $n" }
                .ifBlank { "مفيش أي فحص مسجّل" }
        )
        statusCounts[InspectionStatus.REJECTED]?.takeIf { it > 0 }?.let {
            appendLine("- ⚠ فيه $it عنصر مرفوض في الدور ده")
        }

        // فجوات بيانات في جدول المكتب
        val gapMarks = host.schedule.allMarks.filter { m ->
            idx != null && (
                host.schedule.walls[m]?.let { logic.wallGapAt(it, idx) } == true ||
                    host.schedule.beams[m]?.let { logic.beamGapAt(it, idx) } == true
                )
        }
        if (gapMarks.isNotEmpty()) {
            appendLine("- ⚠ فجوات بيانات في الجدول (${gapMarks.size}): ${gapMarks.take(12).joinToString("، ")}")
        }
        appendLine()

        // ---------------------------------------- الفروق مع الجيران
        val attention = runCatching { AttentionDiff.attentionFor(host.schedule, logic, level) }
            .getOrDefault(emptyList())
        val changingUp = attention.count { it.vsNext.isNotEmpty() }
        appendLine("## التغيّر بين الأدوار")
        appendLine("- عناصر تسليحها مختلف عن الدور اللي قبله: ${attention.count { it.vsPrev.isNotEmpty() }}")
        appendLine("- عناصر تسليحها هيتغيّر في الدور اللي بعده: $changingUp")
        appendLine()

        // ---------------------------------------- عدّ الأسياخ
        val counts = host.barCounts.filter { it.level == level }
        if (counts.isNotEmpty()) {
            val elements = counts.map { it.elementId }.distinct().size
            appendLine("## عدّ الأسياخ")
            appendLine("- مسجّل عدّ لـ$elements عنصر (${counts.size} صف)")
            appendLine()
        }

        // ---------------------------------------- الملفات والمعرفة
        val levelFiles = runCatching { files.list(files.levelDir(level)) }.getOrDefault(emptyList())
        appendLine("## الملفات والمعرفة")
        appendLine(
            "- ملفات الدور: ${levelFiles.count { it.isFile }} ملف، " +
                "${levelFiles.count { it.isDirectory }} مجلد"
        )
        if (levelFiles.isNotEmpty()) {
            appendLine("  " + levelFiles.take(10).joinToString("، ") { it.name + if (it.isDirectory) "/" else "" })
        }
        val levelDocs = documents.filter { it.level == level }
        appendLine(
            "- مستندات متحلّلة: ${levelDocs.count { it.status == "DONE" }}" +
                "، مستنية: ${levelDocs.count { it.status == "PENDING" }}" +
                "، فشلت: ${levelDocs.count { it.status == "FAILED" }}"
        )
        levelDocs.filter { it.status == "DONE" }.take(8).forEach {
            append("  · ${it.fileName} [${it.docType}]")
            if (it.drawingNumber.isNotBlank()) append(" ${it.drawingNumber}")
            appendLine()
        }
        appendLine()

        // ---------------------------------------- الشغل اليومي
        val levelTasks = host.tasks.filter { it.level == level }
        val levelNotes = host.notes.filter { it.level == level }
        val photos = host.sitePhotos.filter { it.level == level }
        // السطور اللي قيمتها صفر مابتتكتبش.
        //
        // "مهام: 0 · ملاحظات: 0 · صور: 0" بتتدفع بسعر كامل في كل طلب
        // وبتقول حاجة الوكيل يقدر يستنتجها من غيابها. الدور الفاضي —
        // وده أغلب الأدوار — كان بيصرف السطور دي على لا حاجة.
        val comments = host.comments.count { it.level == level }
        val daily = listOfNotNull(
            levelTasks.takeIf { it.isNotEmpty() }
                ?.let { "- مهام: ${it.size} (مفتوحة ${it.count { t -> !t.done }})" },
            levelNotes.size.takeIf { it > 0 }?.let { "- ملاحظات: $it" },
            photos.size.takeIf { it > 0 }?.let { "- صور موقع: $it" },
            comments.takeIf { it > 0 }?.let { "- كومنتات على العناصر: $it" }
        )
        if (daily.isEmpty()) {
            appendLine("## الشغل اليومي: مفيش مهام ولا ملاحظات ولا صور في الدور ده")
        } else {
            appendLine("## الشغل اليومي")
            daily.forEach { appendLine(it) }
        }

        val labels = host.attendanceFileLabels()
        if (labels.isNotEmpty()) {
            val recent = host.dailyAttendance.sortedByDescending { it.date }.take(1).firstOrNull()
            appendLine("- ملفات حضور: ${labels.size}" + (recent?.let {
                " · آخر يوم مسجّل فيه ${it.workers + it.foremen + it.engineers + it.supervisors} فرد"
            } ?: ""))
        }
    }.take(MAX_CHARS)
}
