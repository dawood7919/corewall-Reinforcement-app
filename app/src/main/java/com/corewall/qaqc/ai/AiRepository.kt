package com.corewall.qaqc.ai

import com.corewall.qaqc.ai.model.AiAnalysis
import com.corewall.qaqc.ai.model.FloorContext
import com.corewall.qaqc.ai.remote.providerFor
import com.corewall.qaqc.data.db.AiAnalysisDao
import com.corewall.qaqc.data.db.AiAnalysisEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * تنسيق طبقة الـ AI:
 * لقطة الدور (محسوبة) ← المزوّد ← تحقّق من الرد ← كاش في Room.
 *
 * مفيش أي اتصال بالشبكة من غير مفتاح API — الميزة متوقفة تماماً افتراضياً.
 */
class AiRepository(private val dao: AiAnalysisDao) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** آخر تحليل متخزّن للدور (للعرض الفوري/الأوفلاين). */
    suspend fun cachedFor(level: String): Pair<AiAnalysis, AiAnalysisEntity>? =
        withContext(Dispatchers.IO) {
            val row = dao.getForLevel(level) ?: return@withContext null
            val parsed = runCatching { json.decodeFromString<AiAnalysis>(row.json) }.getOrNull()
                ?: return@withContext null
            parsed to row
        }

    /**
     * بيولّد تحليل جديد ويخزّنه. بيرمي [AiError] بأسباب مفهومة.
     */
    suspend fun analyze(config: AiConfig, context: FloorContext): Pair<AiAnalysis, Long> {
        if (!config.isConfigured) throw AiError.NoKey

        val floorJson = json.encodeToString(FloorContext.serializer(), context)
        val raw = providerFor(config.provider)
            .complete(config, AiPrompt.SYSTEM, AiPrompt.userMessage(floorJson))

        val analysis = parseAnalysis(raw)
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            dao.upsert(
                AiAnalysisEntity(
                    level = context.level,
                    json = json.encodeToString(AiAnalysis.serializer(), analysis),
                    model = config.model,
                    createdAt = now
                )
            )
        }
        return analysis to now
    }

    suspend fun clear(level: String) = withContext(Dispatchers.IO) { dao.deleteForLevel(level) }

    /**
     * بعض الموديلات بتلفّ الـ JSON في ```json ... ``` أو بتزوّد كلام قبله/بعده —
     * بنستخرج أول كائن JSON متزن قبل ما نفكّه.
     */
    internal fun parseAnalysis(raw: String): AiAnalysis {
        val cleaned = extractJsonObject(raw) ?: throw AiError.BadResponse(raw.take(300))
        val parsed = runCatching { json.decodeFromString<AiAnalysis>(cleaned) }
            .getOrElse { throw AiError.BadResponse(cleaned.take(300)) }
        if (parsed.isEmpty) throw AiError.BadResponse("رد فاضي")
        return parsed.copy(healthScore = parsed.healthScore.coerceIn(0, 100))
    }

    private fun extractJsonObject(raw: String): String? {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
