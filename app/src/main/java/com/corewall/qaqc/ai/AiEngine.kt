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
        val content = DocumentExtractor.extract(file)
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
            val msg = (e as? AiError)?.userMessage ?: "فشل التحليل"
            return save(doc.copy(status = "FAILED", error = msg, analyzedAt = System.currentTimeMillis()))
        }

        val extraction = runCatching { parseExtraction(raw) }.getOrElse {
            return save(doc.copy(status = "FAILED", error = "رد غير مفهوم من الموديل", analyzedAt = System.currentTimeMillis()))
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
            status = "DONE", error = "",
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
            .joinToString("\n") { "${if (it.role == "user") "المستخدم" else "المساعد"}: ${it.content}" }

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

        val answer = providerFor(config.provider).complete(config, AiPrompt.CHAT_SYSTEM, userMsg)
        withContext(Dispatchers.IO) {
            chatDao.upsert(
                ChatMessageEntity(level = level, role = "assistant", content = answer.trim(),
                    createdAt = System.currentTimeMillis())
            )
        }
        return answer.trim()
    }

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

    // ---------------------------------------------------------------- مساعدات

    internal fun parseExtraction(raw: String): DocExtraction {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = text.indexOf('{')
        require(start >= 0) { "no json" }
        var depth = 0; var inStr = false; var esc = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                esc -> esc = false
                c == '\\' && inStr -> esc = true
                c == '"' -> inStr = !inStr
                inStr -> Unit
                c == '{' -> depth++
                c == '}' -> { depth--; if (depth == 0) return json.decodeFromString(text.substring(start, i + 1)) }
            }
        }
        error("unbalanced json")
    }
}
