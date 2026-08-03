package com.corewall.qaqc.ai

/** مزوّدو الـ AI المدعومين — الإضافة سهلة: زوّد عنصر هنا + Provider مطابق. */
enum class AiProviderId(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
    OPENROUTER(
        label = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "anthropic/claude-sonnet-4.5"
    ),
    OPENAI(
        label = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o"
    ),
    ANTHROPIC(
        label = "Anthropic",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-sonnet-4-5"
    ),
    GEMINI(
        label = "Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.0-flash"
    );

    companion object {
        fun from(raw: String?): AiProviderId =
            entries.firstOrNull { it.name == raw } ?: OPENROUTER
    }
}

/**
 * إعدادات الـ AI. المفتاح **بيتكتب من المستخدم** ويتخزّن على الجهاز بس —
 * مفيش مفتاح متحطوط جوّه الكود ولا في الـ APK.
 */
data class AiConfig(
    val provider: AiProviderId = AiProviderId.OPENROUTER,
    val apiKey: String = "",
    val model: String = AiProviderId.OPENROUTER.defaultModel,
    val baseUrl: String = AiProviderId.OPENROUTER.defaultBaseUrl
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

/** أخطاء واضحة للمستخدم بدل ما نرمي استثناء خام. */
sealed class AiError(val userMessage: String) : Exception(userMessage) {
    data object NoKey : AiError("مفيش مفتاح API — ضيفه من الإعدادات الأول.")
    data object Network : AiError("مفيش اتصال بالإنترنت أو الخدمة مش راضية ترد.")
    data object Timeout : AiError("الطلب أخد وقت طويل — جرّب تاني.")
    data class Unauthorized(val detail: String) : AiError("المفتاح مرفوض أو منتهي. راجع الإعدادات.")
    data class RateLimited(val detail: String) : AiError("تجاوزت حد الاستخدام — استنى شوية وجرّب تاني.")
    data class Server(val code: Int, val detail: String) : AiError("الخدمة رجّعت خطأ ($code).")
    data class BadResponse(val detail: String) : AiError("رد الـ AI مش مفهوم — جرّب تاني.")
}
