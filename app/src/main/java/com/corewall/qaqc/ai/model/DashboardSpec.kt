package com.corewall.qaqc.ai.model

import kotlinx.serialization.Serializable

/**
 * الداشبورد اللي **الـ AI بيقرّره** — مش كروت ثابتة في الكود.
 * حسب البيانات المتاحة للدور، الموديل بيختار يعرض إيه وبأي ترتيب.
 */
@Serializable
data class DashboardSpec(
    val headline: String = "",
    val cards: List<DashCard> = emptyList()
) {
    val isEmpty: Boolean get() = cards.isEmpty() && headline.isBlank()
}

@Serializable
data class DashCard(
    /** METRICS | LIST | ALERT | PROGRESS | TEXT */
    val type: String = "TEXT",
    val title: String = "",
    val subtitle: String = "",
    /** INFO | LOW | MEDIUM | HIGH | CRITICAL — بيحدّد اللون */
    val severity: String = "INFO",
    /** للـ METRICS */
    val metrics: List<DashMetric> = emptyList(),
    /** للـ LIST و ALERT */
    val items: List<String> = emptyList(),
    /** للـ PROGRESS (0..100) */
    val percent: Int = 0,
    /** للـ TEXT */
    val body: String = "",
    /** أكواد عناصر مرتبطة */
    val marks: List<String> = emptyList()
)

@Serializable
data class DashMetric(
    val label: String = "",
    val value: String = "",
    val hint: String = ""
)

/** حالة الداشبورد الديناميكي. */
sealed interface DashboardState {
    data object NotConfigured : DashboardState
    data object Idle : DashboardState
    data object Loading : DashboardState
    data class Ready(val spec: DashboardSpec, val level: String, val generatedAt: Long, val cached: Boolean) : DashboardState
    data class Error(val message: String) : DashboardState
}

/** تقرير مولّد بالـ AI. */
@Serializable
data class GeneratedReport(
    val title: String = "",
    val markdown: String = "",
    val generatedAt: Long = 0L,
    val kind: String = ""
)

enum class ReportKind(val label: String, val prompt: String) {
    DAILY("تقرير يومي", "تقرير يومي عن حالة الدور النهاردة: الأعمال، العمالة، الفحوصات، والمعوقات."),
    WEEKLY("تقرير أسبوعي", "تقرير أسبوعي: التقدّم، الإنتاجية، الفحوصات المكتملة والمعلّقة، والمخاطر."),
    INSPECTION("تقرير فحص", "تقرير فحص فني: حالة كل عنصر، المقبول والمرفوض والمعلّق، والملاحظات الفنية."),
    MATERIAL("طلب مواد", "طلب مواد مبني على التسليح المطلوب للدور والكميات المتبقية."),
    SITE_INSTRUCTION("تعليمات موقع", "تعليمات موقع للفريق: الأولويات، نقاط الجودة، والتحذيرات.")
}
