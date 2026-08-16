package com.corewall.qaqc.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.AgentExecutionPlanEntity
import com.corewall.qaqc.data.db.AgentExecutionStepEntity
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space
import com.corewall.qaqc.ui.nav.Dest

/**
 * مركز تشغيل عقل التطبيق: لا يخبّي الأفعال داخل الردود؛ يعرض الخطة والخطوات
 * والإيصالات حتى يستطيع المهندس مراجعة ما سيحدث وما حدث بالفعل.
 */
@Composable
fun AiCommandCenterScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val plans by vm.executionPlans.collectAsStateWithLifecycle()
    val audit by vm.agentAudit.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()

    LaunchedEffect(level) {
        vm.loadAgentAudit()
        vm.refreshSuggestions()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        item("hero") {
            Surface(shape = Radius.shapeLg, color = c.accentContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = c.accent)
                        Spacer(Modifier.width(Space.sm))
                        Column(Modifier.weight(1f)) {
                            Text("عقل المشروع", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            Text("دور $level · يقرأ، يخطط، وينفّذ بموافقتك", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Button(
                            onClick = { vm.go(Dest.AiChat) },
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Send, null)
                            Spacer(Modifier.width(6.dp))
                            Text("أمر جديد")
                        }
                        OutlinedButton(onClick = { vm.loadAgentAudit() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.History, null)
                            Spacer(Modifier.width(6.dp))
                            Text("تحديث النشاط")
                        }
                    }
                }
            }
        }

        if (suggestions.isNotEmpty()) item("suggestions") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("أولويات مقترحة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
                suggestions.take(3).forEach { suggestion ->
                    Surface(shape = Radius.shapeMd, color = c.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Space.md)) {
                            Text(suggestion.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(suggestion.detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        item("plans-title") {
            Text("خطط التنفيذ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
        }
        if (plans.isEmpty()) item("plans-empty") {
            Surface(shape = Radius.shapeMd, color = c.surface, modifier = Modifier.fillMaxWidth()) {
                Text("اطلب من الذكاء تنفيذ مهمة، وستظهر هنا خطة واضحة للمراجعة قبل أي تعديل.", Modifier.padding(Space.lg), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        items(plans.take(8), key = { it.id }) { plan ->
            val steps by remember(plan.id) { vm.executionSteps(plan.id) }.collectAsStateWithLifecycle(emptyList())
            ExecutionPlanCard(plan, steps, onRun = { vm.executePlan(plan.id) }, onDismiss = { vm.dismissPlan(plan.id) }, onRunStep = { vm.executePlanStep(plan.id, it) })
        }

        if (audit.isNotEmpty()) {
            item("audit-title") {
                Text("آخر نشاط", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
            }
            items(audit.take(8), key = { it.id }) { row ->
                Surface(shape = Radius.shapeMd, color = c.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Space.md), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.TaskAlt, null, tint = if (row.ok) c.success.fg else c.danger.fg)
                        Spacer(Modifier.width(Space.sm))
                        Column(Modifier.weight(1f)) {
                            Text(row.tool, style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
                            Text(row.result.ifBlank { row.detail }, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionPlanCard(
    plan: AgentExecutionPlanEntity,
    steps: List<AgentExecutionStepEntity>,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
    onRunStep: (Long) -> Unit
) {
    val c = LocalCwColors.current
    Surface(shape = Radius.shapeLg, color = c.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("الحالة: ${planStatusLabel(plan.status)} · ${steps.size} خطوة", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
            steps.forEach { step ->
                Surface(shape = Radius.shapeSm, color = c.surfaceAlt, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${step.ordinal}. ${step.label}", style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                            Text(stepStatusLabel(step.status), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                        }
                        if (step.status == "PENDING") {
                            OutlinedButton(onClick = { onRunStep(step.id) }) { Text("تنفيذ") }
                        }
                    }
                }
            }
            if (plan.status == "DRAFT" || plan.status == "APPROVED") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Button(onClick = onRun, colors = ButtonDefaults.buttonColors(containerColor = c.accent), modifier = Modifier.weight(1f)) { Text("اعتماد وتنفيذ") }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                }
            }
        }
    }
}

private fun planStatusLabel(status: String) = when (status) {
    "DRAFT" -> "تحتاج مراجعة"
    "APPROVED" -> "معتمدة"
    "RUNNING" -> "جاري التنفيذ"
    "DONE" -> "مكتملة"
    "PARTIAL" -> "اكتملت جزئياً"
    "DISMISSED" -> "ملغاة"
    else -> status
}

private fun stepStatusLabel(status: String) = when (status) {
    "PENDING" -> "بانتظار الموافقة"
    "RUNNING" -> "جاري التنفيذ"
    "DONE" -> "تم التنفيذ"
    "FAILED" -> "تعذّر التنفيذ"
    "DISMISSED" -> "أُلغي"
    else -> status
}
