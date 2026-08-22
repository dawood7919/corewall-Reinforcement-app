package com.corewall.qaqc.ui.creative

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.corewall.qaqc.MainViewModel
import com.corewall.qaqc.creative.CreativeTemplate
import com.corewall.qaqc.data.db.CreativeDocumentEntity
import com.corewall.qaqc.ui.design.LocalCwColors
import com.corewall.qaqc.ui.design.Radius
import com.corewall.qaqc.ui.design.Space

/** مدخل موحد لقوالب التقارير والمسودات والإصدارات التي ينشئها المستخدم أو الذكاء. */
@Composable
fun CreativeStudioScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val c = LocalCwColors.current
    val documents by vm.creativeDocuments.collectAsStateWithLifecycle()
    val level by vm.currentLevel.collectAsStateWithLifecycle()
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
                            Text("استوديو الإنشاء", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.textPrimary)
                            Text("قوالب احترافية ومسودات قابلة للمراجعة للدور $level", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    Button(onClick = { vm.createCreativeDocument(CreativeTemplate.QUALITY) }, colors = ButtonDefaults.buttonColors(containerColor = c.accent), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("إنشاء تقرير جديد")
                    }
                }
            }
        }
        item("templates") {
            Text("ابدأ من قالب", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
        }
        items(CreativeTemplate.all, key = { it }) { template ->
            TemplateCard(template = template, onClick = { vm.createCreativeDocument(template) })
        }
        item("recent-title") {
            Text("المسودات الأخيرة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.textPrimary)
        }
        if (documents.isEmpty()) item("empty") {
            Surface(shape = Radius.shapeMd, color = c.surface, modifier = Modifier.fillMaxWidth()) {
                Text("لا توجد مسودات بعد. اختر قالباً، أو اطلب من الذكاء إنشاء تقرير لك.", Modifier.padding(Space.lg), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        items(documents, key = { it.id }) { doc -> DraftCard(doc) { vm.openCreativeDocument(doc.id) } }
    }
}

@Composable
private fun TemplateCard(template: String, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(shape = Radius.shapeMd, color = c.surface, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, null, tint = c.accent)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(CreativeTemplate.label(template), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(templateHint(template), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Icon(Icons.Filled.Add, null, tint = c.accent)
        }
    }
}

@Composable
private fun DraftCard(doc: CreativeDocumentEntity, onClick: () -> Unit) {
    val c = LocalCwColors.current
    Surface(shape = Radius.shapeMd, color = c.surface, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, null, tint = c.accent)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(doc.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${CreativeTemplate.label(doc.templateKey)} · ${documentStatus(doc.status)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}

private fun templateHint(key: String) = when (key) {
    CreativeTemplate.QUALITY -> "ملاحظات الجودة والإجراءات التصحيحية والاعتماد"
    CreativeTemplate.TAKEOFF -> "كميات ومعايرة ومصادر الرسمات"
    CreativeTemplate.DAILY -> "أعمال اليوم والعمالة والمعوقات"
    CreativeTemplate.MEETING -> "قرارات ومسؤوليات ومواعيد"
    else -> "خطاب منسق مع مرجع ومرفقات"
}

private fun documentStatus(status: String) = when (status) {
    "DRAFT" -> "مسودة"
    "READY" -> "جاهز للمراجعة"
    "EXPORTED" -> "تم التصدير"
    else -> status
}
