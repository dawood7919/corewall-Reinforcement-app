package com.corewall.qaqc.data

import android.content.Context
import com.corewall.qaqc.ai.AiConfig
import com.corewall.qaqc.ai.AiProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppTheme(val label: String) {
    IOS_LIGHT("Aurora فاتح"),
    DARK_OLED("Aurora دارك"),
    BLUEPRINT("Blueprint هندسي")
}

data class AppSettings(
    val theme: AppTheme = AppTheme.IOS_LIGHT,
    val showNames: Boolean = true,
    val showStatuses: Boolean = true
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
            showStatuses = prefs.getBoolean("showStatuses", true)
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
                baseUrl = aiPrefs.getString("aiBaseUrl", null) ?: provider.defaultBaseUrl
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
            .apply()
    }

    /** تبديل المزوّد بيرجّع الموديل والـ baseUrl لافتراضيات المزوّد الجديد. */
    fun switchAiProvider(provider: AiProviderId) = updateAiConfig {
        it.copy(provider = provider, model = provider.defaultModel, baseUrl = provider.defaultBaseUrl)
    }
}
