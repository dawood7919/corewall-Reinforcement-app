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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.data.db.DocFactEntity
import com.corewall.qaqc.data.db.DocumentEntity
import com.corewall.qaqc.ui.theme.LocalSrtColors

/**
 * شاشة المعرفة: بتورّي كل ملف دخل التطبيق وحالته —
 * اتحلّل؟ معلّق؟ فشل وليه؟ وإيه اللي اتستخرج منه بالظبط.
 * دي كانت ناقصة، وعشان كده مكانش فيه أي طريقة تعرف إن التحليل مشتغلش.
 */
@Composable
fun KnowledgeScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val level by vm.currentLevel.collectAsStateWithLifecycle()
    val docs by vm.documents.collectAsStateWithLifecycle()
    val analyzing by vm.analyzing.collectAsStateWithLifecycle()
    val cfg by vm.aiConfig.collectAsStateWithLifecycle()

    LaunchedEffect(level) { vm.loadKnowledge() }

    val pending = docs.count { it.status == "PENDING" }
    // "غير مدعوم" مش بيتعاد — نوع الملف نفسه هو المشكلة
    val retryable = docs.count { it.status == "FAILED" }
    val done = docs.count { it.status == "DONE" }

    Column(modifier.fillMaxSize()) {
        // ملخّص + زر تحليل الكل
        Surface(color = srt.blueTint, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = srt.blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ذاكرة دور $level", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = srt.blue, modifier = Modifier.weight(1f))
                    Text("$done / ${docs.size}", style = MaterialTheme.typography.labelMedium, color = srt.blue)
                }
                if (!cfg.isConfigured) {
                    Spacer(Modifier.height(8.dp))
                    Text("مفيش مفتاح API — الملفات بتتسجّل بس ومش بتتحلّل.",
                        style = MaterialTheme.typography.bodySmall, color = srt.orange)
                } else if (pending > 0 || retryable > 0) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        onClick = { vm.analyzePendingDocuments() },
                        shape = RoundedCornerShape(12.dp),
                        color = srt.blue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    analyzing > 0 -> "بيحلّل…"
                                    else -> "حلّل المعلّق وأعد المحاولة (${pending + retryable})"
                                },
                                color = Color.White, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (docs.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = srt.text3, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("مفيش ملفات في ذاكرة الدور ده", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("ارفع ملفات من تبويب الملفات وهتظهر هنا تلقائي.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(docs, key = { it.id }) { doc -> DocCard(vm, doc) }
        }
    }
}

@Composable
private fun DocCard(vm: MainViewModel, doc: DocumentEntity) {
    val srt = LocalSrtColors.current
    var expanded by remember { mutableStateOf(false) }
    var facts by remember(doc.id) { mutableStateOf<List<DocFactEntity>>(emptyList()) }

    LaunchedEffect(expanded, doc.status) {
        if (expanded && facts.isEmpty()) facts = runCatching { vm.factsFor(doc.id) }.getOrDefault(emptyList())
    }

    val (statusColor, statusIcon, statusLabel) = when (doc.status) {
        "DONE" -> Triple(srt.green, Icons.Filled.CheckCircle, "اتحلّل")
        // معلّق ومعاه سبب = الشبكة قطعت، وهيتعاد لوحده — مش نفس "لسه ماتحللش"
        "PENDING" -> Triple(
            srt.orange, Icons.Filled.HourglassEmpty,
            if (doc.error.isNotBlank()) "مستني الشبكة" else "معلّق"
        )
        "ANALYZING" -> Triple(srt.blue, Icons.Filled.Refresh, "بيتحلّل")
        "UNSUPPORTED" -> Triple(srt.text3, Icons.Filled.ErrorOutline, "غير مدعوم")
        else -> Triple(srt.red, Icons.Filled.ErrorOutline, "فشل")
    }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.fileName, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(statusLabel)
                            if (doc.docType != "OTHER") append(" · ${doc.docType}")
                            if (doc.drawingNumber.isNotBlank()) append(" · ${doc.drawingNumber}")
                            if (doc.revision.isNotBlank()) append(" · ${doc.revision}")
                        },
                        style = MaterialTheme.typography.labelSmall, color = statusColor
                    )
                }
            }

            if (doc.error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                // الأحمر للفشل النهائي بس؛ الناقص والمستني تنبيه مش خطأ
                Text(
                    doc.error, style = MaterialTheme.typography.bodySmall,
                    color = if (doc.status == "FAILED") srt.red else srt.orange
                )
            }
            if (doc.summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(doc.summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 20 else 2, overflow = TextOverflow.Ellipsis)
            }

            if (expanded) {
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("اتستخرج ${facts.size} معلومة:", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = srt.text3)
                    Spacer(Modifier.height(6.dp))
                    facts.groupBy { it.kind }.forEach { (kind, list) ->
                        Text("• $kind (${list.size})", style = MaterialTheme.typography.labelSmall, color = srt.blue)
                        Text(
                            list.take(12).joinToString("، ") { "${it.key}=${it.value}${it.unit}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = { vm.reanalyzeDocument(doc.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("حلّل تاني", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
