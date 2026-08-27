package com.corewall.qaqc.ai.remote

import android.util.Base64
import com.corewall.qaqc.ai.AiConfig
import com.corewall.qaqc.ai.AiError
import com.corewall.qaqc.ai.AiProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * توليد صورة من وصف نصّي.
 *
 * ## تقسيمة الشغل
 *
 * موديل اللغة **مابيرسمش**. هو بيقرا نتايج الأدوات — أرقام محسوبة من
 * التطبيق — ويكتب منها وصف الصورة، وموديل الصور بياخد الوصف ويرسم.
 * فالجودة بتيجي من إن الوصف مكتوب بموديل شايف البيانات الحقيقية، مش
 * من إن المستخدم يوصف حاجة هو نفسه مشافهاش.
 *
 * ## ليه مش مزوّد واحد
 *
 * كل خدمة بتعرض التوليد بشكل مختلف تماماً، مش بفرق في الحقول:
 * - **OpenAI** — نقطة نهاية مستقلة (`/images/generations`).
 * - **OpenRouter / TokenRouter** — نفس نقطة المحادثة مع `modalities`،
 *   والصورة بترجع جوّه رسالة الرد.
 * - **Gemini** — `generateContent` مع `responseModalities`.
 * - **Anthropic** — مابتولّدش صور خالص. ده حد حقيقي، مش نقص في الكود،
 *   فالرسالة بتقولها بوضوح بدل ما الطلب يفشل برسالة عامة.
 *
 * الناتج دايماً JPEG/PNG بايتس متكتوبة في ملف جوّه مجلد الدور — يعني
 * الصورة بتفضل موجودة بعد قفل المحادثة، وبتظهر في شاشة الملفات زي أي
 * ملف تاني.
 */
object AiImageGenerator {

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * بيولّد صورة ويحفظها في [targetDir]، وبيرجّع الملف.
     *
     * @param prompt وصف الصورة — اللي موديل اللغة كتبه.
     */
    suspend fun generate(
        config: AiConfig,
        prompt: String,
        targetDir: File,
        baseName: String
    ): File = withContext(Dispatchers.IO) {
        if (!config.isConfigured) throw AiError.NoKey
        if (config.imageModel.isBlank()) {
            throw AiError.BadResponse("مفيش موديل صور متحدّد. حطّه في الإعدادات ← الذكاء الاصطناعي.")
        }

        val bytes = when (config.provider) {
            AiProviderId.OPENAI -> openAiImages(config, prompt)
            AiProviderId.OPENROUTER, AiProviderId.TOKENROUTER -> chatImages(config, prompt)
            AiProviderId.GEMINI -> geminiImages(config, prompt)
            AiProviderId.LOCAL -> throw AiError.BadResponse(
                "الموديل المحلي بيكتب نص بس — مابيرسمش صور. اختار مزوّد سحابي للصور."
            )
            AiProviderId.ANTHROPIC -> throw AiError.BadResponse(
                "Anthropic مابتولّدش صور. اختار مزوّد تاني للصور من الإعدادات " +
                    "(OpenAI أو Gemini أو OpenRouter)."
            )
        }

        targetDir.mkdirs()
        val file = File(targetDir, "$baseName.png")
        file.writeBytes(bytes)
        file
    }

    // ---------------------------------------------------------------- OpenAI

    private suspend fun openAiImages(config: AiConfig, prompt: String): ByteArray {
        val payload = buildJsonObject {
            put("model", config.imageModel)
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1024")
        }.toString()

        val raw = AiHttpClient.postJson(
            "${config.baseUrl.trimEnd('/')}/images/generations",
            payload,
            mapOf("Authorization" to "Bearer ${config.apiKey}")
        )
        val first = lenient.parseToJsonElement(raw).jsonObject["data"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw AiError.BadResponse(raw.take(300))

        first["b64_json"]?.jsonPrimitive?.content?.let { return decode(it) }
        // بعض الموديلات بترجّع رابط بدل البايتس.
        first["url"]?.jsonPrimitive?.content?.let { return AiHttpClient.getBytes(it) }
        throw AiError.BadResponse(raw.take(300))
    }

    // ------------------------------------------------- OpenRouter / TokenRouter

    private suspend fun chatImages(config: AiConfig, prompt: String): ByteArray {
        val payload = buildJsonObject {
            put("model", config.imageModel)
            putJsonArray("modalities") { add("image"); add("text") }
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            }
        }.toString()

        val headers = buildMap {
            put("Authorization", "Bearer ${config.apiKey}")
            if (config.provider == AiProviderId.OPENROUTER) {
                put("HTTP-Referer", "https://github.com/corewall-qaqc")
                put("X-Title", "CoreWall QA/QC")
            }
        }

        val raw = AiHttpClient.postJson(
            "${config.baseUrl.trimEnd('/')}/chat/completions", payload, headers
        )
        val message = lenient.parseToJsonElement(raw).jsonObject["choices"]
            ?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw AiError.BadResponse(raw.take(300))

        val url = message["images"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("image_url")?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: throw AiError.BadResponse(
                "الموديل \"${config.imageModel}\" ماردّش بصورة. اتأكد إنه موديل توليد صور."
            )
        return fromDataUrl(url)
    }

    // ---------------------------------------------------------------- Gemini

    private suspend fun geminiImages(config: AiConfig, prompt: String): ByteArray {
        val payload = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", prompt) })
                    }
                })
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("IMAGE"); add("TEXT") }
            }
        }.toString()

        val url = "${config.baseUrl.trimEnd('/')}/models/${config.imageModel}:generateContent" +
            "?key=${config.apiKey}"
        val raw = AiHttpClient.postJson(url, payload, emptyMap())

        val parts = lenient.parseToJsonElement(raw).jsonObject["candidates"]
            ?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: throw AiError.BadResponse(raw.take(300))

        parts.forEach { part ->
            part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.content
                ?.let { return decode(it) }
        }
        throw AiError.BadResponse(
            "الموديل \"${config.imageModel}\" ماردّش بصورة. اتأكد إنه موديل توليد صور."
        )
    }

    // ---------------------------------------------------------------- مساعدات

    private fun decode(b64: String): ByteArray =
        runCatching { Base64.decode(b64, Base64.DEFAULT) }
            .getOrElse { throw AiError.BadResponse("رد الصورة مش مفكوك") }

    /** `data:image/png;base64,…` أو رابط عادي. */
    private suspend fun fromDataUrl(url: String): ByteArray =
        if (url.startsWith("data:")) decode(url.substringAfter(",", ""))
        else AiHttpClient.getBytes(url)
}
