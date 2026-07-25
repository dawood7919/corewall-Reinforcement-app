package com.corewall.qaqc.data

import android.content.Context
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
}
