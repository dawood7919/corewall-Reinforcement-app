package com.corewall.qaqc.ai

import com.corewall.qaqc.ai.docs.DocumentExtractor
import com.corewall.qaqc.ai.remote.providerFor
import com.corewall.qaqc.data.db.ChatMessageDao
import com.corewall.qaqc.data.db.ChatMessageEntity
import com.corewall.qaqc.data.db.DocFactDao
import com.corewall.qaqc.data.db.DocFactEntity
import com.corewall.qaqc.data.db.DocumentDao
import com.corewall.qaqc.data.db.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** نتيجة تحليل مستند — الموديل بيرجّعها JSON. */
@Serializable
data class DocExtraction(
    val docType: String = "OTHER",
    val title: String = "",
    val drawingNumber: String = "",
    val revision: String = "",
    val discipline: String = "",
    val company: String = "",
    val engineer: String = "",
    val date: String = "",
    /** كود الدور لو ظاهر في المستند — بيتربط تلقائياً. */
    val level: String = "",
    val summary: String = "",
    val facts: List<ExtractedFact> = emptyList()
)

@Serializable
data class ExtractedFact(
    val kind: String = "OTHER",
    val key: String = "",
    val value: String = "",
    val unit: String = "",
    val numeric: Double = 0.0
)

/**
 * عقل التطبيق: بيحلّل أي ملف يترفع، يحوّله لمعرفة منظّمة مرتبطة بالدور،
 * وبيجاوب أسئلة هندسية بالاعتماد على كل المعرفة دي.
 *
 * مفيش أي اتصال بالشبكة من غير مفتاح API.
 */
