package com.corewall.qaqc.ai.model

import kotlinx.serialization.Serializable

/**
 * رد المساعد الهندسي كبيانات منظّمة بدل نص خام.
 *
 * الشكل مسطّح عن قصد: كائن واحد بحقول اختيارية بدل شجرة أنواع.
 * الموديل بيغلط كتير في الأشكال المتداخلة (discriminators)، والمسطّح
 * بيتفكّ بنجاح حتى لو الموديل ملّى حقول زيادة أو سابها فاضية.
 */
@Serializable
data class ChatAnswer(
    /** الإجابة في جملة واحدة — أهم سطر، بيتعرض فوق كل حاجة. */
    val headline: String = "",
    val blocks: List<AnswerBlock> = emptyList(),
    /** أسئلة متابعة مقترحة — بتتحوّل لأزرار. */
    val followUps: List<String> = emptyList(),
    /** مصادر الإجابة (أسماء ملفات أو "بيانات التطبيق"). */
    val sources: List<String> = emptyList()
)

/**
 * بلوك واحد في الرد. [type] بيحدّد الحقول اللي بتتقري:
 *
 * - `TEXT`     — فقرة شرح (body)
 * - `METRICS`  — صف أرقام رئيسية (metrics)
 * - `BAR`      — مقارنة مقادير، سلسلة واحدة (points)
 * - `SPLIT`    — جزء من كل: شريط مقسّم (points)
 * - `TREND`    — تغيّر عبر الزمن (points)
 * - `METER`    — نسبة واحدة مقابل حد (percent)
 * - `TABLE`    — جدول (columns + rows)
 * - `LIST`     — نقط (items)
 * - `STEPS`    — خطوات مرقّمة (items)
 * - `ALERT`    — تنبيه بحالة (body + severity)
 */
@Serializable
data class AnswerBlock(
    val type: String = "TEXT",
    val title: String = "",
    val body: String = "",
    /** وحدة القيم (kg, no, m) — بتتعرض جنب الأرقام. */
    val unit: String = "",
    /** INFO | GOOD | WARNING | SERIOUS | CRITICAL */
    val severity: String = "INFO",
    val percent: Double = 0.0,
    val metrics: List<AnswerMetric> = emptyList(),
    val points: List<AnswerPoint> = emptyList(),
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val items: List<String> = emptyList(),
    /** أكواد العناصر المرتبطة بالبلوك. */
    val marks: List<String> = emptyList()
)

@Serializable
data class AnswerMetric(
    val label: String = "",
    val value: String = "",
    /** سطر صغير تحت الرقم (المصدر أو التوضيح). */
    val hint: String = "",
    /** فرق عن فترة/دور سابق، بإشارة: "+12" أو "-3". */
    val delta: String = "",
    /** UP | DOWN | FLAT — اتجاه الفرق. */
    val direction: String = "FLAT",
    /** هل الزيادة كويسة؟ بتحدّد لون الفرق. */
    val upIsGood: Boolean = true
)

@Serializable
data class AnswerPoint(
    val label: String = "",
    val value: Double = 0.0,
    /** نص القيمة زي ما المستخدم يقراها — لو فاضي بنصيغه من value. */
    val display: String = "",
    val note: String = ""
)
