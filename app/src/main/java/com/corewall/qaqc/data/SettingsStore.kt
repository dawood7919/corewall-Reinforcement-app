package com.corewall.qaqc.data

import android.content.Context
import com.corewall.qaqc.ai.AiConfig
import com.corewall.qaqc.ai.AiProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AppTheme(val label: String) {
    IOS_LIGHT("Aurora فاتح"),
    DARK_OLED("Aurora دارك"),
    BLUEPRINT("Blueprint هندسي")
}

data class AppSettings(
    val theme: AppTheme = AppTheme.IOS_LIGHT,
    val showNames: Boolean = true,
    val showStatuses: Boolean = true,
    /**
     * الحبر للقلم بس.
     *
     * لما يبقى شغّال، الرسم على الرسمة بيبقى من الـS Pen (أو أي قلم
     * متوافق) لوحده، والصباع بيفضل للتمرير والتكبير. مقفول افتراضياً
     * عشان الأجهزة اللي مالهاش قلم — لو اتفتح عليها الرسم هيبقى مستحيل.
     */
    val stylusOnly: Boolean = false,

    /**
     * مؤشّر افتراضي بدل اللمس المباشر.
     *
     * اللمس المباشر بيخلّي الصباع نفسه مغطّي النقطة اللي بتحطها — وده
     * مقبول مع قلم رفيع، ومستحيل بالصباع على تفصيلة صغيرة. لما يبقى
     * شغّال، السحب بيحرّك علامة على الشاشة والنقطة بتتحط عند العلامة،
     * فاللي بتحطه بيفضل مكشوف قدامك وانت بتحطه.
     */
    val virtualCursor: Boolean = false,

    // ---- حصر الكميات: مظهر الرسم ----
    // كلها قابلة للتحكّم من شاشة إعدادات الحصر — عشان كل جهاز وكل رسمة
    // ليها كثافة تفاصيل مختلفة، ومفيش مقاس واحد يناسب الكل.
    /** سُمك خطوط الأشكال على الشاشة (نقاط بكسل، مش نقط PDF). */
    val takeoffStrokeWidth: Float = 3.6f,
    /** نصف قطر علامة العدّ/العمود. */
    val takeoffMarkerRadius: Float = 8.5f,
    /** مضاعف حجم خط الأسماء والكميات فوق الأشكال. */
    val takeoffTextScale: Float = 1f,
    /** نصف قطر الالتقاط للرأس القريب، بنقط PDF. */
    val takeoffSnapRadiusPt: Float = 14f
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * إعدادات الـ AI في ملف منفصل — مستثنى من النسخ الاحتياطي السحابي
     * (backup_rules.xml) عشان مفتاح الـ API ميطلعش بره الجهاز.
     */
    private val aiPrefs = context.getSharedPreferences("ai_secret", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AppSettings(
            theme = runCatching { AppTheme.valueOf(prefs.getString("theme", AppTheme.IOS_LIGHT.name)!!) }
                .getOrDefault(AppTheme.IOS_LIGHT),
            showNames = prefs.getBoolean("showNames", true),
            showStatuses = prefs.getBoolean("showStatuses", true),
            stylusOnly = prefs.getBoolean("stylusOnly", false),
            virtualCursor = prefs.getBoolean("virtualCursor", false),
            takeoffStrokeWidth = prefs.getFloat("takeoffStrokeWidth", 3.6f),
            takeoffMarkerRadius = prefs.getFloat("takeoffMarkerRadius", 8.5f),
            takeoffTextScale = prefs.getFloat("takeoffTextScale", 1f),
            takeoffSnapRadiusPt = prefs.getFloat("takeoffSnapRadiusPt", 14f)
        )
    )
    val settings: StateFlow<AppSettings> = _settings

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString("theme", next.theme.name)
            .putBoolean("showNames", next.showNames)
            .putBoolean("showStatuses", next.showStatuses)
            .putBoolean("stylusOnly", next.stylusOnly)
            .putBoolean("virtualCursor", next.virtualCursor)
            .putFloat("takeoffStrokeWidth", next.takeoffStrokeWidth)
            .putFloat("takeoffMarkerRadius", next.takeoffMarkerRadius)
            .putFloat("takeoffTextScale", next.takeoffTextScale)
            .putFloat("takeoffSnapRadiusPt", next.takeoffSnapRadiusPt)
            .apply()
    }

    /** آخر دور شغّال المستخدم فتحه — عشان التطبيق يفتح من نفس المكان. */
    fun getLastLevel(): String? = prefs.getString("lastLevel", null)

    fun setLastLevel(level: String) {
        prefs.edit().putString("lastLevel", level).apply()
    }

    // ---------- إعدادات الـ AI ----------
    // المفتاح بيتكتب من المستخدم ومتخزّن على الجهاز بس — مفيش مفتاح جوّه الكود.

    private val _aiConfig = MutableStateFlow(
        AiProviderId.from(aiPrefs.getString("aiProvider", null)).let { provider ->
            AiConfig(
                provider = provider,
                apiKey = aiPrefs.getString("aiApiKey", "").orEmpty(),
                model = aiPrefs.getString("aiModel", null) ?: provider.defaultModel,
                baseUrl = aiPrefs.getString("aiBaseUrl", null) ?: provider.defaultBaseUrl,
                imageModel = aiPrefs.getString("aiImageModel", "").orEmpty(),
                localModelPath = aiPrefs.getString("aiLocalModelPath", "").orEmpty(),
                localBackend = aiPrefs.getString("aiLocalBackend", "DEFAULT") ?: "DEFAULT"
            )
        }
    )
    val aiConfig: StateFlow<AiConfig> = _aiConfig

    fun updateAiConfig(transform: (AiConfig) -> AiConfig) {
        val next = transform(_aiConfig.value)
        _aiConfig.value = next
        aiPrefs.edit()
            .putString("aiProvider", next.provider.name)
            .putString("aiApiKey", next.apiKey)
            .putString("aiModel", next.model)
            .putString("aiBaseUrl", next.baseUrl)
            .putString("aiImageModel", next.imageModel)
            .putString("aiLocalModelPath", next.localModelPath)
            .putString("aiLocalBackend", next.localBackend)
            .apply()
    }

    // ---------- خزنة المفاتيح ----------

    /**
     * المفاتيح المحفوظة بتتخزّن في نفس ملف `ai_secret` — **مش** في Room.
     *
     * ده مقصود: الملف ده مستبعد من النسخ الاحتياطي السحابي ومن نقل الجهاز
     * (backup_rules.xml). لو حطّينا المفاتيح في قاعدة البيانات هتخرج مع أول
     * نسخة احتياطية، والخاصية اللي المفروض تريّح المستخدم تبقى تسريب.
     */
    private val _savedKeys = MutableStateFlow(readKeys())
    val savedKeys: StateFlow<List<SavedKey>> = _savedKeys

    init {
        // المستخدم اللي محدّث من نسخة قديمة عنده مفتاح شغّال والخزنة فاضية.
        // بنحطّه فيها من أول تشغيل عشان مايضيعش لو بدّل مزوّد.
        if (_savedKeys.value.isEmpty() && _aiConfig.value.apiKey.isNotBlank()) {
            rememberWorkingKey("المفتاح الحالي")
        }
    }

    private fun readKeys(): List<SavedKey> = runCatching {
        keyJson.decodeFromString<List<SavedKey>>(aiPrefs.getString("savedKeys", "[]").orEmpty())
    }.getOrDefault(emptyList())

    private fun writeKeys(list: List<SavedKey>) {
        val sorted = list.sortedByDescending { it.lastUsedAt }
        _savedKeys.value = sorted
        aiPrefs.edit().putString("savedKeys", keyJson.encodeToString(sorted)).apply()
    }

    /**
     * بيحفظ الإعداد الشغّال دلوقتي في الخزنة.
     *
     * بيتنده لوحده أول ما أي طلب ينجح — يعني المفتاح اللي **اشتغل فعلاً** هو
     * اللي بيتحفظ. ده الفرق المهم: حفظ أي نص بيتكتب في الخانة كان هيملا
     * الخزنة بمفاتيح غلط ومقطوعة.
     *
     * بيرجّع true لو اتحفظ جديد، وfalse لو كان محفوظ قبل كده.
     */
    fun rememberWorkingKey(label: String? = null): Boolean {
        val cfg = _aiConfig.value
        if (cfg.apiKey.isBlank()) return false
        val now = System.currentTimeMillis()
        val existing = _savedKeys.value.firstOrNull {
            it.provider == cfg.provider.name && it.apiKey == cfg.apiKey
        }
        if (existing != null) {
            // موجود — بنحدّث آخر استخدام والموديل بس، من غير ما نكرّره
            writeKeys(_savedKeys.value.map {
                if (it.id == existing.id)
                    it.copy(model = cfg.model, baseUrl = cfg.baseUrl, lastUsedAt = now,
                        label = label?.takeIf(String::isNotBlank) ?: it.label)
                else it
            })
            return false
        }
        val autoLabel = label?.takeIf(String::isNotBlank)
            ?: "${cfg.provider.label} · ${cfg.apiKey.tail()}"
        writeKeys(
            _savedKeys.value + SavedKey(
                id = "${now}_${cfg.apiKey.hashCode()}",
                label = autoLabel,
                provider = cfg.provider.name,
                apiKey = cfg.apiKey,
                model = cfg.model,
                baseUrl = cfg.baseUrl,
                lastUsedAt = now
            )
        )
        return true
    }

    /** بيرجّع مفتاح محفوظ للاستخدام — المزوّد والموديل بيرجعوا معاه. */
    fun activateKey(id: String) {
        val k = _savedKeys.value.firstOrNull { it.id == id } ?: return
        updateAiConfig {
            it.copy(
                provider = AiProviderId.from(k.provider),
                apiKey = k.apiKey, model = k.model, baseUrl = k.baseUrl
            )
        }
        writeKeys(_savedKeys.value.map {
            if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it
        })
    }

    fun renameKey(id: String, label: String) {
        val clean = label.trim()
        if (clean.isBlank()) return
        writeKeys(_savedKeys.value.map { if (it.id == id) it.copy(label = clean) else it })
    }

    fun deleteKey(id: String) = writeKeys(_savedKeys.value.filterNot { it.id == id })

    /**
     * تبديل المزوّد.
     *
     * لو فيه مفتاح محفوظ للمزوّد الجديد بنرجّعه؛ غير كده بنفضّي الخانة.
     * قبل كده المفتاح القديم كان بيفضل مكانه بعد التبديل — يعني مفتاح
     * OpenRouter بيتبعت لـGemini وبيترفض، والمستخدم يفتكر إن مفتاحه باظ.
     */
    fun switchAiProvider(provider: AiProviderId) {
        // ضغطة على المزوّد المختار أصلاً مالهاش معنى — ومن غير الحارس ده
        // كانت هتفضّي المفتاح اللي المستخدم لسه كاتبه.
        if (provider == _aiConfig.value.provider) return
        val saved = _savedKeys.value
            .filter { it.provider == provider.name }
            .maxByOrNull { it.lastUsedAt }
        updateAiConfig {
            it.copy(
                provider = provider,
                apiKey = saved?.apiKey.orEmpty(),
                model = saved?.model ?: provider.defaultModel,
                baseUrl = saved?.baseUrl ?: provider.defaultBaseUrl,
                // موديل الصور مربوط بالمزوّد — اسم موديل من خدمة تانية
                // مش هيتعرف، والطلب هيفشل برسالة مالهاش معنى للمستخدم.
                imageModel = ""
                // ملف الموديل المحلي **مابيتمسحش** هنا: تنزيله كلّف
                // جيجابايت ووقت، ومالوش أي علاقة بالمزوّد السحابي
                // المختار. الرجوع للمحلي بيلاقيه مكانه.
            )
        }
    }

    private companion object {
        val keyJson = Json { ignoreUnknownKeys = true }
    }
}

/**
 * مفتاح محفوظ. بيشيل المزوّد والموديل والعنوان معاه — المفتاح لوحده
 * مش كفاية، لأن نفس المفتاح مالوش معنى مع مزوّد تاني.
 */
@Serializable
data class SavedKey(
    val id: String,
    val label: String,
    val provider: String,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val lastUsedAt: Long
) {
    /** آخر ٤ حروف بس — الشاشة ما بتعرضش المفتاح كامل. */
    val masked: String get() = apiKey.tail()
}

/** `••••1234` — بيعرّف المفتاح من غير ما يكشفه. */
internal fun String.tail(): String =
    if (length <= 4) "••••" else "••••" + takeLast(4)
