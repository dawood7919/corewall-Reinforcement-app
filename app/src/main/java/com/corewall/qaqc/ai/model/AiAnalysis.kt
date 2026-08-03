package com.corewall.qaqc.ai.model

import kotlinx.serialization.Serializable

/**
 * ناتج التحليل — الـ AI لازم يرجّع JSON بالشكل ده بالظبط،
 * عشان نعرضه ككروت أصلية مش نص خام.
 */
@Serializable
data class AiAnalysis(
    val summary: String = "",
    val healthScore: Int = 0,
    val status: String = "ATTENTION",              // GOOD | ATTENTION | CRITICAL
    val findings: List<AiItem> = emptyList(),
    val warnings: List<AiItem> = emptyList(),
    val recommendations: List<AiItem> = emptyList(),
    val quantityNotes: List<String> = emptyList(),
    val engineeringNotes: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = summary.isBlank() && findings.isEmpty() && warnings.isEmpty() && recommendations.isEmpty()
}

@Serializable
data class AiItem(
    val title: String = "",
    val detail: String = "",
    /** INFO | LOW | MEDIUM | HIGH | CRITICAL */
    val severity: String = "INFO",
    /** أكواد العناصر المرتبطة (اختياري) — عشان المستخدم يعرف يروح فين. */
    val marks: List<String> = emptyList()
)

enum class AiSeverity { INFO, LOW, MEDIUM, HIGH, CRITICAL;
    companion object {
        fun from(raw: String?): AiSeverity =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: INFO
    }
}

/** حالة واجهة تحليل الـ AI. */
sealed interface AiUiState {
    /** مفيش مفتاح API — الميزة متوقفة تماماً ومفيش أي اتصال بالشبكة. */
    data object NotConfigured : AiUiState
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Ready(
        val analysis: AiAnalysis,
        val level: String,
        val model: String,
        val generatedAt: Long,
        /** بيانات مخزّنة من قبل (مش لسه متولّدة دلوقتي). */
        val cached: Boolean
    ) : AiUiState
    data class Error(val message: String, val previous: Ready? = null) : AiUiState
}
