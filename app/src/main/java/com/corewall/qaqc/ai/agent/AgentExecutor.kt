package com.corewall.qaqc.ai.agent

import com.corewall.qaqc.ai.AiEngine
import com.corewall.qaqc.data.FilesManager
import com.corewall.qaqc.data.model.InspectionStatus
import com.corewall.qaqc.domain.FloorComparison
import com.corewall.qaqc.domain.SteelCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * بينفّذ أدوات الوكيل على التطبيق الحقيقي ويرجّع مشاهدات نصّية.
 *
 * قاعدتين ثابتين هنا:
 * 1. **كل رقم بيطلع من هنا محسوب بكود**، مش من الموديل. الأداة بترجّع
 *    الحقيقة، والموديل بيفسّرها بس.
 * 2. **كل مسار ملف بيتحقّق منه** إنه جوّه مجلد التطبيق قبل أي عملية.
 *    من غير كده، مسار زي `../../` من الموديل ممكن يوصل لملفات بره التطبيق.
 */
class AgentExecutor(
    private val host: AgentHost,
    private val files: FilesManager,
    private val aiEngine: AiEngine
) {

    /** أقصى طول لأي مشاهدة — عشان الحلقة ماتفضاش تكبر. */
    private val maxObservation = 4_000

    suspend fun run(action: AgentAction): ToolOutcome {
        val tool = AgentTools.find(action.tool)
            ?: return ToolOutcome(action.tool, false, "أداة مش معروفة: ${action.tool}")

        return runCatching {
            when (tool.name) {
                "get_floor_summary" -> floorSummary(level(action))
                "get_element" -> element(action.str("mark"), level(action))
                "list_elements" -> listElements(level(action), action.str("status"))
                "compare_floors" -> compareFloors(
                    action.str("from"), action.str("to"), action.str("mark").ifBlank { null }
                )
                "next_floor_changes" -> nextFloorChanges(level(action))
                "get_plan_geometry" -> planGeometry(level(action))
                "steel_quantity" -> steelQuantity(action.str("callout"))
                "get_bar_counts" -> barCounts(level(action))
                "pour_readiness" -> pourReadiness(level(action))

                "list_files" -> listFiles(level(action), action.str("path"))
                "read_file" -> readFile(action.str("path"))
                "list_documents" -> listDocuments(level(action))
                "get_document_facts" -> documentFacts(action.num("documentId")?.toLong())
                "list_photos" -> listPhotos(level(action))
                "search" -> search(action.str("query"), level(action))
                "list_tasks" -> listTasks(level(action))
                "list_notes" -> listNotes(level(action))
                "get_attendance" -> attendance(level(action), action.num("days")?.toInt() ?: 14)

                "search_chat" -> searchChat(level(action), action.str("query"))
                "remember" -> remember(level(action), action.str("key"), action.str("value"))

                "open_screen" -> navigate(action.str("screen"))
                "set_level" -> changeLevel(action.str("level"))
                "open_file" -> openFile(action.str("path"))

                "add_task" -> addTask(action.str("title"), level(action), action.bool("allLevels"))
                "complete_task" -> completeTask(action.num("id")?.toLong())
                "add_note" -> addNote(action.str("title"), action.str("body"), level(action))
                "create_document" -> createDocument(action.str("title"), action.str("template"), level(action))
                "export_document_pdf" -> exportDocumentPdf(action.num("documentId")?.toLong())
                "add_comment" -> addComment(action.str("mark"), action.str("text"), level(action))
                "set_inspection" -> setInspection(action.str("mark"), action.str("status"), level(action))
                "create_folder" -> createFolder(level(action), action.str("path"), action.str("name"))
                "rename_file" -> renameFile(action.str("path"), action.str("newName"))
                "delete_file" -> deleteFile(action.str("path"))
                "delete_task" -> deleteTask(action.num("id")?.toLong())

                else -> ToolOutcome(tool.name, false, "الأداة مش متنفّذة")
            }
        }.getOrElse { e ->
            ToolOutcome(tool.name, false, "فشل التنفيذ: ${e::class.java.simpleName}: ${e.message.orEmpty()}")
        }.let { it.copy(observation = it.observation.take(maxObservation)) }
    }

    private fun level(a: AgentAction): String =
        a.str("level").ifBlank { host.currentLevel }.let { asked ->
            host.levels.firstOrNull { it.equals(asked, ignoreCase = true) } ?: host.currentLevel
        }

    // ------------------------------------------------------------ الذاكرة

    /**
     * بحث في المحادثة كلها.
     *
     * البديل — إن الوكيل يفضل شايف المحادثة كلها في كل طلب — بيغلى
     * طرديًا مع طولها. هنا مفيش حرف بيتبعت غير لما يدوّر فعلاً.
     */
    private suspend fun searchChat(level: String, query: String): ToolOutcome {
        if (query.isBlank()) return fail("search_chat", "محتاج كلمة للبحث")
        val hits = aiEngine.searchChat(level, query)
        return if (hits.isBlank()) {
            ok("search_chat", "مفيش أي رسالة سابقة فيها \"$query\" في الدور $level.")
        } else {
            ok("search_chat", "رسايل سابقة فيها \"$query\":\n$hits")
        }
    }

    private suspend fun remember(level: String, key: String, value: String): ToolOutcome {
        if (key.isBlank() || value.isBlank()) {
            return fail("remember", "محتاج key و value الاتنين")
        }
        aiEngine.rememberNote(level, key, value)
        return ok("remember", "اتسجّل في ذاكرة الدور $level — «$key: $value»")
    }

    private fun ok(tool: String, text: String, user: String = "") = ToolOutcome(tool, true, text, user)
    private fun fail(tool: String, text: String) = ToolOutcome(tool, false, text)

    // ------------------------------------------------------------ الحالة الهندسية

    private fun floorSummary(level: String): ToolOutcome {
        val idx = host.logic.idx(level) ?: return fail("get_floor_summary", "دور غير معروف: $level")
        val statuses = InspectionStatus.entries.associateWith { st ->
            host.planData.elements.count { InspectionStatus.from(host.inspections[it.id to level]) == st }
        }
        val gaps = host.schedule.allMarks.filter { m ->
            host.schedule.walls[m]?.let { host.logic.wallGapAt(it, idx) } == true ||
                host.schedule.beams[m]?.let { host.logic.beamGapAt(it, idx) } == true
        }
        val text = buildString {
            appendLine("دور $level (ترتيب ${idx + 1} من ${host.levels.size})")
            appendLine("عناصر المسقط: ${host.planData.elements.size}")
            appendLine("متسمّى: ${host.names.size}")
            statuses.forEach { (st, n) -> appendLine("${st.label}: $n") }
            appendLine("فجوات بيانات: ${gaps.size}${if (gaps.isEmpty()) "" else " → ${gaps.joinToString("، ")}"}")
            appendLine("مهام مفتوحة: ${host.tasks.count { it.level == level && !it.done }}")
            appendLine("كومنتات: ${host.comments.count { it.level == level }}")
            appendLine("صور موقع: ${host.sitePhotos.count { it.level == level }}")
        }
        return ok("get_floor_summary", text)
    }

    private fun element(mark: String, level: String): ToolOutcome {
        if (mark.isBlank()) return fail("get_element", "لازم تحدّد mark")
        val elementId = host.elementIdForMark(mark)
        val text = buildString {
            appendLine("العنصر: $mark — دور $level")
            when (val r = host.logic.activeRange(host.schedule, mark, level)) {
                is com.corewall.qaqc.domain.ActiveRangeResult.Wall -> {
                    val w = r.row
                    appendLine("النوع: حائط")
                    appendLine("المدى: من ${w.from} لحد ${w.to ?: "أعلى المبنى"} (غير شامل النهاية)")
                    appendLine("السُمك: ${w.w}mm")
                    appendLine("رأسي V: ${w.v}")
                    appendLine("أفقي H: ${w.h}")
                    appendLine("أطراف T: ${w.t}")
                    if (w.edited) appendLine("⚠ الصف ده متعدّل يدوياً عن جدول المكتب")
                    w.note?.let { appendLine("ملاحظة الجدول: $it") }
                }
                is com.corewall.qaqc.domain.ActiveRangeResult.Beam -> {
                    val b = r.row
                    appendLine("النوع: كمرة")
                    appendLine("المدى: من ${b.from} لحد ${b.to ?: b.from} (شامل النهاية)")
                    appendLine("المقطع: ${b.w}×${b.d}mm")
                    appendLine("سفلي B: ${b.bottom.joinToString(" / ")}")
                    appendLine("علوي T: ${b.top.joinToString(" / ")}")
                    appendLine("جانبي: ${b.side}")
                    appendLine("كانات: ${b.links}")
                    if (b.edited) appendLine("⚠ الصف ده متعدّل يدوياً عن جدول المكتب")
                    b.note?.let { appendLine("ملاحظة الجدول: $it") }
                }
                com.corewall.qaqc.domain.ActiveRangeResult.Gap ->
                    appendLine("⚠ فجوة بيانات: الدور جوّه مدى العنصر بس مفيش صف بيغطيه في الجدول")
                com.corewall.qaqc.domain.ActiveRangeResult.OutOfRange ->
                    appendLine("العنصر ده مش موجود في الدور ده (بره مداه) — وده طبيعي")
                com.corewall.qaqc.domain.ActiveRangeResult.UnknownMark ->
                    appendLine("مفيش كود بالاسم ده في الجدول")
                com.corewall.qaqc.domain.ActiveRangeResult.UnknownLevel ->
                    appendLine("دور غير معروف")
            }
            if (elementId != null) {
                val st = InspectionStatus.from(host.inspections[elementId to level])
                appendLine("حالة الفحص: ${st.label}")
                val cs = host.comments.filter { it.elementId == elementId && it.level == level }
                appendLine("كومنتات: ${cs.size}")
                cs.take(5).forEach { appendLine("  · ${it.text}") }
                val bc = host.barCounts.filter { it.elementId == elementId && it.level == level }
                if (bc.isNotEmpty()) {
                    appendLine("عدّ الأسياخ من الموقع:")
                    bc.forEach { appendLine("  · ${it.source}: ${it.count} سيخ قطر ${it.diameter}mm") }
                }
            } else {
                appendLine("(مفيش عنصر على المسقط متسمّى بالكود ده)")
            }
            appendLine("الأدوار اللي فيها فجوة لنفس العنصر: " +
                host.logic.gapLevels(host.schedule, mark).joinToString("، ").ifBlank { "مفيش" })
        }
        return ok("get_element", text)
    }

    private fun listElements(level: String, statusFilter: String): ToolOutcome {
        val wanted = statusFilter.trim().uppercase().takeIf { it.isNotBlank() }
        val rows = host.planData.elements.mapNotNull { el ->
            val mark = host.names[el.id]
            val st = InspectionStatus.from(host.inspections[el.id to level])
            if (wanted != null && st.name != wanted) return@mapNotNull null
            "${mark ?: "(بدون اسم)"} · ${el.category} · ${st.label}"
        }
        return ok(
            "list_elements",
            "عناصر دور $level (${rows.size}):\n" + rows.take(120).joinToString("\n")
        )
    }

    private fun compareFloors(from: String, to: String, mark: String?): ToolOutcome {
        val f = host.levels.firstOrNull { it.equals(from, true) }
            ?: return fail("compare_floors", "دور غير معروف: $from")
        val t = host.levels.firstOrNull { it.equals(to, true) }
            ?: return fail("compare_floors", "دور غير معروف: $to")
        val r = FloorComparison.compare(host.schedule, host.logic, f, t, mark)
            ?: return fail("compare_floors", "تعذّرت المقارنة")
        return ok("compare_floors", renderComparison(r))
    }

    private fun nextFloorChanges(level: String): ToolOutcome {
        val next = FloorComparison.nextLevel(host.logic, level)
            ?: return ok("next_floor_changes", "دور $level هو آخر دور — مفيش دور بعده.")
        val r = FloorComparison.compare(host.schedule, host.logic, level, next)
            ?: return fail("next_floor_changes", "تعذّرت المقارنة")
        return ok("next_floor_changes", renderComparison(r))
    }

    private fun renderComparison(r: FloorComparison.Result): String = buildString {
        appendLine("مقارنة تسليح: ${r.fromLevel} ← ${r.toLevel}")
        appendLine(
            if (r.anyChange) "فيه تغييرات: ${r.changed.size} اتعدّل، ${r.added.size} ظهر، ${r.removed.size} اختفى"
            else "مفيش أي تغيير في التسليح بين الدورين"
        )
        appendLine("عناصر من غير تغيير: ${r.sameCount}")
        if (r.changed.isNotEmpty()) {
            appendLine()
            appendLine("اتعدّل:")
            r.changed.take(25).forEach { c ->
                appendLine("- ${c.mark} (${if (c.isWall) "حائط" else "كمرة"}):")
                c.changes.forEach { appendLine("    ${it.field}: ${it.before} ← ${it.after}") }
            }
        }
        if (r.added.isNotEmpty()) appendLine("ظهر في ${r.toLevel}: " + r.added.joinToString("، ") { it.mark })
        if (r.removed.isNotEmpty()) appendLine("اختفى بعد ${r.fromLevel}: " + r.removed.joinToString("، ") { it.mark })
        if (r.gaps.isNotEmpty()) appendLine("⚠ فجوات بيانات: " + r.gaps.joinToString("، ") { it.mark })
    }

    private fun planGeometry(level: String): ToolOutcome {
        val text = buildString {
            appendLine("هندسة المسقط — ${host.planData.elements.size} عنصر (viewBox ${host.planData.viewBox})")
            appendLine("id | الاسم | الفئة | x | y | عرض | ارتفاع | حالة الفحص")
            host.planData.elements.take(120).forEach { el ->
                val st = InspectionStatus.from(host.inspections[el.id to level])
                appendLine(
                    "${el.id} | ${host.names[el.id] ?: "-"} | ${el.category} | " +
                        "${el.x.toInt()} | ${el.y.toInt()} | ${el.width.toInt()} | ${el.height.toInt()} | ${st.label}"
                )
            }
        }
        return ok("get_plan_geometry", text)
    }

    private fun steelQuantity(callout: String): ToolOutcome {
        if (callout.isBlank()) return fail("steel_quantity", "لازم تبعت callout")
        val parts = SteelCalculator.parseList(callout)
            ?: return fail("steel_quantity", "الكولاوت \"$callout\" مش بصيغة مفهومة (المتوقع T25-200 أو 6T32)")
        val text = buildString {
            appendLine("الكولاوت: $callout")
            var totalPerM = 0.0
            var totalAbs = 0.0
            parts.forEach { p ->
                when (p) {
                    is com.corewall.qaqc.domain.CalloutResult.Spaced -> {
                        totalPerM += p.areaPerMeterMm2
                        appendLine("- قطر ${p.diaMm}mm كل ${p.spacingMm}mm → ${p.totalDescription}")
                    }
                    is com.corewall.qaqc.domain.CalloutResult.Counted -> {
                        totalAbs += p.totalAreaMm2
                        appendLine("- ${p.count} سيخ قطر ${p.diaMm}mm → ${p.totalDescription}")
                    }
                }
            }
            if (totalPerM > 0) appendLine("الإجمالي: %.0f mm²/م طولي".format(totalPerM))
            if (totalAbs > 0) appendLine("الإجمالي: %.0f mm²".format(totalAbs))
        }
        return ok("steel_quantity", text)
    }

    private fun barCounts(level: String): ToolOutcome {
        val rows = host.barCounts.filter { it.level == level }
        if (rows.isEmpty()) return ok("get_bar_counts", "مفيش عدّ أسياخ مسجّل في دور $level")
        val text = buildString {
            appendLine("عدّ الأسياخ — دور $level (${rows.size} صف)")
            rows.groupBy { it.elementId }.forEach { (id, list) ->
                appendLine("${host.names[id] ?: id}:")
                list.forEach { appendLine("  ${it.source}: ${it.count} × قطر ${it.diameter}mm") }
            }
        }
        return ok("get_bar_counts", text)
    }

    /**
     * الجاهزية بتتحسب بنفس الدالة اللي الشاشة بتعرضها — مصدر حقيقة واحد.
     * لو الوكيل جمّع الإجابة من أدوات منفصلة، ممكن يخالف الشاشة، والمهندس
     * يشوف حكمين مختلفين لنفس الدور.
     */
    private fun pourReadiness(level: String): ToolOutcome {
        val r = com.corewall.qaqc.domain.PourReadiness.evaluate(
            level = level,
            elements = host.planData.elements,
            names = host.names,
            inspections = host.inspections,
            schedule = host.schedule,
            logic = host.logic,
            barCounts = host.barCounts,
            photoCount = host.sitePhotos.count { it.level == level },
            openTasks = host.tasks.count { it.level == level && !it.done }
        )
        return ok("pour_readiness", com.corewall.qaqc.domain.PourReadiness.summarize(r))
    }

    // ------------------------------------------------------------ الملفات والمعرفة

    /** بيتأكد إن المسار جوّه مجلد التطبيق — الحماية الوحيدة من مسار خبيث. */
    private fun safeFile(path: String): File? {
        if (path.isBlank()) return null
        val f = File(path).canonicalFile
        val root = files.root.canonicalFile
        return if (f.path == root.path || f.path.startsWith(root.path + File.separator)) f else null
    }

    private fun listFiles(level: String, sub: String): ToolOutcome {
        val base = files.levelDir(level)
        val dir = if (sub.isBlank()) base else File(base, sub)
        val safe = safeFile(dir.path) ?: return fail("list_files", "مسار بره مجلد التطبيق")
        if (!safe.isDirectory) return fail("list_files", "المسار مش مجلد: ${safe.name}")
        val entries = files.list(safe)
        val text = buildString {
            appendLine("مجلد: ${safe.absolutePath}")
            appendLine("عدد العناصر: ${entries.size}")
            entries.take(80).forEach { f ->
                appendLine(
                    "${if (f.isDirectory) "[مجلد]" else "[ملف]"} ${f.name} | " +
                        "${files.sizeOf(f)} بايت | ${f.absolutePath}"
                )
            }
        }
        return ok("list_files", text)
    }

    private suspend fun readFile(path: String): ToolOutcome = withContext(Dispatchers.IO) {
        val f = safeFile(path) ?: return@withContext fail("read_file", "مسار بره مجلد التطبيق")
        if (!f.isFile) return@withContext fail("read_file", "الملف مش موجود")
        when (val c = com.corewall.qaqc.ai.docs.DocumentExtractor.extract(f)) {
            is com.corewall.qaqc.ai.docs.DocumentExtractor.Content.Text ->
                ok("read_file", "محتوى ${f.name}:\n${c.text}")
            is com.corewall.qaqc.ai.docs.DocumentExtractor.Content.Images ->
                ok(
                    "read_file",
                    "${f.name} ملف مصوّر (${c.base64Jpeg.size} صفحة). مش بيتقري كنص هنا — " +
                        "شوف list_documents لو اتحلّل بالـAI."
                )
            is com.corewall.qaqc.ai.docs.DocumentExtractor.Content.Unsupported ->
                fail("read_file", c.reason)
        }
    }

    private suspend fun listDocuments(level: String): ToolOutcome {
        val docs = aiEngine.documentsInScope(level)
        if (docs.isEmpty()) return ok("list_documents", "مفيش مستندات مسجّلة في دور $level")
        val text = buildString {
            appendLine("المستندات المتاحة (دور $level + معرفة المشروع) — ${docs.size}:")
            docs.forEach { d ->
                val scope = if (com.corewall.qaqc.ai.KnowledgeScope.isProject(d.level)) "مشترك" else "دور ${d.level}"
                append("#${d.id} [$scope] ${d.fileName} [${d.docType}] حالة=${d.status}")
                if (d.drawingNumber.isNotBlank()) append(" رسمة=${d.drawingNumber}")
                if (d.revision.isNotBlank()) append(" مراجعة=${d.revision}")
                appendLine()
                if (d.summary.isNotBlank()) appendLine("   ${d.summary.take(220)}")
                appendLine("   المسار: ${d.filePath}")
            }
        }
        return ok("list_documents", text)
    }

    private suspend fun documentFacts(id: Long?): ToolOutcome {
        if (id == null) return fail("get_document_facts", "لازم تبعت documentId")
        val facts = aiEngine.factsFor(id)
        if (facts.isEmpty()) return ok("get_document_facts", "مفيش حقائق مستخرجة من المستند ده")
        val text = buildString {
            appendLine("حقائق المستند #$id (${facts.size}):")
            facts.groupBy { it.kind }.forEach { (kind, list) ->
                appendLine("$kind:")
                list.take(60).forEach { appendLine("  ${it.key} = ${it.value} ${it.unit}".trimEnd()) }
            }
        }
        return ok("get_document_facts", text)
    }

    private fun listPhotos(level: String): ToolOutcome {
        val photos = host.sitePhotos.filter { it.level == level }
        if (photos.isEmpty()) return ok("list_photos", "مفيش صور موقع في دور $level")
        val text = buildString {
            appendLine("صور موقع دور $level (${photos.size}):")
            photos.take(40).forEach {
                appendLine("- ${File(it.filePath).name} | ${it.comment.ifBlank { "من غير تعليق" }} | ${it.filePath}")
            }
        }
        return ok("list_photos", text)
    }

    private suspend fun search(query: String, level: String): ToolOutcome {
        if (query.isBlank()) return fail("search", "لازم تبعت query")
        val q = query.trim()
        val hits = mutableListOf<String>()

        host.tasks.filter { it.title.contains(q, true) || it.notes.contains(q, true) }
            .take(10).forEach { hits += "مهمة (${it.level}): ${it.title}" }
        host.notes.filter { it.title.contains(q, true) || it.body.contains(q, true) }
            .take(10).forEach { hits += "ملاحظة (${it.level}): ${it.title.ifBlank { it.body.take(60) }}" }
        host.comments.filter { it.text.contains(q, true) }
            .take(10).forEach { hits += "كومنت (${it.level}) على ${host.names[it.elementId] ?: it.elementId}: ${it.text}" }
        host.schedule.allMarks.filter { it.contains(q, true) }
            .take(10).forEach { hits += "كود عنصر: $it" }

        runCatching {
            aiEngine.documentsInScope(level).filter {
                it.fileName.contains(q, true) || it.summary.contains(q, true) ||
                    it.drawingNumber.contains(q, true)
            }.take(10).forEach { hits += "مستند #${it.id}: ${it.fileName}" }
        }
        runCatching {
            aiEngine.searchFacts(q, level, 20).forEach {
                val tag = if (com.corewall.qaqc.ai.KnowledgeScope.isProject(it.level)) "معرفة المشروع" else it.kind
                hits += "حقيقة ($tag): ${it.key} = ${it.value} ${it.unit}".trimEnd()
            }
        }
        runCatching {
            files.list(files.levelDir(level)).filter { it.name.contains(q, true) }
                .take(10).forEach { hits += "ملف: ${it.name} | ${it.absolutePath}" }
        }

        return ok(
            "search",
            if (hits.isEmpty()) "مفيش نتايج لـ\"$q\"" else "نتايج \"$q\" (${hits.size}):\n" + hits.joinToString("\n")
        )
    }

    private fun listTasks(level: String): ToolOutcome {
        val rows = host.tasks.filter { it.level == level }
        if (rows.isEmpty()) return ok("list_tasks", "مفيش مهام في دور $level")
        return ok(
            "list_tasks",
            "مهام دور $level (${rows.size}):\n" + rows.joinToString("\n") {
                "#${it.id} [${if (it.done) "خلصت" else "مفتوحة"}] ${it.title}" +
                    if (it.notes.isNotBlank()) " — ${it.notes.take(80)}" else ""
            }
        )
    }

    private fun listNotes(level: String): ToolOutcome {
        val rows = host.notes.filter { it.level == level }
        if (rows.isEmpty()) return ok("list_notes", "مفيش ملاحظات في دور $level")
        return ok(
            "list_notes",
            "ملاحظات دور $level (${rows.size}):\n" + rows.take(20).joinToString("\n") {
                "- ${it.title.ifBlank { "(بدون عنوان)" }}: ${it.body.take(140)}"
            }
        )
    }

    private fun attendance(level: String, days: Int): ToolOutcome {
        val labels = host.attendanceFileLabels()
        if (labels.isEmpty()) return ok("get_attendance", "مفيش ملفات حضور مسجّلة")
        val cutoff = System.currentTimeMillis() - days.toLong() * 24 * 3600 * 1000
        val rows = host.dailyAttendance.filter { it.date >= cutoff }.sortedByDescending { it.date }
        if (rows.isEmpty()) return ok("get_attendance", "مفيش سجلات حضور في آخر $days يوم")
        val text = buildString {
            appendLine("الحضور — آخر $days يوم (${rows.size} سجل)")
            val totalWorkers = rows.sumOf { it.workers }
            val totalAll = rows.sumOf { it.workers + it.foremen + it.engineers + it.supervisors }
            appendLine("إجمالي أيام-عامل: $totalWorkers، إجمالي كل الفئات: $totalAll")
            appendLine("متوسط يومي: ${if (rows.isNotEmpty()) totalAll / rows.size else 0}")
            appendLine()
            rows.take(20).forEach { d ->
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(d.date))
                appendLine(
                    "$date | ${labels[d.fileId] ?: "ملف #${d.fileId}"} | عمال ${d.workers} " +
                        "· مراقبين ${d.foremen} · مهندسين ${d.engineers} · مشرفين ${d.supervisors}" +
                        (if (d.overtimeHours > 0) " · إضافي ${d.overtimeHours}س" else "")
                )
            }
        }
        return ok("get_attendance", text)
    }

    // ------------------------------------------------------------ التنقّل

    private fun navigate(screen: String): ToolOutcome =
        if (host.openScreen(screen.trim().uppercase()))
            ok("open_screen", "اتفتحت شاشة $screen", "فتحت لك $screen")
        else fail("open_screen", "شاشة غير معروفة: $screen")

    private fun changeLevel(level: String): ToolOutcome {
        val match = host.levels.firstOrNull { it.equals(level.trim(), true) }
            ?: return fail("set_level", "دور غير معروف: $level")
        host.setLevel(match)
        return ok("set_level", "الدور الشغّال بقى $match", "غيّرت الدور لـ$match")
    }

    private fun openFile(path: String): ToolOutcome {
        val f = safeFile(path) ?: return fail("open_file", "مسار بره مجلد التطبيق")
        if (!f.exists()) return fail("open_file", "الملف مش موجود")
        return if (host.openFile(f.absolutePath))
            ok("open_file", "اتفتح ${f.name}", "فتحت ${f.name}")
        else fail("open_file", "تعذّر فتح الملف")
    }

    // ------------------------------------------------------------ الكتابة

    private suspend fun addTask(title: String, level: String, allLevels: Boolean): ToolOutcome {
        if (title.isBlank()) return fail("add_task", "لازم عنوان للمهمة")
        val text = title.trim()

        // "اعمل المهمة دي في كل دور" كان مستحيل: الأداة بتاخد دور واحد،
        // والجولة سقفها أربع إجراءات — والمشروع ٤٨ دور. إجراء واحد بيلفّ
        // على الأدوار كلها يعني كارت موافقة واحد كمان، مش ٤٨.
        if (!allLevels) {
            return if (host.addTask(text, level)) {
                ok("add_task", "اتضافت المهمة", "ضفت مهمة: $text")
            } else fail("add_task", "تعذّرت الإضافة")
        }

        val levels = host.levels
        if (levels.isEmpty()) return fail("add_task", "مفيش أدوار في المشروع")
        var done = 0
        for (lv in levels) if (host.addTask(text, lv)) done++
        return if (done == 0) fail("add_task", "تعذّرت الإضافة في أي دور")
        else ok(
            "add_task",
            "اتضافت المهمة في $done دور من ${levels.size}",
            "ضفت «$text» في $done دور"
        )
    }

    private suspend fun completeTask(id: Long?): ToolOutcome {
        if (id == null) return fail("complete_task", "لازم id")
        return if (host.completeTask(id)) ok("complete_task", "تمت المهمة", "علّمت المهمة #$id كمكتملة")
        else fail("complete_task", "تعذّر إتمام المهمة")
    }

    private suspend fun addNote(title: String, body: String, level: String): ToolOutcome {
        if (title.isBlank()) return fail("add_note", "لازم عنوان للملاحظة")
        return if (host.addNote(title.trim(), body.trim(), level))
            ok("add_note", "اتضافت الملاحظة", "ضفت ملاحظة: $title")
        else fail("add_note", "تعذّر إنشاء الملاحظة")
    }

    private suspend fun createDocument(title: String, template: String, level: String): ToolOutcome {
        if (title.isBlank()) return fail("create_document", "لازم عنوان للتقرير")
        val key = template.trim().uppercase()
        if (key !in setOf("QUALITY", "TAKEOFF", "DAILY", "MEETING", "LETTER")) {
            return fail("create_document", "القالب غير متاح: $template")
        }
        val id = host.createCreativeDocument(title.trim(), key, level)
        return if (id != null) ok("create_document", "اتعملت مسودة التقرير", "أنشأت مسودة #$id: $title")
        else fail("create_document", "تعذّر إنشاء مسودة التقرير")
    }

    private suspend fun exportDocumentPdf(documentId: Long?): ToolOutcome {
        if (documentId == null) return fail("export_document_pdf", "لازم رقم المستند")
        val path = host.exportCreativeDocumentPdf(documentId)
        return if (path != null) ok("export_document_pdf", "تم تصدير PDF", "تم حفظ نسخة PDF: $path")
        else fail("export_document_pdf", "تعذّر تصدير PDF")
    }

    private suspend fun addComment(mark: String, text: String, level: String): ToolOutcome {
        if (text.isBlank()) return fail("add_comment", "لازم نص للكومنت")
        val id = host.elementIdForMark(mark) ?: return fail("add_comment", "مفيش عنصر بالكود $mark")
        return if (host.addComment(id, text.trim(), level))
            ok("add_comment", "اتضاف الكومنت على $mark", "ضفت كومنت على $mark")
        else fail("add_comment", "تعذّرت الإضافة")
    }

    private suspend fun setInspection(mark: String, status: String, level: String): ToolOutcome {
        val st = InspectionStatus.entries.firstOrNull { it.name.equals(status.trim(), true) }
            ?: return fail("set_inspection", "حالة غير معروفة: $status")
        val id = host.elementIdForMark(mark) ?: return fail("set_inspection", "مفيش عنصر بالكود $mark")
        return if (host.setInspection(id, st.name, level))
            ok("set_inspection", "حالة $mark بقت ${st.label}", "غيّرت حالة $mark لـ${st.label}")
        else fail("set_inspection", "تعذّر التغيير")
    }

    private fun createFolder(level: String, path: String, name: String): ToolOutcome {
        if (name.isBlank()) return fail("create_folder", "لازم اسم للمجلد")
        val base = files.levelDir(level)
        val parent = if (path.isBlank()) base else File(base, path)
        val safe = safeFile(parent.path) ?: return fail("create_folder", "مسار بره مجلد التطبيق")
        return if (files.createFolder(safe, name))
            ok("create_folder", "اتعمل مجلد $name", "عملت مجلد: $name")
        else fail("create_folder", "تعذّر إنشاء المجلد (يمكن موجود)")
    }

    private fun renameFile(path: String, newName: String): ToolOutcome {
        val f = safeFile(path) ?: return fail("rename_file", "مسار بره مجلد التطبيق")
        if (!f.exists()) return fail("rename_file", "الملف مش موجود")
        return if (files.rename(f, newName))
            ok("rename_file", "اتغيّر الاسم لـ$newName", "غيّرت اسم ${f.name} لـ$newName")
        else fail("rename_file", "تعذّرت إعادة التسمية (يمكن الاسم موجود)")
    }

    private fun deleteFile(path: String): ToolOutcome {
        val f = safeFile(path) ?: return fail("delete_file", "مسار بره مجلد التطبيق")
        if (!f.exists()) return fail("delete_file", "الملف مش موجود")
        val name = f.name
        return if (files.delete(f)) ok("delete_file", "اتمسح $name", "مسحت $name")
        else fail("delete_file", "تعذّر الحذف")
    }

    private suspend fun deleteTask(id: Long?): ToolOutcome {
        if (id == null) return fail("delete_task", "لازم id")
        return if (host.deleteTask(id)) ok("delete_task", "اتمسحت المهمة", "مسحت المهمة #$id")
        else fail("delete_task", "تعذّر الحذف")
    }
}
