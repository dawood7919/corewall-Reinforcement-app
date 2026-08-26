package com.corewall.qaqc.ai

import android.content.Context
import com.corewall.qaqc.ai.docs.DocumentExtractor
import com.corewall.qaqc.ai.remote.providerFor
import com.corewall.qaqc.data.db.ChatMessageDao
import com.corewall.qaqc.data.db.ChatMessageEntity
import com.corewall.qaqc.data.db.DocFactDao
import com.corewall.qaqc.data.db.DocFactEntity
import com.corewall.qaqc.data.db.DocumentDao
import com.corewall.qaqc.data.db.DocumentEntity
import com.corewall.qaqc.data.db.PromptDao
import com.corewall.qaqc.pdfengine.PdfOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * نطاق المعرفة.
 *
 * الأدوار **معزولة عزل مطلق** — دور 10 عمره ما يشوف بيانات دور 9.
 * الاستثناء الوحيد هو المكتبة المشتركة: ملفات المستخدم بيحطّها صراحة
 * في شاشة "معرفة المشروع" عشان تبقى متاحة في كل الأدوار (مواصفات،
 * أكواد، جداول عامة). الاشتراك هنا **قرار صريح من المستخدم**، مش تسريب.
 *
 * بنستخدم قيمة حارسة في عمود `level` بدل عمود جديد — كده مفيش
 * ترحيل لقاعدة البيانات، ومفيش مخاطرة كسر المخطط.
 */
object KnowledgeScope {
    const val PROJECT = "__PROJECT__"
    const val PROJECT_LABEL = "معرفة المشروع"
    fun isProject(level: String) = level == PROJECT
}

/**
 * البرومبت اللي هيتحلّل بيه المستند.
 *
 * [name] بيتخزّن على المستند عشان "حلّل تاني" يستخدم نفس البرومبت،
 * و[guidance] هو نص التعليمات نفسه.
 */
