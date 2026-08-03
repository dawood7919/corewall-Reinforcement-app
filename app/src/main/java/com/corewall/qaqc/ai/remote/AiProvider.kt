package com.corewall.qaqc.ai.remote

import com.corewall.qaqc.ai.AiConfig
import com.corewall.qaqc.ai.AiError
import com.corewall.qaqc.ai.AiProviderId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * نقطة تبديل المزوّد. أي مزوّد جديد = تنفيذ للواجهة دي + عنصر في [AiProviderId].
 * الواجهة بترجّع نص الرد الخام (المفروض JSON) — التحقق بيحصل في الطبقة الأعلى.
 */
interface AiProvider {
    suspend fun complete(config: AiConfig, systemPrompt: String, userContent: String): String
}

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun providerFor(id: AiProviderId): AiProvider = when (id) {
    AiProviderId.OPENROUTER, AiProviderId.OPENAI -> OpenAiCompatProvider
    AiProviderId.ANTHROPIC -> AnthropicProvider
    AiProviderId.GEMINI -> GeminiProvider
}

/**
 * OpenRouter و OpenAI (وأي خدمة متوافقة مع /chat/completions).
 * ده اللي التطبيق بيستخدمه افتراضياً.
 */
object OpenAiCompatProvider : AiProvider {
    override suspend fun complete(config: AiConfig, systemPrompt: String, userContent: String): String {
        val payload = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.2)
            // بنطلب JSON صريح — بيقلّل جداً احتمال رد نصي حر
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                add(buildJsonObject { put("role", "user"); put("content", userContent) })
            }
        }.toString()

        val headers = buildMap {
            put("Authorization", "Bearer ${config.apiKey}")
            if (config.provider == AiProviderId.OPENROUTER) {
                // OpenRouter بيطلب/بيفضّل الهيدرز دي لتعريف التطبيق
                put("HTTP-Referer", "https://github.com/corewall-qaqc")
                put("X-Title", "CoreWall QA/QC")
            }
        }

        val raw = AiHttpClient.postJson("${config.baseUrl.trimEnd('/')}/chat/completions", payload, headers)
        return extractOpenAiContent(raw)
    }

    private fun extractOpenAiContent(raw: String): String = runCatching {
        lenientJson.parseToJsonElement(raw).jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }.getOrElse { throw AiError.BadResponse(raw.take(300)) }
}

/** Anthropic Messages API. */
object AnthropicProvider : AiProvider {
    override suspend fun complete(config: AiConfig, systemPrompt: String, userContent: String): String {
        val payload = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 4096)
            put("temperature", 0.2)
            put("system", systemPrompt)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "user"); put("content", userContent) })
            }
        }.toString()

        val headers = mapOf(
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01"
        )

        val raw = AiHttpClient.postJson("${config.baseUrl.trimEnd('/')}/messages", payload, headers)
        return runCatching {
            lenientJson.parseToJsonElement(raw).jsonObject["content"]!!.jsonArray
                .first().jsonObject["text"]!!.jsonPrimitive.content
        }.getOrElse { throw AiError.BadResponse(raw.take(300)) }
    }
}

/** Google Gemini generateContent. */
object GeminiProvider : AiProvider {
    override suspend fun complete(config: AiConfig, systemPrompt: String, userContent: String): String {
        val payload = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", systemPrompt) }) }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { add(buildJsonObject { put("text", userContent) }) }
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            }
        }.toString()

        val url = "${config.baseUrl.trimEnd('/')}/models/${config.model}:generateContent?key=${config.apiKey}"
        val raw = AiHttpClient.postJson(url, payload, emptyMap())
        return runCatching {
            lenientJson.parseToJsonElement(raw).jsonObject["candidates"]!!.jsonArray
                .first().jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
                .first().jsonObject["text"]!!.jsonPrimitive.content
        }.getOrElse { throw AiError.BadResponse(raw.take(300)) }
    }
}
