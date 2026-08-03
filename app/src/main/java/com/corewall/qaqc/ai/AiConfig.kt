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

/**
 * أخطاء واضحة للمستخدم بدل ما نرمي استثناء خام.
 *
 * كل خطأ بيشيل تفاصيله الحقيقية جوّه الرسالة — من غير كده بيبقى مستحيل
 * نعرف ليه التحليل فشل من على الجهاز.
 */
sealed class AiError(val userMessage: String) : Exception(userMessage) {
    data object NoKey : AiError("مفيش مفتاح API — ضيفه من الإعدادات الأول.")
    data object Timeout : AiError("الطلب أخد وقت طويل — جرّب تاني، أو قلّل حجم الملف.")

    data class Network(val detail: String = "") :
        AiError("مشكلة اتصال بالخدمة" + detail.brief())

    data class Unauthorized(val detail: String) :
        AiError("المفتاح مرفوض أو منتهي. راجع الإعدادات" + detail.brief())

    data class RateLimited(val detail: String) :
        AiError("تجاوزت حد الاستخدام — استنى شوية وجرّب تاني" + detail.brief())

    data class Server(val code: Int, val detail: String) :
        AiError("الخدمة رجّعت خطأ $code" + detail.brief())

    data class BadResponse(val detail: String) :
        AiError("رد الـ AI مش مفهوم" + detail.brief())

    /** حجم الطلب أكبر من ذاكرة الجهاز — بيحصل مع PDF كبير أو صور عالية الدقة. */
    data class TooLarge(val detail: String) :
        AiError("الملف كبير جداً على ذاكرة الجهاز" + detail.brief())

    /** أي حاجة تانية — بنعرض نوعها ورسالتها بدل ما نبلعها. */
    data class Unknown(val detail: String) : AiError("خطأ غير متوقع" + detail.brief())
}

/**
 * رسالة صالحة للعرض من أي استثناء.
 *
 * المهم هنا إن الأخطاء غير المعروفة بتوصل بنوعها ورسالتها بدل رسالة عامة
 * زي "فشل التحليل" — الرسالة العامة بتخلّي التشخيص من على الموقع مستحيل.
 */
fun Throwable.aiMessage(): String = when (this) {
    is AiError -> userMessage
    is OutOfMemoryError -> "الملف كبير جداً على ذاكرة الجهاز — جرّب ملف أصغر."
    else -> "${this::class.java.simpleName}: ${message.orEmpty().take(200)}".trim().trimEnd(':')
}

/** بيحوّل تفاصيل الخطأ لسطر قصير مقروء يتعرض جنب الرسالة. */
private fun String.brief(): String {
    val clean = trim().replace(Regex("\\s+"), " ")
    return if (clean.isBlank()) "." else ": ${clean.take(220)}"
}
