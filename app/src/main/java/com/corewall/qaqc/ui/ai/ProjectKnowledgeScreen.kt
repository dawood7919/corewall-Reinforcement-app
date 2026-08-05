package com.corewall.qaqc.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
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
import com.corewall.qaqc.ui.theme.LocalVizColors

/**
 * **معرفة المشروع** — المكتبة المشتركة بين كل الأدوار.
 *
 * الأدوار معزولة عزل مطلق عن بعض. الشاشة دي هي **الاستثناء الوحيد**،
 * وهي استثناء صريح: أي ملف تحطّه هنا بيبقى متاح للمساعد في كل دور —
 * المواصفات، الأكواد، طرق التنفيذ، الجداول العامة.
 *
 * الفرق ده مقصود: تسريب بيانات دور لدور تاني غلط، لكن مواصفة عامة
 * تتعاد كتابتها في 48 دور برضه غلط.
 */
@Composable
fun ProjectKnowledgeScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val srt = LocalSrtColors.current
    val docs by vm.projectDocuments.collectAsStateWithLifecycle()
    val analyzing by vm.analyzing.collectAsStateWithLifecycle()
    val cfg by vm.aiConfig.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadProjectKnowledge() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.importProjectKnowledge(uris) }

    val done = docs.count { it.status == "DONE" }
    val pending = docs.count { it.status == "PENDING" }
    val failed = docs.count { it.status == "FAILED" }

    Column(modifier.fillMaxSize()) {
        // ---------------------------------------------------- الترويسة
        Surface(color = srt.purple.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(srt.purple.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Hub, contentDescription = null, tint = srt.purple, modifier = Modifier.size(19.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "معرفة المشروع",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = srt.purple
                        )
                        Text(
                            "متاحة للمساعد في كل الأدوار",
                            style = MaterialTheme.typography.labelSmall,
                            color = srt.purple.copy(alpha = 0.8f)
                        )
                    }
                    Text("$done / ${docs.size}", style = MaterialTheme.typography.labelMedium, color = srt.purple)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        color = srt.purple,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, tint = Color.White,
                                modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ارفع ملف مشترك", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (pending > 0 || failed > 0) {
                        Surface(
                            onClick = { vm.analyzePendingDocuments() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = srt.purple,
                                    modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (analyzing > 0) "بيحلّل…" else "${pending + failed}",
                                    color = srt.purple, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                if (!cfg.isConfigured) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "مفيش مفتاح API — الملفات هتتسجّل بس ومش هتتحلّل.",
                        style = MaterialTheme.typography.bodySmall, color = srt.orange
                    )
                }
            }
        }

        if (docs.isEmpty()) {
            EmptyLibrary()
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(docs, key = { it.id }) { doc -> SharedDocCard(vm, doc) }
            item { Spacer(Modifier.height(8.dp)) }
            item { ScopeNote() }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    val srt = LocalSrtColors.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(srt.purple.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Hub, contentDescription = null, tint = srt.purple, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(14.dp))
        Text("المكتبة فاضية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "حطّ هنا الحاجات اللي بتخصّ المشروع كله مش دور معيّن:\n" +
                "المواصفات · الأكواد · طرق التنفيذ · جداول عامة",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SharedDocCard(vm: MainViewModel, doc: DocumentEntity) {
    val srt = LocalSrtColors.current
    val viz = LocalVizColors.current
    var expanded by remember(doc.id) { mutableStateOf(false) }
    var facts by remember(doc.id) { mutableStateOf<List<DocFactEntity>>(emptyList()) }

    LaunchedEffect(expanded, doc.status) {
        if (expanded && facts.isEmpty()) {
            facts = runCatching { vm.factsFor(doc.id) }.getOrDefault(emptyList())
        }
    }

    val (tone, icon, label) = when (doc.status) {
        "DONE" -> Triple(viz.good, Icons.Filled.CheckCircle, "متاح لكل الأدوار")
        "PENDING" -> Triple(viz.warning, Icons.Filled.HourglassEmpty,
            if (doc.error.isNotBlank()) "مستني الشبكة" else "مستني التحليل")
        "ANALYZING" -> Triple(srt.blue, Icons.Filled.Refresh, "بيتحلّل")
        "UNSUPPORTED" -> Triple(srt.text3, Icons.Filled.ErrorOutline, "غير مدعوم")
        else -> Triple(viz.critical, Icons.Filled.ErrorOutline, "فشل")
    }

    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(tone.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        doc.fileName, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(label)
                            if (doc.docType != "OTHER") append(" · ${doc.docType}")
                            if (doc.drawingNumber.isNotBlank()) append(" · ${doc.drawingNumber}")
                        },
                        style = MaterialTheme.typography.labelSmall, color = tone
                    )
                }
            }

            if (doc.error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    doc.error, style = MaterialTheme.typography.bodySmall,
                    color = if (doc.status == "FAILED") viz.critical else viz.warning
                )
            }
            if (doc.summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    doc.summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 20 else 2, overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = shrinkVertically()) {
                Column {
                    if (facts.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "اتستخرج ${facts.size} معلومة:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = srt.text3
                        )
                        Spacer(Modifier.height(6.dp))
                        facts.groupBy { it.kind }.forEach { (kind, list) ->
                            Text("• $kind (${list.size})", style = MaterialTheme.typography.labelSmall, color = srt.purple)
                            Text(
                                list.take(12).joinToString("، ") { "${it.key}=${it.value}${it.unit}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 10.dp, bottom = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("حلّل تاني", Icons.Filled.Refresh) { vm.reanalyzeDocument(doc.id) }
                        SmallAction("افتح", Icons.Filled.UploadFile) { vm.openAnyFile(doc.filePath) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** بيوضّح الفرق بين المكتبة المشتركة وذاكرة الدور — الفرق ده مهم يتفهم. */
@Composable
private fun ScopeNote() {
    val srt = LocalSrtColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "الفرق بين المكتبتين",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "• ملفات الدور (تبويب الملفات) → المساعد بيشوفها في الدور ده **بس**. " +
                    "الأدوار معزولة تماماً عن بعض.\n" +
                    "• ملفات هنا → المساعد بيشوفها في **كل** الأدوار.\n\n" +
                    "حطّ هنا اللي بيخصّ المشروع كله. سيب جداول ورسومات الدور في مكانها " +
                    "عشان بيانات دور ماتظهرش في دور تاني.",
                style = MaterialTheme.typography.bodySmall,
                color = srt.text3
            )
        }
    }
}
