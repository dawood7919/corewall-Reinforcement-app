package com.corewall.qaqc.ai.agent

import com.corewall.qaqc.data.db.AgentActionAuditEntity
import com.corewall.qaqc.data.db.AgentExecutionDao
import com.corewall.qaqc.data.db.AgentExecutionPlanEntity
import com.corewall.qaqc.data.db.AgentExecutionStepEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * طبقة تنفيذ دائمة بين اقتراح النموذج وبيانات التطبيق. لا يخزن النموذج أوامره
 * في المحادثة فقط: كل خطة وخطوة وإيصال يبقى قابلاً للمراجعة بعد إعادة التشغيل.
 */
class AgentExecutionStore(private val dao: AgentExecutionDao) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun plans(level: String): Flow<List<AgentExecutionPlanEntity>> = dao.observePlans(level)
    fun steps(planId: Long): Flow<List<AgentExecutionStepEntity>> = dao.observeSteps(planId)

    data class CreatedPlan(val planId: Long, val stepIds: List<Long>)

    suspend fun createPlan(level: String, question: String, actions: List<PendingAction>): CreatedPlan {
        val now = System.currentTimeMillis()
        val planId = dao.insertPlan(
            AgentExecutionPlanEntity(
                level = level,
                title = question.trim().take(96).ifBlank { "خطة تنفيذ الذكاء" },
                sourceQuestion = question.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        val stepIds = if (actions.isNotEmpty()) {
            dao.insertSteps(actions.mapIndexed { index, pending ->
                AgentExecutionStepEntity(
                    planId = planId,
                    ordinal = index + 1,
                    tool = pending.action.tool,
                    argsJson = json.encodeToString(pending.action.args),
                    label = pending.label,
                    risk = pending.tool.risk.name,
                    requiresApproval = true,
                    createdAt = now,
                    updatedAt = now
                )
            })
        } else emptyList()
        return CreatedPlan(planId, stepIds)
    }

    suspend fun stepsForPlan(planId: Long): List<AgentExecutionStepEntity> = dao.stepsForPlan(planId)

    suspend fun actionForStep(stepId: Long): AgentAction? = dao.step(stepId)?.let { step ->
        val args = runCatching { json.decodeFromString<JsonObject>(step.argsJson) }.getOrDefault(JsonObject(emptyMap()))
        AgentAction(tool = step.tool, args = args, reason = step.label)
    }

    suspend fun markPlan(planId: Long, status: String) =
        dao.updatePlanStatus(planId, status, System.currentTimeMillis())

    suspend fun markStep(stepId: Long, status: String, result: String) =
        dao.updateStepStatus(stepId, status, result.take(2_000), System.currentTimeMillis())

    suspend fun audit(
        level: String,
        tool: String,
        detail: String,
        result: String,
        ok: Boolean,
        auto: Boolean,
        planId: Long? = null,
        stepId: Long? = null
    ) {
        dao.insertAudit(
            AgentActionAuditEntity(
                planId = planId,
                stepId = stepId,
                level = level,
                tool = tool,
                detail = detail.take(1_000),
                result = result.take(2_000),
                ok = ok,
                auto = auto,
                at = System.currentTimeMillis()
            )
        )
    }

    suspend fun latestAudit(level: String) = dao.latestAudit(level, 40)
}