class AiEngine(
    private val documentDao: DocumentDao,
    private val factDao: DocFactDao,
    private val chatDao: ChatMessageDao
) {
    private val json = Json {
        ignoreUnknownKeys = true; isLenient = true
        encodeDefaults = true; explicitNulls = false
        // الموديل بيرجّع null بدل القيمة الفاضية أحياناً — نخليها القيمة الافتراضية
        // بدل ما الفك كله يفشل على حقل واحد.
        coerceInputValues = true
    }

    // ---------------------------------------------------------------- تسجيل وتحليل

    /** بيسجّل الملف كـ"بانتظار التحليل" فور رفعه (من غير شبكة). */
    suspend fun register(file: File, level: String): Long = withContext(Dispatchers.IO) {
        documentDao.byPath(file.absolutePath)?.let { return@withContext it.id }
        val now = System.currentTimeMillis()
        documentDao.upsert(
            DocumentEntity(
                filePath = file.absolutePath, fileName = file.name, level = level,
                docType = "OTHER", title = file.nameWithoutExtension,
                drawingNumber = "", revision = "", discipline = "", company = "",
                engineer = "", docDate = "", summary = "",
                status = "PENDING", error = "", analyzedAt = 0L, createdAt = now
            )
        )
    }

    /**
     * بيحلّل مستند: استخراج المحتوى ← الموديل ← معرفة منظّمة + ربط بالدور.
     * بيرجّع الحالة النهائية.
     */
    suspend fun analyze(config: AiConfig, docId: Long, knownLevels: List<String>): DocumentEntity? {
        val doc = withContext(Dispatchers.IO) { documentDao.byId(docId) } ?: return null
        if (!config.isConfigured) return doc

        suspend fun save(d: DocumentEntity) = withContext(Dispatchers.IO) { documentDao.upsert(d); d }
        save(doc.copy(status = "ANALYZING", error = ""))

        val file = File(doc.filePath)
        val content = runCatching { DocumentExtractor.extract(file) }.getOrElse { e ->
            return save(doc.copy(status = "FAILED", error = "تعذّر قراءة الملف — ${describe(e)}",
                analyzedAt = System.currentTimeMillis()))
        }
        if (content is DocumentExtractor.Content.Unsupported) {
            return save(doc.copy(status = "UNSUPPORTED", error = content.reason, analyzedAt = System.currentTimeMillis()))
        }

        val prompt = AiPrompt.docSystem(knownLevels)
        val header = "اسم الملف: ${doc.fileName}\nالدور وقت الرفع: ${doc.level}\n\n"

        val raw = runCatching {
            when (content) {
                is DocumentExtractor.Content.Text ->
                    providerFor(config.provider).complete(
                        config, prompt, header + "نوع المحتوى: ${content.kindHint}\n\n${content.text}"
                    )
                is DocumentExtractor.Content.Images ->
                    providerFor(config.provider).completeWithImages(
                        config, prompt,
                        header + "نوع المحتوى: ${content.kindHint}. حلّل الصور المرفقة.",
                        content.base64Png
                    )
                else -> ""
            }
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            // الملفات المصوّرة محتاجة موديل بيشوف — ده أشهر سبب للرفض
            val visionHint = if (content is DocumentExtractor.Content.Images && e is AiError.Server)
                " • الملف ده بيتبعت كصور، فلازم موديل داعم للرؤية (vision) — غيّر الموديل من إعدادات الـAI."
            else ""
            return save(doc.copy(status = "FAILED", error = describe(e) + visionHint,
                analyzedAt = System.currentTimeMillis()))
        }

        val (extraction, truncated) = runCatching { parseExtraction(raw) }.getOrElse {
            val msg = if (raw.isBlank()) "الموديل رجّع رد فاضي — يمكن مش داعم تحليل الصور"
            else "رد غير مفهوم من الموديل: ${peek(raw)}"
            return save(doc.copy(status = "FAILED", error = msg, analyzedAt = System.currentTimeMillis()))
        }

        // ربط تلقائي بالدور: لو المستند نفسه بيقول دور معروف، نستخدمه
        val detected = extraction.level.trim()
        val finalLevel = knownLevels.firstOrNull { it.equals(detected, ignoreCase = true) }
            ?: knownLevels.firstOrNull { detected.isNotBlank() && it.contains(detected, ignoreCase = true) }
            ?: doc.level

        val updated = doc.copy(
            level = finalLevel,
            docType = extraction.docType.ifBlank { "OTHER" },
            title = extraction.title.ifBlank { doc.fileName },
            drawingNumber = extraction.drawingNumber,
            revision = extraction.revision,
            discipline = extraction.discipline,
            company = extraction.company,
            engineer = extraction.engineer,
            docDate = extraction.date,
            summary = extraction.summary,
            status = "DONE",
            // بنحفظ اللي وصل، بس بنقول إنه ناقص بدل ما نسيبه يبان كامل
            error = if (truncated)
                "الرد اتقطع — البيانات جزئية (${extraction.facts.size} عنصر). اضغط \"حلّل تاني\" لو ناقص."
            else "",
            analyzedAt = System.currentTimeMillis()
        )
        withContext(Dispatchers.IO) {
            documentDao.upsert(updated)
            factDao.deleteForDocument(docId)
            val facts = extraction.facts
                .filter { it.key.isNotBlank() }
                .take(400)
                .map {
                    DocFactEntity(
                        documentId = docId, level = finalLevel,
                        kind = it.kind.ifBlank { "OTHER" }, key = it.key.trim(),
                        value = it.value, unit = it.unit, numericValue = it.numeric
                    )
                }
            if (facts.isNotEmpty()) factDao.upsertAll(facts)
        }
        return updated
    }

    /** بيحلّل كل المستندات المعلّقة واحد ورا التاني. */
    suspend fun analyzePending(config: AiConfig, knownLevels: List<String>, max: Int = 5): Int {
        if (!config.isConfigured) return 0
        val pending = withContext(Dispatchers.IO) { documentDao.pending(max) }
        var done = 0
        pending.forEach { d ->
            runCatching { analyze(config, d.id, knownLevels) }.onSuccess { done++ }
        }
        return done
    }

    // ---------------------------------------------------------------- المحادثة

    suspend fun history(level: String): List<ChatMessageEntity> =
        withContext(Dispatchers.IO) { chatDao.forLevel(level) }

    /** يرجّع المستند لحالة PENDING عشان يتحلّل تاني. */
    suspend fun reset(docId: Long) = withContext(Dispatchers.IO) {
        documentDao.byId(docId)?.let {
            documentDao.upsert(it.copy(status = "PENDING", error = ""))
            factDao.deleteForDocument(docId)
        }
        Unit
    }

    /** الحقائق المستخرجة من مستند (للعرض). */
    suspend fun factsFor(docId: Long): List<DocFactEntity> =
        withContext(Dispatchers.IO) { factDao.forDocument(docId) }

    suspend fun clearChat(level: String) = withContext(Dispatchers.IO) { chatDao.clearLevel(level) }

    /**
     * سؤال هندسي. بنجمع سياق حقيقي (المشروع + معرفة المستندات + الحقائق
     * المرتبطة بالسؤال) وبنبعته مع السؤال — من غير هلوسة أرقام.
     */
    suspend fun ask(
        config: AiConfig,
        level: String,
        question: String,
        projectSnapshot: String
    ): String {
        if (!config.isConfigured) throw AiError.NoKey

        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            chatDao.upsert(ChatMessageEntity(level = level, role = "user", content = question, createdAt = now))
        }

        val knowledge = retrieve(level, question)
        val past = withContext(Dispatchers.IO) { chatDao.forLevel(level) }
            .takeLast(8)
            .joinToString("\n") {
                val who = if (it.role == "user") "المستخدم" else "المساعد"
                // ردود المساعد متخزّنة JSON — بنبعت الخلاصة بس بدل الكود كله
                "$who: ${if (it.role == "user") it.content else headlineOf(it.content)}"
            }

        val userMsg = buildString {
            appendLine("### حالة المشروع (محسوبة من التطبيق)")
            appendLine(projectSnapshot)
            appendLine()
            appendLine("### معرفة المستندات المرتبطة بالسؤال")
            appendLine(knowledge.ifBlank { "(مفيش مستندات محلّلة تخص السؤال)" })
            appendLine()
            if (past.isNotBlank()) { appendLine("### آخر الرسائل"); appendLine(past); appendLine() }
            appendLine("### السؤال")
            append(question)
        }

        val raw = providerFor(config.provider).complete(config, AiPrompt.CHAT_SYSTEM, userMsg)
        // بنخزّن الـJSON زي ما هو — الشاشة هي اللي بترسمه كروت ورسوم.
        // لو الموديل ردّ نص حر، بنلفّه في بلوك نصي عشان العرض يفضل موحّد.
        val stored = normalizeAnswer(raw)
        withContext(Dispatchers.IO) {
            chatDao.upsert(
                ChatMessageEntity(level = level, role = "assistant", content = stored,
                    createdAt = System.currentTimeMillis())
            )
        }
        return stored
    }

    /**
     * بيضمن إن اللي بيتخزّن دايماً [ChatAnswer] صالح.
     * الموديل ساعات بيتجاهل المخطط ويرد نص — بدل ما نعرضه خام،
     * بنحطّه في بلوك TEXT فالشاشة عندها شكل واحد تتعامل معاه.
     */
    private fun normalizeAnswer(raw: String): String {
        val parsed = runCatching {
            val obj = JsonRepair.extractObject(raw) ?: error("no json")
            json.decodeFromString(com.corewall.qaqc.ai.model.ChatAnswer.serializer(), obj.json)
        }.getOrNull()

        val answer = when {
            parsed == null || (parsed.headline.isBlank() && parsed.blocks.isEmpty()) ->
                com.corewall.qaqc.ai.model.ChatAnswer(
                    headline = "",
                    blocks = listOf(
                        com.corewall.qaqc.ai.model.AnswerBlock(type = "TEXT", body = raw.trim())
                    )
                )
            else -> parsed
        }
        return json.encodeToString(com.corewall.qaqc.ai.model.ChatAnswer.serializer(), answer)
    }

    /** الخلاصة من رد متخزّن — للسياق ولمعاينة المحادثة. */
    private fun headlineOf(stored: String): String = runCatching {
        val a = json.decodeFromString(com.corewall.qaqc.ai.model.ChatAnswer.serializer(), stored)
        a.headline.ifBlank { a.blocks.firstOrNull { it.body.isNotBlank() }?.body.orEmpty() }
    }.getOrElse { stored }.take(400)

    /**
     * استرجاع بسيط وفعّال: بندوّر بكلمات السؤال في الحقائق والمستندات.
     * أدق من التخمين وأرخص بكتير من إرسال كل حاجة.
     */
    private suspend fun retrieve(level: String, question: String): String = withContext(Dispatchers.IO) {
        val terms = question
            .split(Regex("[^\\p{L}\\p{N}\\-Ø.]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .take(12)

        val hits = LinkedHashMap<Long, MutableList<DocFactEntity>>()
        terms.forEach { t ->
            factDao.search(t, 60).forEach { f ->
                hits.getOrPut(f.documentId) { mutableListOf() }.add(f)
            }
        }
        // لو مفيش تطابق، هات حقائق الدور الحالي كسياق عام
        if (hits.isEmpty()) {
            factDao.forLevel(level).take(80).forEach { f ->
                hits.getOrPut(f.documentId) { mutableListOf() }.add(f)
            }
        }

        val docs = documentDao.getAll().associateBy { it.id }
        buildString {
            // كل المستندات المعروفة (عشان أسئلة زي "إيه الرسومات الموجودة")
            val known = documentDao.forLevel(level).take(30)
            if (known.isNotEmpty()) {
                appendLine("مستندات دور $level:")
                known.forEach {
                    appendLine("- ${it.fileName} [${it.docType}] ${it.drawingNumber} ${it.revision} — ${it.summary.take(160)}")
                }
                appendLine()
            }
            hits.entries.take(12).forEach { (docId, facts) ->
                val d = docs[docId] ?: return@forEach
                appendLine("من \"${d.fileName}\" (${d.docType}, دور ${d.level}):")
                facts.distinctBy { it.key + it.value }.take(40).forEach {
                    appendLine("  • ${it.kind}: ${it.key} = ${it.value} ${it.unit}".trimEnd())
                }
            }
        }.take(14_000)
    }

    // ---------------------------------------------------------------- الداشبورد الديناميكي

    /**
     * الـ AI بيقرّر الداشبورد بنفسه حسب البيانات المتاحة.
     * بيتخزّن في نفس جدول الكاش بمفتاح مختلف — من غير أي تعديل في قاعدة البيانات
     * (تفادياً لمخاطر الـ migration).
     */
    suspend fun buildDashboard(
        config: AiConfig,
        level: String,
        projectSnapshot: String,
        cache: com.corewall.qaqc.data.db.AiAnalysisDao
    ): Pair<com.corewall.qaqc.ai.model.DashboardSpec, Long> {
        if (!config.isConfigured) throw AiError.NoKey
        val knowledge = knowledgeDigest(level)
        val user = buildString {
            appendLine("### بيانات الدور (محسوبة من التطبيق)")
            appendLine(projectSnapshot)
            appendLine()
            appendLine("### معرفة المستندات المرفوعة")
            appendLine(knowledge.ifBlank { "(مفيش مستندات محلّلة)" })
        }
        val raw = providerFor(config.provider).complete(config, AiPrompt.DASHBOARD_SYSTEM, user)
        val spec = parseJson(raw, com.corewall.qaqc.ai.model.DashboardSpec.serializer())
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            cache.upsert(
                com.corewall.qaqc.data.db.AiAnalysisEntity(
                    level = dashKey(level),
                    json = json.encodeToString(com.corewall.qaqc.ai.model.DashboardSpec.serializer(), spec),
                    model = config.model,
                    createdAt = now
                )
            )
        }
        return spec to now
    }

    suspend fun cachedDashboard(
        level: String,
        cache: com.corewall.qaqc.data.db.AiAnalysisDao
    ): Pair<com.corewall.qaqc.ai.model.DashboardSpec, Long>? = withContext(Dispatchers.IO) {
        val row = cache.getForLevel(dashKey(level)) ?: return@withContext null
        val spec = runCatching {
            json.decodeFromString(com.corewall.qaqc.ai.model.DashboardSpec.serializer(), row.json)
        }.getOrNull() ?: return@withContext null
        spec to row.createdAt
    }

    private fun dashKey(level: String) = "dash::$level"

    // ---------------------------------------------------------------- توليد المستندات

    /** بيولّد تقرير هندسي (يومي/أسبوعي/فحص/مواد/تعليمات) من بيانات حقيقية. */
    suspend fun generateReport(
        config: AiConfig,
        level: String,
        kind: com.corewall.qaqc.ai.model.ReportKind,
        projectSnapshot: String
    ): com.corewall.qaqc.ai.model.GeneratedReport {
        if (!config.isConfigured) throw AiError.NoKey
        val knowledge = knowledgeDigest(level)
        val user = buildString {
            appendLine("نوع التقرير المطلوب: ${kind.label} — ${kind.prompt}")
            appendLine()
            appendLine("### بيانات الدور (محسوبة من التطبيق — استخدمها زي ما هي)")
            appendLine(projectSnapshot)
            appendLine()
            appendLine("### معرفة المستندات")
            appendLine(knowledge.ifBlank { "(مفيش مستندات محلّلة)" })
        }
        // التقرير Markdown مش JSON — لازم نطفي وضع الـJSON عند المزوّد
        val md = providerFor(config.provider).complete(config, AiPrompt.REPORT_SYSTEM, user, expectJson = false)
        return com.corewall.qaqc.ai.model.GeneratedReport(
            title = kind.label,
            markdown = md.trim(),
            generatedAt = System.currentTimeMillis(),
            kind = kind.name
        )
    }

    /** ملخّص كل معرفة الدور — بيتحطّ في سياق الداشبورد والتقارير. */
    private suspend fun knowledgeDigest(level: String): String = withContext(Dispatchers.IO) {
        val docs = documentDao.forLevel(level)
        if (docs.isEmpty()) return@withContext ""
        val facts = factDao.forLevel(level)
        buildString {
            docs.take(25).forEach { d ->
                append("- ${d.fileName} [${d.docType}]")
                if (d.drawingNumber.isNotBlank()) append(" ${d.drawingNumber}")
                if (d.revision.isNotBlank()) append(" ${d.revision}")
                appendLine(": ${d.summary.take(200)}")
            }
            if (facts.isNotEmpty()) {
                appendLine()
                appendLine("حقائق مستخرجة (${facts.size}):")
                facts.groupBy { it.kind }.forEach { (kind, list) ->
                    appendLine("  $kind: " + list.take(40).joinToString("، ") { "${it.key}=${it.value}${it.unit}" })
                }
            }
        }.take(12_000)
    }

    /** فكّ JSON عام مع تحمّل علامات markdown والردود المقطوعة. */
    private fun <T> parseJson(raw: String, serializer: kotlinx.serialization.KSerializer<T>): T {
        val obj = JsonRepair.extractObject(raw) ?: throw AiError.BadResponse(peek(raw))
        return runCatching { json.decodeFromString(serializer, obj.json) }
            .getOrElse { throw AiError.BadResponse(peek(raw)) }
    }

    // ---------------------------------------------------------------- مساعدات

    /**
     * سبب الفشل زي ما هو. الأخطاء المعروفة بترجّع رسالتها الجاهزة،
     * وأي حاجة تانية بترجّع نوعها ورسالتها — عمرنا ما نخبّي السبب
     * ورا رسالة عامة، لأن ساعتها مفيش طريقة نعرف بيها المشكلة من الموقع.
     */
    private fun describe(e: Throwable): String = e.aiMessage()

    /** بيرجّع الاستخراج مع علامة إن الرد كان مقطوع (بيانات جزئية). */
    internal fun parseExtraction(raw: String): Pair<DocExtraction, Boolean> {
        val obj = JsonRepair.extractObject(raw) ?: error("مفيش JSON في الرد")
        return json.decodeFromString<DocExtraction>(obj.json) to obj.repaired
    }

    /**
     * لقطة من أول الرد وآخره — الآخر هو اللي بيوضّح إن الرد اتقطع،
     * وده مكانش باين لما كنا بنعرض البداية بس.
     */
    private fun peek(raw: String): String {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        return if (s.length <= 260) s else "${s.take(160)} … ${s.takeLast(80)}"
    }
}
