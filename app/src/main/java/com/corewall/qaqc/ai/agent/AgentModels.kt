package com.corewall.qaqc.ai.agent

import com.corewall.qaqc.ai.model.ChatAnswer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * خطوة واحدة من الوكيل: يا إما بيطلب أدوات، يا إما بيدّي الإجابة النهائية.
 * الاتنين مع بعض مسموحين — بس لو فيه أدوات، بنشغّلها الأول والإجابة
 * بتتاخد من الجولة اللي بعدها.
 */
@Serializable
data class AgentStep(
    /** سطر قصير عن سبب الخطوة — بيتعرض للمستخدم وهو مستني. */
    val thought: String = "",
    val actions: List<AgentAction> = emptyList(),
    val answer: ChatAnswer? = null,
    /** الوكيل خلص ومحتاجش أدوات تانية. */
    val done: Boolean = false
)

@Serializable
data class AgentAction(
    val tool: String = "",
    val args: JsonObject = JsonObject(emptyMap()),
    /** ليه بينفّذ ده — بيتعرض في كارت الموافقة وسجل الإجراءات. */
    val reason: String = ""
) {
    /** الموديل بيبعت الأرقام أحياناً كنص وأحياناً كرقم — الاتنين بيتقروا هنا. */
    fun str(key: String, fallback: String = ""): String {
        val p = args[key] as? JsonPrimitive ?: return fallback
        if (p is JsonNull) return fallback
        return p.content.ifBlank { fallback }
    }

    fun num(key: String): Double? = str(key).trim().toDoubleOrNull()

    /** الموديل بيبعت البوليان أحياناً كنص ("true") وأحياناً كقيمة. */
    fun bool(key: String): Boolean = str(key).trim().lowercase() in setOf("true", "1", "yes", "نعم")

    /** وصف مقروء للإجراء — للسجل ولكارت الموافقة. */
    fun describe(): String {
        val a = args.entries.joinToString("، ") { (k, v) -> "$k=${v.plain()}" }
        return if (a.isBlank()) tool else "$tool ($a)"
    }
}

private fun JsonElement.plain(): String =
    runCatching { jsonPrimitive.content }.getOrElse { toString() }

/** نتيجة تنفيذ أداة — بترجع للموديل كمشاهدة (observation). */
data class ToolOutcome(
    val tool: String,
    val ok: Boolean,
    /** نص/JSON مختصر بيتبعت للموديل. */
    val observation: String,
    /** رسالة للمستخدم لو الإجراء غيّر حاجة فعلاً. */
    val userMessage: String = ""
)

/**
 * إجراء مقترح مستنّي موافقة المستخدم.
 * بيتخزّن في الذاكرة بس — لو التطبيق اتقفل، الاقتراح بيروح، وده مقصود:
 * موافقة على حاجة نسيتها مش موافقة.
 */
data class PendingAction(
    val id: Long,
    val action: AgentAction,
    val tool: AgentTool,
    val label: String,
    /** رابط اختياري إلى الخطة الدائمة التي نشأ منها الإجراء. */
    val planId: Long? = null,
    val stepId: Long? = null
)

/** سطر في سجل الإجراءات — كل حاجة الوكيل عملها، ظاهرة وقابلة للمراجعة. */
data class ActionLogEntry(
    val at: Long,
    val tool: String,
    val detail: String,
    val ok: Boolean,
    val auto: Boolean
)
