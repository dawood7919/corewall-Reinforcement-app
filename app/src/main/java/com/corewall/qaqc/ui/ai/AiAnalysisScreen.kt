package com.corewall.qaqc.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.ai.model.AiItem
import com.corewall.qaqc.ai.model.AiUiState
import com.corewall.qaqc.ui.EmptyState
import com.corewall.qaqc.ui.theme.LocalSrtColors

/** شاشة التحليل الكامل — كل النتائج والتحذيرات والتوصيات والكميات. */
@Composable
fun AiAnalysisScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val state by vm.aiState.collectAsStateWithLifecycle()

    val ready = state as? AiUiState.Ready
        ?: (state as? AiUiState.Error)?.previous
    if (ready == null) {
        EmptyState(
            icon = Icons.Filled.AutoAwesome,
            title = "مفيش تحليل لسه",
            subtitle = "ارجع للشاشة الرئيسية واضغط تحديث في كارت المساعد الذكي.",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val a = ready.analysis
    val statusColor = when (a.status.uppercase()) {
        "GOOD" -> srt.green
        "CRITICAL" -> srt.red
        else -> srt.orange
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${a.healthScore}", style = MaterialTheme.typography.headlineSmall, color = statusColor, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("دور ${ready.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("مؤشّر الصحة · ${a.status}", style = MaterialTheme.typography.labelSmall, color = srt.text3)
                        }
                    }
                    if (a.summary.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(a.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        group("أهم النتائج", a.findings, Icons.Filled.CheckCircle, srt.blue)
        group("تحذيرات", a.warnings, Icons.Filled.WarningAmber, srt.orange)
        group("توصيات", a.recommendations, Icons.Filled.Lightbulb, srt.green)
        notesGroup("ملاحظات الكميات", a.quantityNotes, Icons.Filled.Straighten, srt.purple)
        notesGroup("ملاحظات هندسية", a.engineeringNotes, Icons.Filled.AutoAwesome, srt.blue)

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "تحليل مولّد بالذكاء الاصطناعي (${ready.model}) — للاسترشاد فقط، " +
                    "والمرجع النهائي دايماً هو الرسومات المعتمدة والمواصفات.",
                style = MaterialTheme.typography.labelSmall,
                color = srt.text3
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(
    title: String, items: List<AiItem>, icon: ImageVector, accent: Color
) {
    if (items.isEmpty()) return
    item {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
                }
                Spacer(Modifier.height(8.dp))
                items.forEach { AiItemRow(it) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.notesGroup(
    title: String, notes: List<String>, icon: ImageVector, accent: Color
) {
    if (notes.isEmpty()) return
    item {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
                }
                Spacer(Modifier.height(8.dp))
                notes.forEach {
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("•", color = accent)
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