data class PromptChoice(val name: String = "", val guidance: String = "") {
    companion object {
        /** التحليل العام — من غير تعليمات مخصّصة. */
        val Default = PromptChoice()
    }
}

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
    private val appContext: Context,
    private val documentDao: DocumentDao,
    private val factDao: DocFactDao,
    private val chatDao: ChatMessageDao,
    private val promptDao: PromptDao
) {
    private companion object {
        /**
         * سقف ملاحظات الذاكرة في البرومبت.
         *
         * ١٢٠٠ حرف ≈ ٤٠٠ توكن — تمن ثابت مقبول على كل طلب. من غير سقف،
         * الذاكرة بتكبر مع الاستعمال لحد ما تبقى هي نفسها سبب البطء اللي
         * كانت المفروض تحلّه.
         */
        const val MEMORY_BUDGET = 1_200

        /**
         * سقف كتلة معرفة المستندات.
         *
         * كان ١٤ ألف حرف — أكبر جزء في الطلب كله، أكبر من شرح التطبيق
         * وكتالوج الأدوات مع بعض. والأهم إنه كان **قص** مش **اختيار**:
         * بيملا بترتيب وصول الصفوف من قاعدة البيانات وبيرمي الباقي.
         * أربعة آلاف حرف **مرتّبة بالصلة** بتجيب معلومات أحسن بربع التمن.
         */
        const val KNOWLEDGE_BUDGET = 4_000

        /** عدد المستندات في الفهرس — الباقي بيتجاب بأداة عند الحاجة. */
        const val DOC_LIST_LIMIT = 10
    }

    private val json = Json {
        ignoreUnknownKeys = true; isLenient = true
        encodeDefaults = true; explicitNulls = false
        // الموديل بيرجّع null بدل القيمة الفاضية أحياناً — نخليها القيمة الافتراضية
        // بدل ما الفك كله يفشل على حقل واحد.
        coerceInputValues = true
    }

    // ---------------------------------------------------------------- تسجيل وتحليل

    /** بيسجّل الملف كـ"بانتظار التحليل" فور رفعه (من غير شبكة). */
    suspend fun register(file: File, level: String, forceLevel: Boolean = false): Long = withContext(Dispatchers.IO) {
        documentDao.byPath(file.absolutePath)?.let { existing ->
            if (forceLevel && existing.level != level) documentDao.upsert(existing.copy(level = level))
            return@withContext existing.id
        }
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
    suspend fun analyze(
        config: AiConfig,
        docId: Long,
        knownLevels: List<String>,
        /** برومبت المستخدم لنوع المستند ده — فاضي = التحليل الافتراضي. */
        prompt: PromptChoice = PromptChoice.Default,
        preserveLevel: Boolean = false
    ): DocumentEntity? {
        val doc = withContext(Dispatchers.IO) { documentDao.byId(docId) } ?: return null
        if (!config.isConfigured) return doc

        suspend fun save(d: DocumentEntity) = withContext(Dispatchers.IO) { documentDao.upsert(d); d }
        save(doc.copy(status = "ANALYZING", error = ""))

        val file = File(doc.filePath)
        val content = runCatching {
            if (file.extension.equals("pdf", ignoreCase = true)) PdfOps.ensureInit(appContext)
            DocumentExtractor.extract(file)
        }.getOrElse { e ->
            return save(doc.copy(status = "FAILED", error = "تعذّر قراءة الملف — ${describe(e)}",
                analyzedAt = System.currentTimeMillis()))
        }
        if (content is DocumentExtractor.Content.Unsupported) {
            return save(doc.copy(status = "UNSUPPORTED", error = content.reason, analyzedAt = System.currentTimeMillis()))
        }

        val systemPrompt = AiPrompt.docSystem(knownLevels, prompt.guidance)
        val header = "اسم الملف: ${doc.fileName}\nالدور وقت الرفع: ${doc.level}\n\n"

        val raw = runCatching {
            when (content) {
                is DocumentExtractor.Content.Text ->
                    providerFor(config.provider).complete(
                        config, systemPrompt, header + "نوع المحتوى: ${content.kindHint}\n\n${content.text}"
                    )
                is DocumentExtractor.Content.Images ->
                    providerFor(config.provider).completeWithImages(
                        config, systemPrompt,
                        header + "نوع المحتوى: ${content.kindHint}. حلّل الصور المرفقة.",
                        content.base64Jpeg
                    )
                else -> ""
            }
        }.getOrElse { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e

            // انقطاع الشبكة مش فشل نهائي: الملف بيفضل في الانتظار ويتحلّل
            // لوحده أول ما النت يرجع. تعليمه "فشل" كان بيخلّي الشبكة اللي
            // قطعت لحظة تحتاج تدخّل يدوي بعدين.
            if ((e as? AiError)?.retryable == true) {
                return save(doc.copy(status = "PENDING", error = describe(e)))
            }

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

        // ربط تلقائي بالدور: لو المستند نفسه بيقول دور معروف، نستخدمه.
        // بس ملفات مكتبة المشروع بتفضل مشتركة مهما قال المستند — المستخدم
        // حطّها هناك عن قصد، والموديل مالوش حق ينقلها لدور واحد.
        val detected = extraction.level.trim()
        val finalLevel = if (preserveLevel || KnowledgeScope.isProject(doc.level)) doc.level
        else knownLevels.firstOrNull { it.equals(detected, ignoreCase = true) }
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
            analyzedAt = System.currentTimeMillis(),
            promptName = prompt.name
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

    /**
     * بيحلّل المستندات المعلّقة واحد ورا التاني.
     * لو النت واقع، بنوقف بعد أول ملف بدل ما نعدّي على الباقي —
     * كلهم هيفشلوا بنفس السبب والانتظار بيتضاعف على الفاضي.
     */
    suspend fun analyzePending(config: AiConfig, knownLevels: List<String>, max: Int = 5): Int {
        if (!config.isConfigured) return 0
        val pending = withContext(Dispatchers.IO) { documentDao.pending(max) }
        var done = 0
        for (d in pending) {
            val result = runCatching { analyze(config, d.id, knownLevels, promptFor(d.promptName)) }
            val doc = result.getOrNull()
            when {
                doc == null -> Unit
                doc.status == "DONE" -> done++
                // رجع للانتظار = مشكلة شبكة، مفيش فايدة نكمّل الدفعة دلوقتي
                doc.status == "PENDING" -> return done
            }
        }
        return done
    }

    /**
     * بيحوّل الاسم المتخزّن على المستند لبرومبت كامل.
     * لو المستخدم مسح البرومبت بعد ما اتحلّل بيه، بنرجع للعام بدل ما نفشل.
     */
    suspend fun promptFor(name: String): PromptChoice {
        if (name.isBlank()) return PromptChoice.Default
        val p = withContext(Dispatchers.IO) { promptDao.byName(name) } ?: return PromptChoice.Default
        return PromptChoice(p.name, p.body)
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

    // ---------------------------------------------------------------- دعم الوكيل

    /** المعرفة المرتبطة بسؤال — نفس الاسترجاع اللي بتستخدمه المحادثة. */
    suspend fun knowledgeFor(level: String, question: String): String = retrieve(level, question)

    /** آخر الرسائل كملخّص — ردود المساعد بتترجع بخلاصتها مش بالـJSON كله. */
    suspend fun historyDigest(level: String, take: Int = 6): String =
        withContext(Dispatchers.IO) {
            chatDao.forLevel(level).takeLast(take).joinToString("\n") {
                val who = if (it.role == "user") "المستخدم" else "المساعد"
                "$who: ${if (it.role == "user") it.content else headlineOf(it.content)}"
            }
        }

    /** بيسجّل دور محادثة كامل: سؤال المستخدم ورد الوكيل. */
    suspend fun saveTurn(level: String, question: String, answer: com.corewall.qaqc.ai.model.ChatAnswer) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            chatDao.upsert(ChatMessageEntity(level = level, role = "user", content = question, createdAt = now))
            chatDao.upsert(
                ChatMessageEntity(
                    level = level, role = "assistant",
                    content = json.encodeToString(com.corewall.qaqc.ai.model.ChatAnswer.serializer(), answer),
                    createdAt = now + 1
                )
            )
        }

    /** مستندات دور — بيستخدمها الوكيل. */
    suspend fun documentsFor(level: String): List<DocumentEntity> =
        withContext(Dispatchers.IO) { documentDao.forLevel(level) }

    /** بحث في الحقائق — **مقيّد بالدور ومكتبة المشروع**، مش كل الأدوار. */
    suspend fun searchFacts(query: String, level: String, limit: Int = 30): List<DocFactEntity> =
        withContext(Dispatchers.IO) {
            factDao.searchInScope(query, level, KnowledgeScope.PROJECT, limit)
        }

    /** مستندات الدور + مكتبة المشروع. */
    suspend fun documentsInScope(level: String): List<DocumentEntity> =
        withContext(Dispatchers.IO) { documentDao.inScope(level, KnowledgeScope.PROJECT) }

    /** مستندات مكتبة المشروع بس. */
    suspend fun projectDocuments(): List<DocumentEntity> =
        withContext(Dispatchers.IO) { documentDao.forLevel(KnowledgeScope.PROJECT) }

    suspend fun clearChat(level: String) = withContext(Dispatchers.IO) { chatDao.clearLevel(level) }

    // ---------------------------------------------------------------- الذاكرة

    /**
     * ذاكرة الوكيل.
     *
     * المشكلة اللي بتحلّها: المحادثة كانت بتتبعت كـ«آخر ٦ رسائل بخلاصتها».
     * يعني حاجة اتقالت من عشر رسائل — قرار، رقم، تفضيل — بتختفي خالص،
     * والحل السهل (نبعت المحادثة كلها) بيغلى طرديًا مع طول المحادثة لحد
     * ما كل سؤال يتكلّف زي تحليل مستند.
     *
     * فالذاكرة هنا حاجتين مختلفتين:
     *
     * 1. **المحادثة كلها متسجّلة وقابلة للبحث** ([searchChat]) — الوكيل
     *    بيدوّر فيها بأداة لما يحتاج، فمفيش حرف بيتبعت من غير داعي.
     * 2. **ملاحظات صغيرة صريحة** ([rememberNote]) — الوكيل بيكتب فيها
     *    اللي يستاهل يفضل (قرار، تفضيل، رقم مرجعي)، ودي بتتبعت كاملة
     *    في كل طلب لأنها مقفولة على [MEMORY_BUDGET] حرف.
     *
     * التقسيمة دي هي الفرق بين «ذاكرة رخيصة وناقصة» و«ذاكرة كاملة وغالية»:
     * الملخّص الصغير بيتبعت دايمًا، والباقي متاح عند الطلب.
     */
    suspend fun rememberNote(level: String, key: String, value: String) =
        withContext(Dispatchers.IO) {
            val k = key.trim()
            if (k.isNotBlank()) {
                // نفس المفتاح بيتكتب فوق القديم — من غير كده الذاكرة بتمتلي
                // بنسخ من نفس الحقيقة وكل واحدة بتاكل من الميزانية.
                chatDao.forgetByPrefix(level, "$k::")
                chatDao.upsert(
                    ChatMessageEntity(
                        level = level, role = "memory",
                        content = "$k::${value.trim().take(300)}",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

    /** ملاحظات الذاكرة كنص جاهز للبرومبت — مقفولة على ميزانية ثابتة. */
    suspend fun memoryDigest(level: String): String = withContext(Dispatchers.IO) {
        val notes = chatDao.memory(level, 40)
        if (notes.isEmpty()) return@withContext ""
        buildString {
            var used = 0
            for (n in notes) {
                val line = "- " + n.content.replace("::", ": ")
                if (used + line.length > MEMORY_BUDGET) break
                appendLine(line)
                used += line.length + 1
            }
        }.trim()
    }

    /** بحث في المحادثة كلها — النتيجة سطر لكل رسالة. */
    suspend fun searchChat(level: String, query: String, limit: Int = 12): String =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isBlank()) return@withContext ""
            val hits = chatDao.search(level, q, limit)
            if (hits.isEmpty()) return@withContext ""
            hits.reversed().joinToString("\n") {
                val who = if (it.role == "user") "المستخدم" else "المساعد"
                val body = if (it.role == "user") it.content else headlineOf(it.content)
                "$who: ${body.take(300)}"
            }
        }

    /** عدد رسائل المحادثة في دور. */
    suspend fun chatCount(level: String): Int =
        withContext(Dispatchers.IO) { chatDao.countForLevel(level) }

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

        val docs = documentDao.inScope(level, KnowledgeScope.PROJECT)
        if (docs.isEmpty()) return@withContext ""
        val byId = docs.associateBy { it.id }

        // كل الاستعلامات مقيّدة بـ(الدور الحالي + مكتبة المشروع).
        // من غير التقييد ده كانت حقائق دور تاني بتتسرّب في الإجابة.
        //
        // الدرجة = كام كلمة من كلمات السؤال طابقت الحقيقة دي.
        // الإشارة دي كانت **بتتحسب وبتترمي**: الحقيقة اللي بتطابق تلات
        // كلمات كانت بتتضاف تلات مرات في قايمة، وبعدين `distinctBy`
        // بيلغي التكرار — يعني أقوى دليل على الصلة بيتمسح.
        val scored = HashMap<String, Int>()
        val facts = HashMap<String, DocFactEntity>()
        terms.forEach { t ->
            factDao.searchInScope(t, level, KnowledgeScope.PROJECT, 60).forEach { f ->
                val k = "${f.documentId}|${f.key}|${f.value}"
                facts[k] = f
                scored[k] = (scored[k] ?: 0) + 1
            }
        }

        val ranked = scored.entries
            .sortedByDescending { it.value }
            .mapNotNull { facts[it.key] }

        buildString {
            // ① المستندات: سطر قصير لكل واحد، وعدد محدود.
            //
            // كانت ٣٠ مستند × ملخّص ١٦٠ حرف ≈ ٧٥٠٠ حرف — أكتر من نص
            // الميزانية بتتصرف على **فهرس** قبل ما أي حقيقة تتكتب.
            appendLine("المستندات المتاحة (دور $level + معرفة المشروع):")
            docs.take(DOC_LIST_LIMIT).forEach {
                val tag = if (KnowledgeScope.isProject(it.level)) "[مشروع]" else "[$level]"
                appendLine("- $tag ${it.fileName} [${it.docType}] ${it.drawingNumber}".trimEnd())
            }
            if (docs.size > DOC_LIST_LIMIT) {
                appendLine("- (+${docs.size - DOC_LIST_LIMIT} مستند تاني — استخدم list_documents)")
            }
            appendLine()

            if (ranked.isEmpty()) {
                // القديم كان بيرمي ٨٠ حقيقة عشوائية هنا لما مفيش تطابق.
                // دي مش معرفة — دي ضوضاء بتتدفع بسعر كامل وبتشتّت الموديل
                // عن اللي هو محتاجه فعلاً.
                appendLine("مفيش حقايق مستخرجة مطابقة لكلمات السؤال.")
                appendLine("لو محتاج تفاصيل مستند، استخدم get_document_facts.")
                return@buildString
            }

            // ② الحقايق بالأهم فالأهم لحد ما الميزانية تخلص.
            //
            // القص في الآخر (`take(14_000)`) كان أسوأ اختيار ممكن: بيسيب
            // اللي وصل الأول — يعني ترتيب SQLite — وبيرمي اللي بعده، حتى
            // لو كان هو الحقيقة الوحيدة اللي بتجاوب على السؤال.
            var used = length
            var shownDoc = -1L
            var dropped = 0
            for (f in ranked) {
                val d = byId[f.documentId] ?: continue
                val header = if (f.documentId != shownDoc) "من \"${d.fileName}\":\n" else ""
                val line = header + "  • ${f.kind}: ${f.key} = ${f.value} ${f.unit}".trimEnd() + "\n"
                if (used + line.length > KNOWLEDGE_BUDGET) { dropped++; continue }
                append(line)
                used += line.length
                shownDoc = f.documentId
            }
            if (dropped > 0) {
                appendLine("(+$dropped حقيقة أقل صلة — اسأل عنها بـsearch أو get_document_facts)")
            }
        }
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
        val docs = documentDao.inScope(level, KnowledgeScope.PROJECT)
        if (docs.isEmpty()) return@withContext ""
        val facts = factDao.inScope(level, KnowledgeScope.PROJECT)
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
