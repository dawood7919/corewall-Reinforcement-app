package com.corewall.qaqc.ai.remote

import com.corewall.qaqc.ai.AiConfig
import com.corewall.qaqc.ai.AiError
import com.corewall.qaqc.ai.AiProviderId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
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
    /**
     * [expectJson] بيشغّل وضع الـJSON عند المزوّد. لازم يبقى false للطلبات
     * اللي بترجّع نص (التقارير بـMarkdown) — غير كده المزوّد بيجبر الرد
     * يبقى JSON والمستخدم بيشوف كود بدل المستند.
     */
    suspend fun complete(
        config: AiConfig,
        systemPrompt: String,
        userContent: String,
        expectJson: Boolean = true
    ): String

    /**
     * نفس الطلب بس مع صور (base64 JPEG) — للـPDF وصور الموقع.
     * الافتراضي بيرجّع للنص العادي لو المزوّد مش داعم الرؤية.
     */
    suspend fun completeWithImages(
        config: AiConfig,
        systemPrompt: String,
        userContent: String,
        imagesBase64: List<String>
    ): String = complete(config, systemPrompt, userContent)
}

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** سقف طول الرد. مستندات الـBBS بترجّع مئات الصفوف، والافتراضي بيقطعها. */
private const val MAX_TOKENS = 8000

fun providerFor(id: AiProviderId): AiProvider = when (id) {
    // TokenRouter بتتكلم نفس صيغة OpenAI (/chat/completions + Bearer)،
    // فبتمشي على نفس المزوّد من غير كود مخصوص.
    AiProviderId.OPENROUTER, AiProviderId.OPENAI, AiProviderId.TOKENROUTER -> OpenAiCompatProvider
    AiProviderId.ANTHROPIC -> AnthropicProvider
    AiProviderId.GEMINI -> GeminiProvider
}

/**
 * OpenRouter و OpenAI (وأي خدمة متوافقة مع /chat/completions).
 * ده اللي التطبيق بيستخدمه افتراضياً.
 */
object OpenAiCompatProvider : AiProvider {
    override suspend fun complete(
        config: AiConfig,
        systemPrompt: String,
        userContent: String,
        expectJson: Boolean
    ): String {
        val payload = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.2)
            // من غير سقف صريح بعض المزوّدين بيقطعوا الرد عند حد منخفض،
            // فالـJSON بيوصل ناقص — ده كان سبب فشل تحليل ملفات BBS.
            put("max_tokens", MAX_TOKENS)
            // بنطلب JSON صريح — بيقلّل جداً احتمال رد نصي حر
            if (expectJson) putJsonObject("response_format") { put("type", "json_object") }
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

    override suspend fun completeWithImages(
        config: AiConfig,
        systemPrompt: String,
        userContent: String,
        imagesBase64: List<String>
    ): String {
        val payload = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.2)
            put("max_tokens", MAX_TOKENS)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject { put("type", "text"); put("text", userContent) })
                        imagesBase64.forEach { b64 ->
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$b64") }
                            })
                        }
                    }
                })
            }
        }.toString()

        val headers = buildMap {
            put("Authorization", "Bearer ${config.apiKey}")
            if (config.provider == AiProviderId.OPENROUTER) {
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
    override suspend fun complete(
        config: AiConfig,
        systemPrompt: String,
        userContent: String,
        expectJson: Boolean
    ): String {
        val payload = buildJsonObject {
            put("model", config.model)
            put("max_tokens", MAX_TOKENS)
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

/**
 * Google Gemini generateContent (Google AI Studio).
 *
 * ملاحظة مهمة: الكلاس ده كان **مش** مغطّي [completeWithImages]، فكان بيرجع
 * للنسخة النصّية. ومعنى كده إن الـPDF — اللي التطبيق بيحوّله صور — كان
 * بيوصل لـGemini من غير أي صورة، بس ومعاه جملة "حلّل الصور المرفقة".
 * الموديل كان بيلاقي نفسه مطلوب منه يحلّل حاجة مش شايفها، فبيخمّن.
 * ده كان سبب إن تحليل الملفات بيطلع غلط على Gemini.
 */
object GeminiProvider : AiProvider {

    override suspend fun complete(
        config: AiConfig,
        systemPrompt: String,
        userContent: String,
        expectJson: Boolean
    ): String = send(config, systemPrompt, expectJson) {
        add(buildJsonObject { put("text", userContent) })
    }

    override suspend fun completeWithImages(
        config: AiConfig,
        systemPrompt: String,
        userContent: String,
        imagesBase64: List<String>
    ): String = send(config, systemPrompt, expectJson = true) {
        add(buildJsonObject { put("text", userContent) })
        imagesBase64.forEach { b64 ->
            add(buildJsonObject {
                putJsonObject("inlineData") {
                    // الصور بتتولّد JPEG في DocumentExtractor.toBase64
                    put("mimeType", "image/jpeg")
                    put("data", b64)
                }
            })
        }
    }

    /** الجسم واحد في الحالتين — الفرق بس في محتوى `parts`. */
    private suspend fun send(
        config: AiConfig,
        systemPrompt: String,
        expectJson: Boolean,
        parts: JsonArrayBuilder.() -> Unit
    ): String {
        val payload = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", systemPrompt) }) }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts", parts)
                })
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                // من غير السقف ده Gemini بيقطع الرد عند حد افتراضي منخفض،
                // فالـJSON بيوصل ناقص — نفس اللي حصل مع المزوّدين التانيين.
                put("maxOutputTokens", MAX_TOKENS)
                if (expectJson) put("responseMimeType", "application/json")
            }
        }.toString()

        // المفتاح في هيدر مش في الـURL: لو الطلب اتسجّل في لوج أو بروكسي
        // الـURL بيتسجّل معاه، والمفتاح بيبقى مكشوف.
        val url = "${config.baseUrl.trimEnd('/')}/models/${config.model}:generateContent"
        val raw = AiHttpClient.postJson(url, payload, mapOf("x-goog-api-key" to config.apiKey))
        return runCatching {
            lenientJson.parseToJsonElement(raw).jsonObject["candidates"]!!.jsonArray
                .first().jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
                .first { it.jsonObject.containsKey("text") }
                .jsonObject["text"]!!.jsonPrimitive.content
        }.getOrElse { throw AiError.BadResponse(raw.take(300)) }
    }
}
